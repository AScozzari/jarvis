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
import org.linphone.core.Account
import org.linphone.core.AccountParams
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.LogLevel
import org.linphone.core.MediaEncryption
import org.linphone.core.RegistrationState as LinphoneRegistrationState
import org.linphone.core.TransportType
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

    private var core: Core? = null
    private var account: Account? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onRegistrationStateChanged(
            core: Core,
            account: Account,
            state: LinphoneRegistrationState?,
            message: String
        ) {
            Log.i(TAG, "Linphone registration state: $state - $message")
            when (state) {
                LinphoneRegistrationState.Ok -> {
                    _registrationState.value = RegistrationState.REGISTERED
                    Log.i(TAG, "SIP REGISTERED successfully")
                }
                LinphoneRegistrationState.Failed -> {
                    _registrationState.value = RegistrationState.FAILED
                    Log.e(TAG, "SIP registration FAILED: $message")
                }
                LinphoneRegistrationState.Progress -> {
                    _registrationState.value = RegistrationState.REGISTERING
                }
                LinphoneRegistrationState.Cleared -> {
                    _registrationState.value = RegistrationState.UNREGISTERED
                }
                else -> {}
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i(TAG, "Linphone call state: $state - $message")
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    val remoteAddress = call.remoteAddress
                    val number = remoteAddress.username ?: remoteAddress.asStringUriOnly()
                    val displayName = remoteAddress.displayName ?: ""
                    handleIncomingCall(
                        callId = call.callLog.callId ?: "incoming_${System.currentTimeMillis()}",
                        callerNumber = number,
                        callerName = displayName
                    )
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> {
                    _callState.value = CallState.CALLING
                    _currentCall.value = _currentCall.value?.copy(state = CallState.CALLING)
                }
                Call.State.OutgoingRinging -> {
                    _callState.value = CallState.RINGING
                    _currentCall.value = _currentCall.value?.copy(state = CallState.RINGING)
                    Log.i(TAG, "Call ringing")
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    _callState.value = CallState.CONNECTED
                    _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
                    callStartTime = System.currentTimeMillis()
                    startDurationTimer()
                    Log.i(TAG, "Call connected - audio streaming")
                }
                Call.State.Paused, Call.State.PausedByRemote -> {
                    _callState.value = CallState.HOLDING
                    _currentCall.value = _currentCall.value?.copy(state = CallState.HOLDING)
                    _isOnHold.value = true
                }
                Call.State.Resuming -> {
                    _callState.value = CallState.CONNECTED
                    _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
                    _isOnHold.value = false
                }
                Call.State.End, Call.State.Released -> {
                    stopDurationTimer()
                    _callState.value = CallState.DISCONNECTED
                    _currentCall.value = _currentCall.value?.copy(state = CallState.DISCONNECTED)
                    _isMuted.value = false
                    _isSpeakerOn.value = false
                    _isOnHold.value = false
                    Log.i(TAG, "Call ended")
                    scope.launch {
                        delay(2000)
                        if (_callState.value == CallState.DISCONNECTED) {
                            _callState.value = CallState.IDLE
                            _currentCall.value = null
                        }
                    }
                }
                Call.State.Error -> {
                    stopDurationTimer()
                    _callState.value = CallState.DISCONNECTED
                    _currentCall.value = _currentCall.value?.copy(state = CallState.DISCONNECTED)
                    Log.e(TAG, "Call error: $message")
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
    }

    fun initialize(config: SipConfig) {
        Log.i(TAG, "Initializing Linphone SIP stack: ${config.server}:${config.effectivePort}")
        currentConfig = config

        try {
            val factory = Factory.instance()
            factory.setLogCollectionPath(context.filesDir.absolutePath)
            factory.enableLogCollection(LogCollectionState.Enabled)
            factory.loggingService.setLogLevel(LogLevel.Warning)

            if (core == null) {
                core = factory.createCore(null, null, context)
            }

            core?.let { c ->
                c.isIpv6Enabled = false
                c.mediaEncryption = MediaEncryption.None

                val stunServer = config.stunServer
                if (!stunServer.isNullOrBlank()) {
                    val natPolicy = c.createNatPolicy()
                    natPolicy.stunServer = stunServer
                    natPolicy.isStunEnabled = true
                    natPolicy.isIceEnabled = true
                    c.natPolicy = natPolicy
                    Log.i(TAG, "STUN/ICE configured: $stunServer")
                }

                val codecs = config.codecs
                if (!codecs.isNullOrEmpty()) {
                    c.audioPayloadTypes.forEach { pt ->
                        pt.enable(codecs.any { codec ->
                            pt.mimeType.equals(codec, ignoreCase = true)
                        })
                    }
                    Log.i(TAG, "Codecs configured: ${codecs.joinToString(", ")}")
                }

                c.addListener(coreListener)
                c.start()
                isInitialized = true
                Log.i(TAG, "Linphone Core started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Linphone: ${e.message}", e)
            isInitialized = false
        }
    }

    fun register() {
        if (!isInitialized || core == null) {
            Log.w(TAG, "Linphone not initialized, cannot register")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        val config = currentConfig ?: run {
            Log.w(TAG, "No SIP config available")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        _registrationState.value = RegistrationState.REGISTERING
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val password = config.password ?: ""
        val port = config.effectivePort
        Log.i(TAG, "Registering SIP account: $username@$domain:$port")

        try {
            val c = core!!
            val factory = Factory.instance()

            val transport = when (config.transport?.uppercase()) {
                "TCP" -> TransportType.Tcp
                "TLS" -> TransportType.Tls
                else -> TransportType.Udp
            }

            val authInfo = factory.createAuthInfo(
                username, null, password, null, null, domain, null
            )
            c.addAuthInfo(authInfo)

            val accountParams: AccountParams = c.createAccountParams()
            val sipAddress = factory.createAddress("sip:$username@$domain:$port")
            accountParams.identityAddress = sipAddress

            val serverAddress = factory.createAddress("sip:$domain:$port")
            serverAddress?.transport = transport
            accountParams.serverAddress = serverAddress

            accountParams.isRegisterEnabled = true
            accountParams.setExpires(300)

            if (account != null) {
                c.removeAccount(account!!)
            }
            val newAccount = c.createAccount(accountParams)
            c.addAccount(newAccount)
            c.defaultAccount = newAccount
            account = newAccount

            Log.i(TAG, "Linphone account created: sip:$username@$domain:$port (${config.transport})")
        } catch (e: Exception) {
            Log.e(TAG, "Linphone registration failed: ${e.message}", e)
            _registrationState.value = RegistrationState.FAILED
        }
    }

    fun unregister() {
        Log.i(TAG, "Unregistering SIP account")
        _registrationState.value = RegistrationState.UNREGISTERING

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            hangupCall()
        }

        try {
            account?.let { acc ->
                val params = acc.params.clone()
                params.isRegisterEnabled = false
                acc.params = params
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during unregistration: ${e.message}", e)
            _registrationState.value = RegistrationState.UNREGISTERED
        }
    }

    fun makeCall(number: String) {
        if (_registrationState.value != RegistrationState.REGISTERED) {
            Log.w(TAG, "Cannot make call: not registered (state=${_registrationState.value})")
            return
        }

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            Log.w(TAG, "Cannot make call: another call in progress")
            return
        }

        val config = currentConfig ?: return
        val domain = config.effectiveRealm
        val sipUri = "sip:$number@$domain"
        val callId = "call_${++callIdCounter}_${System.currentTimeMillis()}"
        Log.i(TAG, "Making call to: $sipUri (id: $callId)")

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

        try {
            val c = core!!
            val address = Factory.instance().createAddress(sipUri)
            if (address != null) {
                val callParams = c.createCallParams(null)
                callParams?.isAudioEnabled = true
                callParams?.isVideoEnabled = false
                c.inviteAddressWithParams(address, callParams!!)
                Log.i(TAG, "Linphone call initiated to $sipUri")
            } else {
                Log.e(TAG, "Invalid SIP URI: $sipUri")
                _callState.value = CallState.DISCONNECTED
                _currentCall.value = callInfo.copy(state = CallState.DISCONNECTED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Linphone call failed: ${e.message}", e)
            _callState.value = CallState.DISCONNECTED
            _currentCall.value = callInfo.copy(state = CallState.DISCONNECTED)
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
        try {
            core?.currentCall?.accept()
        } catch (e: Exception) {
            Log.e(TAG, "Linphone answer failed: ${e.message}", e)
        }
    }

    fun hangupCall() {
        val currentState = _callState.value
        if (currentState == CallState.IDLE || currentState == CallState.DISCONNECTED) {
            Log.w(TAG, "No active call to hangup")
            return
        }

        Log.i(TAG, "Hanging up call")
        stopDurationTimer()

        try {
            core?.currentCall?.terminate()
                ?: core?.calls?.firstOrNull()?.terminate()
        } catch (e: Exception) {
            Log.e(TAG, "Linphone hangup failed: ${e.message}", e)
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
        try {
            core?.currentCall?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Linphone hold failed: ${e.message}", e)
        }
    }

    fun unholdCall() {
        if (_callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot unhold: call not on hold")
            return
        }
        Log.i(TAG, "Resuming call from hold")
        try {
            core?.currentCall?.resume()
                ?: core?.calls?.firstOrNull()?.resume()
        } catch (e: Exception) {
            Log.e(TAG, "Linphone unhold failed: ${e.message}", e)
        }
    }

    fun muteCall() {
        if (_callState.value != CallState.CONNECTED && _callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot mute: no active call")
            return
        }
        Log.i(TAG, "Muting microphone")
        core?.isMicEnabled = false
        _isMuted.value = true
    }

    fun unmuteCall() {
        Log.i(TAG, "Unmuting microphone")
        core?.isMicEnabled = true
        _isMuted.value = false
    }

    fun toggleSpeaker() {
        val newState = !_isSpeakerOn.value
        Log.i(TAG, "Speaker ${if (newState) "ON" else "OFF"}")

        core?.let { c ->
            val currentCall = c.currentCall ?: c.calls.firstOrNull()
            if (currentCall != null) {
                val audioDevices = c.audioDevices
                val targetType = if (newState) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
                val device = audioDevices.find { it.type == targetType }
                if (device != null) {
                    currentCall.outputAudioDevice = device
                }
            }
        }
        _isSpeakerOn.value = newState
    }

    fun sendDtmf(digit: Char) {
        if (_callState.value != CallState.CONNECTED) {
            Log.w(TAG, "Cannot send DTMF: call not connected")
            return
        }
        Log.i(TAG, "Sending DTMF: $digit")
        try {
            core?.currentCall?.sendDtmf(digit)
        } catch (e: Exception) {
            Log.e(TAG, "Linphone DTMF failed: ${e.message}", e)
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

        try {
            val address = Factory.instance().createAddress(transferUri)
            if (address != null) {
                core?.currentCall?.transferTo(address)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Linphone transfer failed: ${e.message}", e)
            hangupCall()
        }
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

        try {
            core?.removeListener(coreListener)
            account?.let { core?.removeAccount(it) }
            core?.clearAllAuthInfo()
            core?.stop()
            core = null
            account = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down Linphone: ${e.message}", e)
        }

        isInitialized = false
        _registrationState.value = RegistrationState.UNREGISTERED
        Log.i(TAG, "SIP manager shut down")
    }

    fun isReady(): Boolean = isInitialized && _registrationState.value == RegistrationState.REGISTERED

    fun isPjsipAvailable(): Boolean = true

    fun onCallStateChanged(callId: String, state: CallState) {
        Log.i(TAG, "External call state changed: $callId -> $state")
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
        Log.i(TAG, "External registration state changed: $state")
        _registrationState.value = state
    }
}
