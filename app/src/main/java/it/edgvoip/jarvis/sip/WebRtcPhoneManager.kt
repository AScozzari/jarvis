package it.edgvoip.jarvis.sip

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.edgvoip.jarvis.data.model.SipConfig
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcPhoneManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WebRtcPhoneManager"
        private const val REGISTER_EXPIRES = 300
        private const val KEEPALIVE_INTERVAL_MS = 60_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val WS_CLOSE_NORMAL = 1000
        private const val ICE_GATHER_TIMEOUT_MS = 3000L
        private const val LOCAL_AUDIO_TRACK_ID = "jarvis_audio_0"
        private const val LOCAL_STREAM_ID = "jarvis_stream_0"
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
    private var keepAliveJob: Job? = null
    private var reconnectJob: Job? = null
    private var callIdCounter = 0
    private var reconnectAttempts = 0
    private var intentionalDisconnect = false

    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    private val cseqCounter = AtomicInteger(1)
    private var registerCallId: String = ""
    private var currentCallSipId: String = ""
    private var localTag: String = ""
    private var remoteTag: String = ""
    private var currentInviteBranch: String = ""
    private var currentInviteCSeq: Int = 0
    private var pendingAuthMethod: String = ""
    private var incomingInviteRaw: String = ""

    private var localSdp: String = ""
    private var remoteSdp: String = ""

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var webRtcInitialized = false
    private var iceGatheringComplete: CompletableDeferred<Unit>? = null

    fun initialize(config: SipConfig) {
        Log.i(TAG, "Initializing WebRTC Phone Manager: ${config.server}:${config.effectivePort}")
        currentConfig = config
        isInitialized = true
        initWebRtc()
        Log.i(TAG, "WebRTC Phone Manager initialized")
    }

    private fun initWebRtc() {
        if (webRtcInitialized) return
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

            webRtcInitialized = true
            Log.i(TAG, "WebRTC PeerConnectionFactory initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC: ${e.message}", e)
        }
    }

    private fun createPeerConnection(): PeerConnection? {
        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "PeerConnectionFactory not initialized")
            return null
        }

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE connection state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        Log.i(TAG, "ICE connected - media flowing")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.e(TAG, "ICE connection failed")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "ICE disconnected")
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "ICE receiving: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state: $state")
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringComplete?.complete(Unit)
                }
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d(TAG, "ICE candidate: ${candidate?.sdp}")
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.i(TAG, "Remote stream added: ${stream?.audioTracks?.size} audio tracks")
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.i(TAG, "Remote stream removed")
            }

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.i(TAG, "Remote track added: ${receiver?.track()?.kind()}")
            }
        }

        return factory.createPeerConnection(rtcConfig, observer)
    }

    private fun addLocalAudioTrack(pc: PeerConnection) {
        val factory = peerConnectionFactory ?: return

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, localAudioSource).apply {
            setEnabled(true)
        }

        pc.addTrack(localAudioTrack, listOf(LOCAL_STREAM_ID))
        Log.i(TAG, "Local audio track added to PeerConnection")
    }

    private suspend fun createOfferSdp(): String? {
        val pc = peerConnection ?: return null
        val sdpDeferred = CompletableDeferred<String?>()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    sdpDeferred.complete(null)
                    return
                }
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local description set")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Set local description failed: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
                sdpDeferred.complete(sdp.description)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer failed: $error")
                sdpDeferred.complete(null)
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)

        return withTimeoutOrNull(5000L) { sdpDeferred.await() }
    }

    private suspend fun createAnswerSdp(): String? {
        val pc = peerConnection ?: return null
        val sdpDeferred = CompletableDeferred<String?>()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    sdpDeferred.complete(null)
                    return
                }
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local answer description set")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Set local answer description failed: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
                sdpDeferred.complete(sdp.description)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer failed: $error")
                sdpDeferred.complete(null)
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)

        return withTimeoutOrNull(5000L) { sdpDeferred.await() }
    }

    private fun setRemoteDescription(sdpString: String, type: SessionDescription.Type) {
        val pc = peerConnection ?: return
        val sdp = SessionDescription(type, sdpString)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "Remote description set (${type.name})")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote description failed: $error")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }

    private suspend fun waitForIceGathering() {
        iceGatheringComplete = CompletableDeferred()
        val pc = peerConnection
        if (pc == null || pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            return
        }
        withTimeoutOrNull(ICE_GATHER_TIMEOUT_MS) {
            iceGatheringComplete?.await()
        }
        Log.i(TAG, "ICE gathering finished (state: ${pc.iceGatheringState()})")
    }

    private fun setupPeerConnectionForCall() {
        disposePeerConnection()

        peerConnection = createPeerConnection()
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection")
            return
        }
        addLocalAudioTrack(peerConnection!!)
        Log.i(TAG, "PeerConnection created for call")
    }

    private fun disposePeerConnection() {
        try {
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing PeerConnection: ${e.message}")
        }
        localAudioTrack = null
        localAudioSource = null
        peerConnection = null
    }

    fun register() {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized, cannot register")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        val config = currentConfig ?: run {
            Log.w(TAG, "No SIP config available")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        _registrationState.value = RegistrationState.REGISTERING
        intentionalDisconnect = false
        reconnectAttempts = 0

        connectWebSocket(config)
    }

    fun unregister() {
        Log.i(TAG, "Unregistering SIP account")
        _registrationState.value = RegistrationState.UNREGISTERING

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            hangupCall()
        }

        stopKeepAlive()
        sendSipUnregister()

        scope.launch {
            delay(1000)
            intentionalDisconnect = true
            disconnectWebSocket()
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

        currentCallSipId = generateCallId()
        localTag = generateTag()
        remoteTag = ""
        pendingAuthMethod = "INVITE"

        setAudioModeInCall()

        scope.launch {
            setupPeerConnectionForCall()
            val sdp = createOfferSdp()
            if (sdp != null) {
                waitForIceGathering()
                localSdp = peerConnection?.localDescription?.description ?: sdp
            } else {
                localSdp = generateFallbackSdp(config)
            }
            sendSipInvite(number, config)
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
        val config = currentConfig ?: return
        setAudioModeInCall()

        scope.launch {
            setupPeerConnectionForCall()

            if (remoteSdp.isNotBlank()) {
                setRemoteDescription(remoteSdp, SessionDescription.Type.OFFER)
            }

            val answerSdp = createAnswerSdp()
            if (answerSdp != null) {
                waitForIceGathering()
                localSdp = peerConnection?.localDescription?.description ?: answerSdp
            } else {
                localSdp = generateFallbackSdp(config)
            }

            sendSip200OkForInvite()
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

        when (currentState) {
            CallState.CALLING, CallState.RINGING -> sendSipCancel()
            CallState.INCOMING -> sendSip486Busy()
            CallState.CONNECTED, CallState.HOLDING -> sendSipBye()
            else -> {}
        }

        endCallCleanup("User hangup")
    }

    fun holdCall() {
        if (_callState.value != CallState.CONNECTED) {
            Log.w(TAG, "Cannot hold: call not connected")
            return
        }
        Log.i(TAG, "Putting call on hold")
        localAudioTrack?.setEnabled(false)
        sendSipReInvite(hold = true)
    }

    fun unholdCall() {
        if (_callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot unhold: call not on hold")
            return
        }
        Log.i(TAG, "Resuming call from hold")
        localAudioTrack?.setEnabled(!_isMuted.value)
        sendSipReInvite(hold = false)
    }

    fun muteCall() {
        if (_callState.value != CallState.CONNECTED && _callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot mute: no active call")
            return
        }
        Log.i(TAG, "Muting microphone")
        localAudioTrack?.setEnabled(false)
        _isMuted.value = true
    }

    fun unmuteCall() {
        Log.i(TAG, "Unmuting microphone")
        localAudioTrack?.setEnabled(true)
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
        sendSipInfo(digit)
    }

    fun transferCall(number: String) {
        if (_callState.value != CallState.CONNECTED && _callState.value != CallState.HOLDING) {
            Log.w(TAG, "Cannot transfer: no active call")
            return
        }

        val config = currentConfig ?: return
        val transferUri = "sip:$number@${config.effectiveRealm}"
        Log.i(TAG, "Transferring call to: $transferUri")
        sendSipRefer(transferUri)
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

    fun shutdown() {
        Log.i(TAG, "Shutting down WebRTC Phone Manager")
        stopDurationTimer()
        stopKeepAlive()

        if (_callState.value != CallState.IDLE && _callState.value != CallState.DISCONNECTED) {
            hangupCall()
        }

        intentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectWebSocket()
        disposePeerConnection()

        try {
            audioDeviceModule?.release()
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing WebRTC factory: ${e.message}")
        }
        peerConnectionFactory = null
        audioDeviceModule = null
        webRtcInitialized = false

        isInitialized = false
        _registrationState.value = RegistrationState.UNREGISTERED
        Log.i(TAG, "WebRTC Phone Manager shut down")
    }

    fun isReady(): Boolean = isInitialized && _registrationState.value == RegistrationState.REGISTERED

    private fun setAudioModeInCall() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    private fun resetAudioMode() {
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private fun endCallCleanup(reason: String) {
        Log.i(TAG, "Call ended: $reason")
        stopDurationTimer()
        disposePeerConnection()
        resetAudioMode()

        _callState.value = CallState.DISCONNECTED
        _currentCall.value = _currentCall.value?.copy(state = CallState.DISCONNECTED)
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isOnHold.value = false
        incomingInviteRaw = ""
        remoteSdp = ""

        scope.launch {
            delay(2000)
            if (_callState.value == CallState.DISCONNECTED) {
                _callState.value = CallState.IDLE
                _currentCall.value = null
            }
        }
    }

    // ── WebSocket ──

    private fun connectWebSocket(config: SipConfig) {
        val wsUrl = config.effectiveWsUrl
        Log.i(TAG, "Connecting WebSocket to: $wsUrl")

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Sec-WebSocket-Protocol", "sip")
            .build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                reconnectAttempts = 0
                registerCallId = generateCallId()
                localTag = generateTag()
                cseqCounter.set(1)
                sendSipRegister(config)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSipMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                handleWebSocketDisconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(WS_CLOSE_NORMAL, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                handleWebSocketDisconnect()
            }
        })
    }

    private fun disconnectWebSocket() {
        try {
            webSocket?.close(WS_CLOSE_NORMAL, "Bye")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient = null
    }

    private fun handleWebSocketDisconnect() {
        if (intentionalDisconnect) return

        if (_registrationState.value == RegistrationState.REGISTERED ||
            _registrationState.value == RegistrationState.REGISTERING
        ) {
            _registrationState.value = RegistrationState.FAILED
        }

        stopKeepAlive()
        attemptReconnect()
    }

    private fun attemptReconnect() {
        if (intentionalDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached")
            _registrationState.value = RegistrationState.FAILED
            return
        }

        reconnectAttempts++
        val delayMs = (INITIAL_RECONNECT_DELAY_MS * (1L shl (reconnectAttempts - 1).coerceAtMost(4)))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        Log.i(TAG, "Reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            val config = currentConfig ?: return@launch
            disconnectWebSocket()
            _registrationState.value = RegistrationState.REGISTERING
            connectWebSocket(config)
        }
    }

    private fun startKeepAlive() {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            while (true) {
                delay(KEEPALIVE_INTERVAL_MS)
                if (_registrationState.value == RegistrationState.REGISTERED) {
                    val config = currentConfig ?: break
                    Log.d(TAG, "Sending keep-alive re-REGISTER")
                    sendSipRegister(config)
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
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

    // ── SIP Message Sending ──

    private fun sendRaw(message: String) {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "Cannot send SIP: WebSocket not connected")
            return
        }
        Log.d(TAG, ">>> SIP OUT:\n${message.take(500)}")
        ws.send(message)
    }

    private fun sendSipRegister(config: SipConfig, authHeader: String? = null) {
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val branch = generateBranch()
        val cseq = cseqCounter.getAndIncrement()

        val sb = StringBuilder()
        sb.appendLine("REGISTER sip:$domain SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <sip:$username@$domain>")
        sb.appendLine("Call-ID: $registerCallId")
        sb.appendLine("CSeq: $cseq REGISTER")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>;expires=$REGISTER_EXPIRES")
        sb.appendLine("Allow: INVITE,ACK,BYE,CANCEL,OPTIONS,MESSAGE,INFO,UPDATE,REFER,NOTIFY")
        sb.appendLine("Supported: path,outbound,gruu")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        if (authHeader != null) {
            sb.appendLine(authHeader)
        }
        sb.appendLine("Expires: $REGISTER_EXPIRES")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSipUnregister() {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val branch = generateBranch()
        val cseq = cseqCounter.getAndIncrement()

        val sb = StringBuilder()
        sb.appendLine("REGISTER sip:$domain SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <sip:$username@$domain>")
        sb.appendLine("Call-ID: $registerCallId")
        sb.appendLine("CSeq: $cseq REGISTER")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>;expires=0")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Expires: 0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSipInvite(number: String, config: SipConfig, authHeader: String? = null) {
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val displayName = config.displayName ?: config.callerIdName ?: username
        currentInviteBranch = generateBranch()
        currentInviteCSeq = cseqCounter.getAndIncrement()

        val sdpBytes = localSdp.toByteArray()

        val sb = StringBuilder()
        sb.appendLine("INVITE sip:$number@$domain SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$currentInviteBranch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: \"$displayName\" <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <sip:$number@$domain>")
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $currentInviteCSeq INVITE")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>")
        sb.appendLine("Allow: INVITE,ACK,BYE,CANCEL,OPTIONS,MESSAGE,INFO,UPDATE,REFER,NOTIFY")
        sb.appendLine("Supported: 100rel,timer,replaces,norefersub")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        if (authHeader != null) {
            sb.appendLine(authHeader)
        }
        sb.appendLine("Content-Type: application/sdp")
        sb.appendLine("Content-Length: ${sdpBytes.size}")
        sb.appendLine()
        sb.append(localSdp)

        sendRaw(sb.toString())
    }

    private fun sendSipAck(toUri: String, remoteTagVal: String) {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val branch = generateBranch()
        val cseq = currentInviteCSeq

        val sb = StringBuilder()
        sb.appendLine("ACK $toUri SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <$toUri>;tag=$remoteTagVal")
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $cseq ACK")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSipBye() {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val number = _currentCall.value?.number ?: return
        val branch = generateBranch()
        val cseq = cseqCounter.getAndIncrement()

        val toUri = if (_currentCall.value?.direction == CallDirection.OUTGOING) {
            "sip:$number@$domain"
        } else {
            "sip:$username@$domain"
        }

        val sb = StringBuilder()
        sb.appendLine("BYE $toUri SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        if (_currentCall.value?.direction == CallDirection.OUTGOING) {
            sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
            sb.appendLine("To: <sip:$number@$domain>${if (remoteTag.isNotEmpty()) ";tag=$remoteTag" else ""}")
        } else {
            sb.appendLine("From: <sip:$number@$domain>${if (remoteTag.isNotEmpty()) ";tag=$remoteTag" else ""}")
            sb.appendLine("To: <sip:$username@$domain>;tag=$localTag")
        }
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $cseq BYE")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSipCancel() {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val number = _currentCall.value?.number ?: return

        val sb = StringBuilder()
        sb.appendLine("CANCEL sip:$number@$domain SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$currentInviteBranch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <sip:$number@$domain>")
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $currentInviteCSeq CANCEL")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSip486Busy() {
        if (incomingInviteRaw.isBlank()) return
        val parsed = parseSipMessage(incomingInviteRaw)
        val viaHeader = parsed.headers["via"] ?: parsed.headers["v"] ?: ""
        val fromHeader = parsed.headers["from"] ?: parsed.headers["f"] ?: ""
        val toHeader = parsed.headers["to"] ?: parsed.headers["t"] ?: ""
        val callIdHeader = parsed.headers["call-id"] ?: parsed.headers["i"] ?: currentCallSipId
        val cseqHeader = parsed.headers["cseq"] ?: "1 INVITE"

        val sb = StringBuilder()
        sb.appendLine("SIP/2.0 486 Busy Here")
        sb.appendLine("Via: $viaHeader")
        sb.appendLine("From: $fromHeader")
        sb.appendLine("To: $toHeader;tag=$localTag")
        sb.appendLine("Call-ID: $callIdHeader")
        sb.appendLine("CSeq: $cseqHeader")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSip200OkForInvite() {
        if (incomingInviteRaw.isBlank()) return
        val parsed = parseSipMessage(incomingInviteRaw)
        val viaHeader = parsed.headers["via"] ?: parsed.headers["v"] ?: ""
        val fromHeader = parsed.headers["from"] ?: parsed.headers["f"] ?: ""
        val toHeader = parsed.headers["to"] ?: parsed.headers["t"] ?: ""
        val callIdHeader = parsed.headers["call-id"] ?: parsed.headers["i"] ?: currentCallSipId
        val cseqHeader = parsed.headers["cseq"] ?: "1 INVITE"
        val config = currentConfig ?: return
        val username = config.sipUsername

        val sdpBytes = localSdp.toByteArray()

        val sb = StringBuilder()
        sb.appendLine("SIP/2.0 200 OK")
        sb.appendLine("Via: $viaHeader")
        sb.appendLine("From: $fromHeader")
        sb.appendLine("To: $toHeader;tag=$localTag")
        sb.appendLine("Call-ID: $callIdHeader")
        sb.appendLine("CSeq: $cseqHeader")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>")
        sb.appendLine("Allow: INVITE,ACK,BYE,CANCEL,OPTIONS,MESSAGE,INFO,UPDATE,REFER,NOTIFY")
        sb.appendLine("Supported: 100rel,timer,replaces,norefersub")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Type: application/sdp")
        sb.appendLine("Content-Length: ${sdpBytes.size}")
        sb.appendLine()
        sb.append(localSdp)

        sendRaw(sb.toString())

        _callState.value = CallState.CONNECTED
        _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
        callStartTime = System.currentTimeMillis()
        startDurationTimer()
        Log.i(TAG, "Incoming call answered - connected")
    }

    private fun sendSipReInvite(hold: Boolean) {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val number = _currentCall.value?.number ?: return
        val branch = generateBranch()
        val cseq = cseqCounter.getAndIncrement()
        val direction = if (hold) "sendonly" else "sendrecv"

        val sdp = localSdp.replace("a=sendrecv", "a=$direction")
            .replace("a=sendonly", "a=$direction")
            .replace("a=recvonly", "a=$direction")
        val sdpBytes = sdp.toByteArray()

        val toUri = "sip:$number@$domain"

        val sb = StringBuilder()
        sb.appendLine("INVITE $toUri SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <$toUri>${if (remoteTag.isNotEmpty()) ";tag=$remoteTag" else ""}")
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $cseq INVITE")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Type: application/sdp")
        sb.appendLine("Content-Length: ${sdpBytes.size}")
        sb.appendLine()
        sb.append(sdp)

        sendRaw(sb.toString())

        if (hold) {
            _callState.value = CallState.HOLDING
            _currentCall.value = _currentCall.value?.copy(state = CallState.HOLDING)
            _isOnHold.value = true
        } else {
            _callState.value = CallState.CONNECTED
            _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
            _isOnHold.value = false
        }
    }

    private fun sendSipInfo(digit: Char) {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val number = _currentCall.value?.number ?: return
        val branch = generateBranch()
        val cseq = cseqCounter.getAndIncrement()

        val body = "Signal=$digit\r\nDuration=160\r\n"
        val bodyBytes = body.toByteArray()

        val toUri = "sip:$number@$domain"

        val sb = StringBuilder()
        sb.appendLine("INFO $toUri SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <$toUri>${if (remoteTag.isNotEmpty()) ";tag=$remoteTag" else ""}")
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $cseq INFO")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>")
        sb.appendLine("Content-Type: application/dtmf-relay")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: ${bodyBytes.size}")
        sb.appendLine()
        sb.append(body)

        sendRaw(sb.toString())
    }

    private fun sendSipRefer(targetUri: String) {
        val config = currentConfig ?: return
        val username = config.sipUsername
        val domain = config.effectiveRealm
        val number = _currentCall.value?.number ?: return
        val branch = generateBranch()
        val cseq = cseqCounter.getAndIncrement()

        val toUri = "sip:$number@$domain"

        val sb = StringBuilder()
        sb.appendLine("REFER $toUri SIP/2.0")
        sb.appendLine("Via: SIP/2.0/WSS ${generateViaHost()};branch=$branch;rport")
        sb.appendLine("Max-Forwards: 70")
        sb.appendLine("From: <sip:$username@$domain>;tag=$localTag")
        sb.appendLine("To: <$toUri>${if (remoteTag.isNotEmpty()) ";tag=$remoteTag" else ""}")
        sb.appendLine("Call-ID: $currentCallSipId")
        sb.appendLine("CSeq: $cseq REFER")
        sb.appendLine("Contact: <sip:$username@${generateViaHost()};transport=ws>")
        sb.appendLine("Refer-To: <$targetUri>")
        sb.appendLine("Referred-By: <sip:$username@$domain>")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun sendSip200Ok(parsed: SipParsed) {
        val viaHeader = parsed.headers["via"] ?: parsed.headers["v"] ?: ""
        val fromHeader = parsed.headers["from"] ?: parsed.headers["f"] ?: ""
        val toHeader = parsed.headers["to"] ?: parsed.headers["t"] ?: ""
        val callIdHeader = parsed.headers["call-id"] ?: parsed.headers["i"] ?: ""
        val cseqHeader = parsed.headers["cseq"] ?: ""

        val sb = StringBuilder()
        sb.appendLine("SIP/2.0 200 OK")
        sb.appendLine("Via: $viaHeader")
        sb.appendLine("From: $fromHeader")
        sb.appendLine("To: $toHeader;tag=$localTag")
        sb.appendLine("Call-ID: $callIdHeader")
        sb.appendLine("CSeq: $cseqHeader")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    // ── SIP Message Parsing & Handling ──

    private data class SipParsed(
        val startLine: String,
        val isResponse: Boolean,
        val statusCode: Int,
        val method: String,
        val headers: Map<String, String>,
        val body: String
    )

    private fun parseSipMessage(raw: String): SipParsed {
        val parts = raw.split("\r\n\r\n", limit = 2)
        val headerPart = parts[0]
        val body = if (parts.size > 1) parts[1] else ""

        val lines = headerPart.split("\r\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            val linesLf = headerPart.split("\n").filter { it.isNotBlank() }
            if (linesLf.isEmpty()) return SipParsed("", false, 0, "", emptyMap(), "")
            return parseSipLines(linesLf, body)
        }
        return parseSipLines(lines, body)
    }

    private fun parseSipLines(lines: List<String>, body: String): SipParsed {
        val startLine = lines[0].trim()
        val isResponse = startLine.startsWith("SIP/2.0")
        val statusCode = if (isResponse) {
            startLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
        } else 0
        val method = if (!isResponse) {
            startLine.split(" ").firstOrNull() ?: ""
        } else {
            ""
        }

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                if (headers.containsKey(key)) {
                    headers[key] = headers[key] + ", " + value
                } else {
                    headers[key] = value
                }
            }
        }

        return SipParsed(startLine, isResponse, statusCode, method, headers, body)
    }

    private fun handleSipMessage(raw: String) {
        Log.d(TAG, "<<< SIP IN:\n${raw.take(500)}")

        val normalizedRaw = if (!raw.contains("\r\n")) raw.replace("\n", "\r\n") else raw
        val parsed = parseSipMessage(normalizedRaw)

        if (parsed.isResponse) {
            handleSipResponse(parsed, normalizedRaw)
        } else {
            handleSipRequest(parsed, normalizedRaw)
        }
    }

    private fun handleSipResponse(parsed: SipParsed, raw: String) {
        val cseqHeader = parsed.headers["cseq"] ?: ""
        val method = cseqHeader.split(" ").lastOrNull()?.uppercase() ?: ""

        Log.i(TAG, "SIP Response: ${parsed.statusCode} for $method")

        when {
            parsed.statusCode == 401 || parsed.statusCode == 407 -> {
                handleAuthChallenge(parsed, method, raw)
            }
            method == "REGISTER" -> handleRegisterResponse(parsed)
            method == "INVITE" -> handleInviteResponse(parsed, raw)
            method == "BYE" -> {
                Log.i(TAG, "BYE response: ${parsed.statusCode}")
            }
            method == "CANCEL" -> {
                Log.i(TAG, "CANCEL response: ${parsed.statusCode}")
            }
            method == "REFER" -> {
                Log.i(TAG, "REFER response: ${parsed.statusCode}")
                if (parsed.statusCode == 202 || parsed.statusCode == 200) {
                    Log.i(TAG, "Transfer accepted")
                }
            }
        }
    }

    private fun handleRegisterResponse(parsed: SipParsed) {
        when (parsed.statusCode) {
            200 -> {
                _registrationState.value = RegistrationState.REGISTERED
                Log.i(TAG, "SIP REGISTERED successfully via WebSocket")
                startKeepAlive()
            }
            403 -> {
                _registrationState.value = RegistrationState.FAILED
                Log.e(TAG, "SIP registration FORBIDDEN (403)")
            }
            else -> {
                if (parsed.statusCode in 400..699) {
                    _registrationState.value = RegistrationState.FAILED
                    Log.e(TAG, "SIP registration failed: ${parsed.statusCode}")
                }
            }
        }
    }

    private fun handleInviteResponse(parsed: SipParsed, raw: String) {
        val toHeaderFull = parsed.headers["to"] ?: parsed.headers["t"] ?: ""
        val tagMatch = Regex(";tag=([^;,>\\s]+)").find(toHeaderFull)
        if (tagMatch != null) {
            remoteTag = tagMatch.groupValues[1]
        }

        when (parsed.statusCode) {
            100 -> {
                Log.i(TAG, "Call: Trying")
                _callState.value = CallState.CALLING
                _currentCall.value = _currentCall.value?.copy(state = CallState.CALLING)
            }
            180, 183 -> {
                Log.i(TAG, "Call: Ringing")
                _callState.value = CallState.RINGING
                _currentCall.value = _currentCall.value?.copy(state = CallState.RINGING)
                if (parsed.body.isNotBlank()) {
                    remoteSdp = parsed.body
                    setRemoteDescription(remoteSdp, SessionDescription.Type.PRANSWER)
                }
            }
            200 -> {
                Log.i(TAG, "Call: Connected (200 OK)")
                if (parsed.body.isNotBlank()) {
                    remoteSdp = parsed.body
                    setRemoteDescription(remoteSdp, SessionDescription.Type.ANSWER)
                }

                val number = _currentCall.value?.number ?: ""
                val config = currentConfig
                val domain = config?.effectiveRealm ?: ""
                val toUri = "sip:$number@$domain"
                sendSipAck(toUri, remoteTag)

                _callState.value = CallState.CONNECTED
                _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTED)
                callStartTime = System.currentTimeMillis()
                startDurationTimer()
                Log.i(TAG, "Call connected - media active via WebRTC PeerConnection")
            }
            in 300..399 -> {
                Log.w(TAG, "Call redirect: ${parsed.statusCode}")
                endCallCleanup("Redirect ${parsed.statusCode}")
            }
            486 -> {
                Log.i(TAG, "Call: Busy")
                endCallCleanup("Busy")
            }
            487 -> {
                Log.i(TAG, "Call: Cancelled")
                endCallCleanup("Cancelled")
            }
            in 400..699 -> {
                Log.e(TAG, "Call failed: ${parsed.statusCode}")
                endCallCleanup("Error ${parsed.statusCode}")
            }
        }
    }

    private fun handleSipRequest(parsed: SipParsed, raw: String) {
        Log.i(TAG, "SIP Request: ${parsed.method}")

        when (parsed.method.uppercase()) {
            "INVITE" -> handleIncomingInvite(parsed, raw)
            "BYE" -> {
                sendSip200Ok(parsed)
                endCallCleanup("Remote hangup")
            }
            "CANCEL" -> {
                sendSip200Ok(parsed)
                endCallCleanup("Cancelled by remote")
            }
            "ACK" -> {
                Log.i(TAG, "Received ACK")
            }
            "OPTIONS" -> {
                sendSip200Ok(parsed)
            }
            "NOTIFY" -> {
                sendSip200Ok(parsed)
                Log.i(TAG, "Received NOTIFY")
            }
            "INFO" -> {
                sendSip200Ok(parsed)
                Log.i(TAG, "Received INFO")
            }
            "MESSAGE" -> {
                sendSip200Ok(parsed)
                Log.i(TAG, "Received MESSAGE: ${parsed.body}")
            }
        }
    }

    private fun handleIncomingInvite(parsed: SipParsed, raw: String) {
        val fromHeader = parsed.headers["from"] ?: parsed.headers["f"] ?: ""
        val callerUri = extractUri(fromHeader)
        val callerDisplay = extractDisplayName(fromHeader)
        val callerNumber = extractUserFromUri(callerUri)
        val callIdHeader = parsed.headers["call-id"] ?: parsed.headers["i"] ?: generateCallId()

        if (_currentCall.value?.state == CallState.CONNECTED ||
            _currentCall.value?.state == CallState.HOLDING
        ) {
            val fromTag = extractTag(fromHeader)
            if (fromTag.isNotEmpty() && fromTag == remoteTag && callIdHeader == currentCallSipId) {
                Log.i(TAG, "Re-INVITE received (hold/unhold)")
                handleReInvite(parsed, raw)
                return
            }
        }

        incomingInviteRaw = raw
        currentCallSipId = callIdHeader
        remoteTag = extractTag(fromHeader)
        localTag = generateTag()

        if (parsed.body.isNotBlank()) {
            remoteSdp = parsed.body
        }

        sendSip180Ringing(parsed)

        handleIncomingCall(
            callId = callIdHeader,
            callerNumber = callerNumber,
            callerName = callerDisplay
        )
    }

    private fun handleReInvite(parsed: SipParsed, raw: String) {
        if (parsed.body.isNotBlank()) {
            remoteSdp = parsed.body
            setRemoteDescription(remoteSdp, SessionDescription.Type.OFFER)
        }
        incomingInviteRaw = raw
        val config = currentConfig ?: return

        scope.launch {
            val answerSdp = createAnswerSdp()
            if (answerSdp != null) {
                waitForIceGathering()
                localSdp = peerConnection?.localDescription?.description ?: answerSdp
            }
            sendSip200OkForInvite()
        }
    }

    private fun sendSip180Ringing(parsed: SipParsed) {
        val viaHeader = parsed.headers["via"] ?: parsed.headers["v"] ?: ""
        val fromHeader = parsed.headers["from"] ?: parsed.headers["f"] ?: ""
        val toHeader = parsed.headers["to"] ?: parsed.headers["t"] ?: ""
        val callIdHeader = parsed.headers["call-id"] ?: parsed.headers["i"] ?: ""
        val cseqHeader = parsed.headers["cseq"] ?: ""

        val sb = StringBuilder()
        sb.appendLine("SIP/2.0 180 Ringing")
        sb.appendLine("Via: $viaHeader")
        sb.appendLine("From: $fromHeader")
        sb.appendLine("To: $toHeader;tag=$localTag")
        sb.appendLine("Call-ID: $callIdHeader")
        sb.appendLine("CSeq: $cseqHeader")
        sb.appendLine("User-Agent: JarvisAndroid/1.0")
        sb.appendLine("Content-Length: 0")
        sb.appendLine()

        sendRaw(sb.toString())
    }

    private fun handleAuthChallenge(parsed: SipParsed, method: String, raw: String) {
        val config = currentConfig ?: return
        val wwwAuth = parsed.headers["www-authenticate"]
            ?: parsed.headers["proxy-authenticate"]
            ?: run {
                Log.e(TAG, "No auth challenge header found")
                return
            }

        Log.i(TAG, "Handling auth challenge for $method")

        val realm = extractAuthParam(wwwAuth, "realm")
        val nonce = extractAuthParam(wwwAuth, "nonce")
        val algorithm = extractAuthParam(wwwAuth, "algorithm").ifEmpty { "MD5" }
        val qop = extractAuthParam(wwwAuth, "qop")

        val username = config.sipUsername
        val password = config.password ?: ""
        val domain = config.effectiveRealm

        val uri = when (method) {
            "REGISTER" -> "sip:$domain"
            "INVITE" -> "sip:${_currentCall.value?.number ?: ""}@$domain"
            else -> "sip:$domain"
        }

        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")

        val response: String
        val cnonce: String
        val nc: String

        if (qop.contains("auth")) {
            cnonce = generateCnonce()
            nc = "00000001"
            response = md5Hex("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            cnonce = ""
            nc = ""
            response = md5Hex("$ha1:$nonce:$ha2")
        }

        val headerName = if (parsed.statusCode == 407) "Proxy-Authorization" else "Authorization"
        val authLine = buildString {
            append("$headerName: Digest username=\"$username\"")
            append(", realm=\"$realm\"")
            append(", nonce=\"$nonce\"")
            append(", uri=\"$uri\"")
            append(", response=\"$response\"")
            append(", algorithm=$algorithm")
            if (qop.contains("auth")) {
                append(", qop=auth")
                append(", nc=$nc")
                append(", cnonce=\"$cnonce\"")
            }
        }

        when (method) {
            "REGISTER" -> sendSipRegister(config, authLine)
            "INVITE" -> {
                val number = _currentCall.value?.number ?: return
                sendSipInvite(number, config, authLine)
            }
        }
    }

    // ── SDP Fallback (when PeerConnection unavailable) ──

    private fun generateFallbackSdp(config: SipConfig): String {
        val sessionId = System.currentTimeMillis()
        val codecs = config.codecs ?: listOf("PCMU", "PCMA")

        val sb = StringBuilder()
        sb.append("v=0\r\n")
        sb.append("o=- $sessionId $sessionId IN IP4 0.0.0.0\r\n")
        sb.append("s=JarvisAndroid\r\n")
        sb.append("c=IN IP4 0.0.0.0\r\n")
        sb.append("t=0 0\r\n")

        val payloadTypes = mutableListOf<Int>()
        val rtpmapLines = mutableListOf<String>()

        for (codec in codecs) {
            when (codec.uppercase()) {
                "PCMU" -> {
                    payloadTypes.add(0)
                    rtpmapLines.add("a=rtpmap:0 PCMU/8000\r\n")
                }
                "PCMA" -> {
                    payloadTypes.add(8)
                    rtpmapLines.add("a=rtpmap:8 PCMA/8000\r\n")
                }
                "G722" -> {
                    payloadTypes.add(9)
                    rtpmapLines.add("a=rtpmap:9 G722/8000\r\n")
                }
                "OPUS" -> {
                    payloadTypes.add(111)
                    rtpmapLines.add("a=rtpmap:111 opus/48000/2\r\n")
                    rtpmapLines.add("a=fmtp:111 minptime=10;useinbandfec=1\r\n")
                }
                "TELEPHONE-EVENT", "101" -> {
                    payloadTypes.add(101)
                    rtpmapLines.add("a=rtpmap:101 telephone-event/8000\r\n")
                    rtpmapLines.add("a=fmtp:101 0-16\r\n")
                }
            }
        }

        if (!payloadTypes.contains(101)) {
            payloadTypes.add(101)
            rtpmapLines.add("a=rtpmap:101 telephone-event/8000\r\n")
            rtpmapLines.add("a=fmtp:101 0-16\r\n")
        }

        val ptList = payloadTypes.joinToString(" ")
        sb.append("m=audio 9 UDP/TLS/RTP/SAVPF $ptList\r\n")

        for (line in rtpmapLines) {
            sb.append(line)
        }

        sb.append("a=sendrecv\r\n")
        sb.append("a=rtcp-mux\r\n")
        sb.append("a=ptime:20\r\n")
        sb.append("a=maxptime:150\r\n")

        return sb.toString()
    }

    // ── Utility Functions ──

    private fun generateBranch(): String = "z9hG4bK${UUID.randomUUID().toString().replace("-", "").take(16)}"
    private fun generateTag(): String = UUID.randomUUID().toString().replace("-", "").take(10)
    private fun generateCallId(): String = "${UUID.randomUUID().toString().replace("-", "").take(16)}@jarvis.android"
    private fun generateViaHost(): String = "${UUID.randomUUID().toString().take(8)}.invalid"
    private fun generateCnonce(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun extractAuthParam(header: String, param: String): String {
        val regex = Regex("""$param\s*=\s*"?([^",\s]+)"?""", RegexOption.IGNORE_CASE)
        return regex.find(header)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun extractUri(headerValue: String): String {
        val match = Regex("<([^>]+)>").find(headerValue)
        return match?.groupValues?.getOrNull(1) ?: headerValue
    }

    private fun extractDisplayName(headerValue: String): String {
        val match = Regex("""^"?([^"<]+)"?\s*<""").find(headerValue.trim())
        return match?.groupValues?.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
    }

    private fun extractUserFromUri(uri: String): String {
        val match = Regex("""sip:([^@]+)@""").find(uri)
        return match?.groupValues?.getOrNull(1) ?: uri
    }

    private fun extractTag(headerValue: String): String {
        val match = Regex(""";tag=([^;,>\s]+)""").find(headerValue)
        return match?.groupValues?.getOrNull(1) ?: ""
    }
}
