package it.edgvoip.jarvis.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import it.edgvoip.jarvis.MainActivity
import it.edgvoip.jarvis.R
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.model.DeviceRegisterRequest
import it.edgvoip.jarvis.sip.SipService
import it.edgvoip.jarvis.ui.screens.SettingsViewModel
import it.edgvoip.jarvis.ui.screens.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class JarvisMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "JarvisMessaging"

        const val CHANNEL_CALLS = "jarvis_calls"
        const val CHANNEL_LEADS = "jarvis_leads"
        const val CHANNEL_MESSAGES = "jarvis_messages"
        const val CHANNEL_SYSTEM = "jarvis_system"

        private const val INCOMING_CALL_NOTIFICATION_ID = 3000
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

        when (type) {
            "incoming_call" -> handleIncomingCallPush(data)
            else -> {
                val title = data["title"] ?: message.notification?.title ?: "Jarvis"
                val body = data["body"] ?: message.notification?.body ?: ""
                val notificationId = data["notification_id"]
                val deepLink = data["deep_link"]
                showNotification(type, title, body, notificationId, deepLink)
            }
        }
    }

    private fun isPushNotificationsEnabled(): Boolean {
        return try {
            runBlocking {
                applicationContext.settingsDataStore.data
                    .map { prefs -> prefs[SettingsViewModel.KEY_PUSH_NOTIFICATIONS] ?: true }
                    .first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore lettura preferenza push, default true: ${e.message}")
            true
        }
    }

    private fun handleIncomingCallPush(data: Map<String, String>) {
        if (!isPushNotificationsEnabled()) {
            Log.i(TAG, "Push notifiche disabilitate dall'utente, ignorando chiamata in arrivo")
            return
        }

        val callerNumber = data["caller_number"] ?: "Sconosciuto"
        val callerName = data["caller_name"] ?: ""
        val callId = data["call_id"] ?: ""

        Log.i(TAG, "Chiamata in arrivo via push: $callerNumber ($callerName)")

        acquireWakeLock()

        val intent = Intent(this, SipService::class.java).apply {
            action = SipService.ACTION_START
        }
        startForegroundService(intent)

        showIncomingCallNotification(callerNumber, callerName, callId)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Jarvis:IncomingCallWakeLock"
            )
            wakeLock.acquire(30_000L)
            Log.i(TAG, "Wake lock acquisito per chiamata in arrivo")
        } catch (e: Exception) {
            Log.e(TAG, "Errore wake lock: ${e.message}", e)
        }
    }

    private fun showIncomingCallNotification(callerNumber: String, callerName: String, callId: String) {
        val displayName = callerName.ifEmpty { callerNumber }

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incoming_call", true)
            putExtra("caller_number", callerNumber)
            putExtra("caller_name", callerName)
            putExtra("call_id", callId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 100, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(this, SipService::class.java).apply {
            action = SipService.ACTION_ANSWER
        }
        val answerPendingIntent = PendingIntent.getService(
            this, 101, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, SipService::class.java).apply {
            action = SipService.ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getService(
            this, 102, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_CALLS)
            .setContentTitle("Chiamata in arrivo")
            .setContentText(displayName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.mipmap.ic_launcher, "Rispondi", answerPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Rifiuta", hangupPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
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
