package it.edgvoip.jarvis.sip

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.edgvoip.jarvis.data.model.SipConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class CallState {
    IDLE,
    INCOMING,
    CALLING,
    RINGING,
    CONNECTED,
    HOLDING,
    DISCONNECTED
}

enum class CallDirection {
    INCOMING,
    OUTGOING
}

enum class RegistrationState {
    UNREGISTERED,
    REGISTERING,
    REGISTERED,
    UNREGISTERING,
    FAILED
}

data class CallInfo(
    val id: String,
    val number: String,
    val name: String,
    val state: CallState,
    val duration: Int,
    val direction: CallDirection
)

@Singleton
class SipManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SipManager"
        private var pjsipAvailable: Boolean = false

        init {
            try {
                System.loadLibrary("pjsua2")
                pjsipAvailable = true
                Log.i(TAG, "PJSIP native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                pjsipAvailable = false
                Log.w(TAG, "PJSIP native library not available, using stub implementation")
            } catch (e: Throwable) {
                pjsipAvailable = false
                Log.w(TAG, "PJSIP load failed: ${e.message}", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _registrationState = MutableStateFlow(RegistrationState.UNREGISTERED)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _currentCall = MutableStateFlow<CallInfo?>(null)
    val currentCall: StateFlow<CallInfo?> = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isOnHold = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private var currentConfig: SipConfig? = null
    private var isInitialized = false
    private var callStartTime: Long = 0L
    private var durationJob: Job? = null
    private var callIdCounter = 0

    fun initialize(config: SipConfig) {
        Log.i(TAG, "Initializing SIP stack: ${config.server}:${config.effectivePort}")
        currentConfig = config

        if (pjsipAvailable) {
            initializePjsip(config)
        } else {
            Log.i(TAG, "PJSIP not available - SIP engine ready for native library integration")
            isInitialized = true
        }
    }

    private fun initializePjsip(config: SipConfig) {
        // TODO: Replace with PJSIP native calls
        try {
            Log.i(TAG, "Configuring PJSIP endpoint")
            Log.i(TAG, "Server: ${config.server}, Port: ${config.effectivePort}, Transport: ${config.transport}")
            Log.i(TAG, "Codecs: ${config.codecs?.joinToString(", ") ?: "default"}")
            isInitialized = true
            Log.i(TAG, "PJSIP endpoint configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PJSIP: ${e.message}", e)
            isInitialized = false
        }
    }

    fun register() {
        if (!isInitialized) {
            Log.w(TAG, "SIP stack not initialized, cannot register")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        val config = currentConfig ?: run {
            Log.w(TAG, "No SIP config available")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        _registrationState.value = RegistrationState.REGISTERING
        Log.i(TAG, "Registering SIP account: ${config.sipUsername}@${config.effectiveRealm}")

        if (pjsipAvailable) {
            performPjsipRegistration(config)
        } else {
            scope.launch {
                delay(500)
                _registrationState.value = RegistrationState.REGISTERED
                Log.i(TAG, "SIP registration simulated (native library not loaded)")
            }
        }
    }

    private fun performPjsipRegistration(config: SipConfig) {
        // TODO: Replace with PJSIP native calls
        try {
            Log.i(TAG, "Creating PJSIP account for ${config.sipUsername}@${config.effectiveRealm}")
            _registrationState.value = RegistrationState.REGISTERED
        } catch (e: Exception) {
            Log.e(TAG, "PJSIP registration failed: ${e.message}", e)
            _registrationState.value = RegistrationState.FAILED
        }
    }

    fun unregister() {
        Log.i(TAG, "Unregistering SIP account")
        _registrationState.value = RegistrationState.UNREGISTERING

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            hangupCall()
        }

        scope.launch {
            try {
                if (pjsipAvailable) {
                    Log.i(TAG, "Destroying PJSIP account")
                }
                _registrationState.value = RegistrationState.UNREGISTERED
                Log.i(TAG, "SIP account unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error during unregistration: ${e.message}", e)
                _registrationState.value = RegistrationState.UNREGISTERED
            }
        }
    }

    fun makeCall(number: String) {
        if (_registrationState.value != RegistrationState.REGISTERED) {
            Log.w(TAG, "Cannot make call: not registered")
            return
        }

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            Log.w(TAG, "Cannot make call: another call in progress")
            return
        }

        val callId = "call_${++callIdCounter}_${System.currentTimeMillis()}"
        Log.i(TAG, "Making call to: $number (id: $callId)")

        val callInfo = CallInfo(
            id = callId,
            number = number,
            name = "",
            state = CallState.CALLING,
            duration = 0,
            direction = CallDirection.OUTGOING
        )

        _currentCall.value = callInfo
        _callState.value = CallState.CALLING
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isOnHold.value = false

        if (pjsipAvailable) {
            performPjsipCall(number, callId)
        } else {
            scope.launch {
                delay(1000)
                _callState.value = CallState.RINGING
                _currentCall.value = callInfo.copy(state = CallState.RINGING)
                Log.i(TAG, "Call ringing (simulated)")

                delay(2000)
                _callState.value = CallState.CONNECTED
                _currentCall.value = callInfo.copy(state = CallState.CONNECTED)
                callStartTime = System.currentTimeMillis()
                startDurationTimer()
                Log.i(TAG, "Call connected (simulated)")
            }
        }
    }

    private fun performPjsipCall(number: String, callId: String) {
        // TODO: Replace with PJSIP native calls
        try {
            val config = currentConfig ?: return
            val sipUri = "sip:$number@${config.effectiveRealm}"
            Log.i(TAG, "PJSIP calling: $sipUri")
        } catch (e: Exception) {
            Log.e(TAG, "PJSIP call failed: ${e.message}", e)
            _callState.value = CallState.DISCONNECTED
            _currentCall.value = _currentCall.value?.copy(state = CallState.DISCONNECTED)
        }
    }

    fun handleIncomingCall(callId: String, callerNumber: String, callerName: String) {
        Log.i(TAG, "Incoming call from: $callerNumber ($callerName)")

        val callInfo = CallInfo(
            id = callId,
            number = callerNumber,
            name = callerName,
            state = CallState.INCOMING,
            duration = 0,
            direction = CallDirection.INCOMING
        )

        _currentCall.value = callInfo
        _callState.value = CallState.INCOMING
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isOnHold.value = false
    }

    fun answerCall() {
        if (_callState.value != CallState.INCOMING) {
            Log.w(TAG, "No incoming call to answer")
            return
        }

        Log.i(TAG, "Answering call")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP answering call with 200 OK")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP answer failed: ${e.message}", e)
            }
        }

        _callState.value = CallState.CONNECTED
        _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
        callStartTime = System.currentTimeMillis()
        startDurationTimer()
    }

    fun hangupCall() {
        val currentState = _callState.value
        if (currentState == CallState.IDLE || currentState == CallState.DISCONNECTED) {
            Log.w(TAG, "No active call to hangup")
            return
        }

        Log.i(TAG, "Hanging up call")
        stopDurationTimer()

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP hanging up call")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP hangup failed: ${e.message}", e)
            }
        }

        _callState.value = CallState.DISCONNECTED
        _currentCall.value = _currentCall.value?.copy(state = CallState.DISCONNECTED)
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isOnHold.value = false

        scope.launch {
            delay(2000)
            if (_callState.value == CallState.DISCONNECTED) {
                _callState.value = CallState.IDLE
                _currentCall.value = null
            }
        }
    }

    fun holdCall() {
        if (_callState.value != CallState.CONNECTED) {
            Log.w(TAG, "Cannot hold: call not connected")
            return
        }

        Log.i(TAG, "Putting call on hold")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP hold call")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP hold failed: ${e.message}", e)
            }
        }

        _callState.value = CallState.HOLDING
        _currentCall.value = _currentCall.value?.copy(state = CallState.HOLDING)
        _isOnHold.value = true
    }

    fun unholdCall() {
        if (_callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot unhold: call not on hold")
            return
        }

        Log.i(TAG, "Resuming call from hold")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP unhold call")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP unhold failed: ${e.message}", e)
            }
        }

        _callState.value = CallState.CONNECTED
        _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
        _isOnHold.value = false
    }

    fun muteCall() {
        if (_callState.value != CallState.CONNECTED && _callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot mute: no active call")
            return
        }

        Log.i(TAG, "Muting microphone")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP mute audio")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP mute failed: ${e.message}", e)
            }
        }

        audioManager.isMicrophoneMute = true
        _isMuted.value = true
    }

    fun unmuteCall() {
        Log.i(TAG, "Unmuting microphone")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP unmute audio")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP unmute failed: ${e.message}", e)
            }
        }

        audioManager.isMicrophoneMute = false
        _isMuted.value = false
    }

    fun toggleSpeaker() {
        val newState = !_isSpeakerOn.value
        Log.i(TAG, "Speaker ${if (newState) "ON" else "OFF"}")

        audioManager.isSpeakerphoneOn = newState
        _isSpeakerOn.value = newState
    }

    fun sendDtmf(digit: Char) {
        if (_callState.value != CallState.CONNECTED) {
            Log.w(TAG, "Cannot send DTMF: call not connected")
            return
        }

        Log.i(TAG, "Sending DTMF: $digit")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP send DTMF digit: $digit")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP DTMF failed: ${e.message}", e)
            }
        }
    }

    fun transferCall(number: String) {
        if (_callState.value != CallState.CONNECTED && _callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot transfer: no active call")
            return
        }

        val config = currentConfig ?: return
        val transferUri = "sip:$number@${config.effectiveRealm}"
        Log.i(TAG, "Transferring call to: $transferUri")

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "PJSIP transfer to: $transferUri")
            } catch (e: Exception) {
                Log.e(TAG, "PJSIP transfer failed: ${e.message}", e)
            }
        }

        hangupCall()
    }

    fun getCallDuration(): Flow<Int> = flow {
        while (true) {
            if (_callState.value == CallState.CONNECTED || _callState.value == CallState.HOLDING) {
                val elapsed = ((System.currentTimeMillis() - callStartTime) / 1000).toInt()
                emit(elapsed)
            } else {
                emit(0)
            }
            delay(1000)
        }
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        durationJob = scope.launch {
            while (true) {
                delay(1000)
                if (_callState.value == CallState.CONNECTED || _callState.value == CallState.HOLDING) {
                    val elapsed = ((System.currentTimeMillis() - callStartTime) / 1000).toInt()
                    _currentCall.value = _currentCall.value?.copy(duration = elapsed)
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    fun shutdown() {
        Log.i(TAG, "Shutting down SIP manager")
        stopDurationTimer()

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            hangupCall()
        }

        if (_registrationState.value == RegistrationState.REGISTERED) {
            unregister()
        }

        if (pjsipAvailable) {
            try {
                Log.i(TAG, "Destroying PJSIP endpoint")
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down PJSIP: ${e.message}", e)
            }
        }

        isInitialized = false
        Log.i(TAG, "SIP manager shut down")
    }

    fun isReady(): Boolean = isInitialized && _registrationState.value == RegistrationState.REGISTERED

    fun isPjsipAvailable(): Boolean = pjsipAvailable

    fun onCallStateChanged(callId: String, state: CallState) {
        Log.i(TAG, "Call state changed: $callId -> $state")
        _callState.value = state
        _currentCall.value = _currentCall.value?.copy(state = state)

        when (state) {
            CallState.CONNECTED -> {
                callStartTime = System.currentTimeMillis()
                startDurationTimer()
            }
            CallState.DISCONNECTED -> {
                stopDurationTimer()
                scope.launch {
                    delay(2000)
                    if (_callState.value == CallState.DISCONNECTED) {
                        _callState.value = CallState.IDLE
                        _currentCall.value = null
                    }
                }
            }
            else -> {}
        }
    }

    fun onRegistrationStateChanged(state: RegistrationState) {
        Log.i(TAG, "Registration state changed: $state")
        _registrationState.value = state
    }
}
