package it.edgvoip.jarvis.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import it.edgvoip.jarvis.MainActivity
import it.edgvoip.jarvis.R
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.model.DeviceRegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class JarvisMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "JarvisMessaging"

        const val CHANNEL_CALLS = "jarvis_calls"
        const val CHANNEL_LEADS = "jarvis_leads"
        const val CHANNEL_MESSAGES = "jarvis_messages"
        const val CHANNEL_SYSTEM = "jarvis_system"

        private var notificationIdCounter = 2000
    }

    @Inject
    lateinit var api: JarvisApi

    @Inject
    lateinit var tokenManager: TokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Nuovo token FCM ricevuto")

        serviceScope.launch {
            try {
                val slug = tokenManager.getTenantSlug() ?: return@launch
                val deviceName = android.os.Build.MODEL
                val request = DeviceRegisterRequest(
                    fcmToken = token,
                    platform = "android",
                    deviceName = deviceName
                )
                val response = api.registerDevice(slug, request)
                if (response.isSuccessful) {
                    Log.i(TAG, "Token FCM registrato con successo sul backend")
                } else {
                    Log.w(TAG, "Errore registrazione token FCM: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore invio token FCM al backend: ${e.message}", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i(TAG, "Notifica push ricevuta: ${message.data}")

        val data = message.data
        val type = data["type"] ?: "system"
        val title = data["title"] ?: message.notification?.title ?: "Jarvis"
        val body = data["body"] ?: message.notification?.body ?: ""
        val notificationId = data["notification_id"]
        val deepLink = data["deep_link"]

        showNotification(type, title, body, notificationId, deepLink)
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Chiamate",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifiche per chiamate in arrivo e perse"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(ringtoneUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val leadsChannel = NotificationChannel(
            CHANNEL_LEADS,
            "Nuovi Lead",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifiche per nuovi lead e opportunità"
            enableVibration(true)
            setSound(notificationSoundUri, audioAttributes)
            setShowBadge(true)
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messaggi",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifiche per nuovi messaggi"
            enableVibration(true)
            setSound(notificationSoundUri, audioAttributes)
            setShowBadge(true)
        }

        val systemChannel = NotificationChannel(
            CHANNEL_SYSTEM,
            "Sistema",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifiche di sistema e aggiornamenti"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(callsChannel)
        notificationManager.createNotificationChannel(leadsChannel)
        notificationManager.createNotificationChannel(messagesChannel)
        notificationManager.createNotificationChannel(systemChannel)
    }

    private fun showNotification(
        type: String,
        title: String,
        body: String,
        notificationId: String?,
        deepLink: String?
    ) {
        val channelId = when (type) {
            "call", "missed_call" -> CHANNEL_CALLS
            "lead", "new_lead" -> CHANNEL_LEADS
            "message", "chat" -> CHANNEL_MESSAGES
            else -> CHANNEL_SYSTEM
        }

        val priority = when (type) {
            "call", "missed_call" -> android.app.Notification.PRIORITY_HIGH
            else -> android.app.Notification.PRIORITY_DEFAULT
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
            putExtra("notification_id", notificationId)
            if (deepLink != null) {
                putExtra("deep_link", deepLink)
            }
            when (type) {
                "call", "missed_call" -> putExtra("navigate_to", "phone")
                "lead", "new_lead" -> putExtra("navigate_to", "notifications")
                "message", "chat" -> putExtra("navigate_to", "chat")
                else -> putExtra("navigate_to", "notifications")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationIdCounter,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = R.mipmap.ic_launcher

        val notification = android.app.Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(iconRes)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setCategory(
                when (type) {
                    "call", "missed_call" -> Notification.CATEGORY_CALL
                    "message", "chat" -> Notification.CATEGORY_MESSAGE
                    else -> Notification.CATEGORY_EVENT
                }
            )
            .setVisibility(
                if (type == "call" || type == "missed_call")
                    Notification.VISIBILITY_PUBLIC
                else
                    Notification.VISIBILITY_PRIVATE
            )
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationIdCounter++, notification)
    }
}
