package it.edgvoip.jarvis.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import it.edgvoip.jarvis.MainActivity
import it.edgvoip.jarvis.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SipService : Service() {

    companion object {
        private const val TAG = "SipService"
        private const val CHANNEL_SIP = "jarvis_sip_channel"
        private const val CHANNEL_CALL = "jarvis_call_channel"
        private const val NOTIFICATION_SIP_ID = 1001
        private const val NOTIFICATION_CALL_ID = 1002
        private const val WAKE_LOCK_TAG = "Jarvis:SipWakeLock"

        const val ACTION_START = "it.edgvoip.jarvis.sip.ACTION_START"
        const val ACTION_STOP = "it.edgvoip.jarvis.sip.ACTION_STOP"
        const val ACTION_ANSWER = "it.edgvoip.jarvis.sip.ACTION_ANSWER"
        const val ACTION_HANGUP = "it.edgvoip.jarvis.sip.ACTION_HANGUP"

        fun start(context: Context) {
            val intent = Intent(context, SipService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SipService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var sipManager: WebRtcPhoneManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SipService created")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannels()
        observeCallState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting SIP service")
                startForeground(NOTIFICATION_SIP_ID, createSipNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                acquireWakeLock()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping SIP service")
                releaseAudioFocus()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ANSWER -> {
                Log.i(TAG, "Answering call from notification")
                sipManager.answerCall()
                requestAudioFocus()
                openInCallScreen()
            }
            ACTION_HANGUP -> {
                Log.i(TAG, "Hanging up call from notification")
                sipManager.hangupCall()
                releaseAudioFocus()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "SipService destroyed")
        releaseAudioFocus()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        val sipChannel = NotificationChannel(
            CHANNEL_SIP,
            "SIP Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Servizio SIP attivo in background"
            setShowBadge(false)
        }

        val callChannel = NotificationChannel(
            CHANNEL_CALL,
            "Chiamate in arrivo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifiche per chiamate in arrivo"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(sipChannel)
        notificationManager.createNotificationChannel(callChannel)
    }

    private fun createSipNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SIP)
            .setContentTitle("Jarvis - SIP Attivo")
            .setContentText("Registrato e in attesa di chiamate")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createIncomingCallNotification(callerNumber: String, callerName: String): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incoming_call", true)
            putExtra("caller_number", callerNumber)
            putExtra("caller_name", callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 100, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(this, SipService::class.java).apply {
            action = ACTION_ANSWER
        }
        val answerPendingIntent = PendingIntent.getService(
            this, 101, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, SipService::class.java).apply {
            action = ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getService(
            this, 102, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = callerName.ifEmpty { callerNumber }

        return NotificationCompat.Builder(this, CHANNEL_CALL)
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
            .build()
    }

    private fun createActiveCallNotification(number: String, duration: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("active_call", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 200, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, SipService::class.java).apply {
            action = ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getService(
            this, 201, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SIP)
            .setContentTitle("Chiamata in corso")
            .setContentText("$number • $duration")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "Chiudi", hangupPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun observeCallState() {
        serviceScope.launch {
            sipManager.callState.collectLatest { state ->
                val notificationManager = getSystemService(NotificationManager::class.java)

                when (state) {
                    CallState.INCOMING -> {
                        val call = sipManager.currentCall.value
                        val number = call?.number ?: "Sconosciuto"
                        val name = call?.name ?: ""
                        val notification = createIncomingCallNotification(number, name)
                        notificationManager.notify(NOTIFICATION_CALL_ID, notification)
                        requestAudioFocus()
                    }
                    CallState.CONNECTED, CallState.HOLDING -> {
                        notificationManager.cancel(NOTIFICATION_CALL_ID)
                        val call = sipManager.currentCall.value
                        val number = call?.number ?: ""
                        val duration = formatDuration(call?.duration ?: 0)
                        val notification = createActiveCallNotification(number, duration)
                        notificationManager.notify(NOTIFICATION_SIP_ID, notification)
                    }
                    CallState.DISCONNECTED, CallState.IDLE -> {
                        notificationManager.cancel(NOTIFICATION_CALL_ID)
                        notificationManager.notify(NOTIFICATION_SIP_ID, createSipNotification())
                        releaseAudioFocus()
                    }
                    else -> {}
                }
            }
        }

        serviceScope.launch {
            sipManager.currentCall.collectLatest { call ->
                if (call != null && (call.state == CallState.CONNECTED || call.state == CallState.HOLDING)) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    val duration = formatDuration(call.duration)
                    val notification = createActiveCallNotification(call.number, duration)
                    notificationManager.notify(NOTIFICATION_SIP_ID, notification)
                }
            }
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        Log.w(TAG, "Audio focus lost")
                        hasAudioFocus = false
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Log.w(TAG, "Audio focus lost transiently")
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        Log.i(TAG, "Audio focus gained")
                        hasAudioFocus = true
                    }
                }
            }
            .build()

        audioFocusRequest = focusRequest
        val result = audioManager?.requestAudioFocus(focusRequest)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        if (hasAudioFocus) {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.i(TAG, "Audio focus acquired for voice communication")
        }
    }

    private fun releaseAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
            audioManager?.mode = AudioManager.MODE_NORMAL
            hasAudioFocus = false
            audioFocusRequest = null
            Log.i(TAG, "Audio focus released")
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(10 * 60 * 1000L)
            }
            Log.i(TAG, "Wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun openInCallScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("active_call", true)
        }
        startActivity(intent)
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
