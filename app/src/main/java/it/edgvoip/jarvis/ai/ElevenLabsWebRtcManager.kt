package it.edgvoip.jarvis.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import io.elevenlabs.models.ConversationMode
import io.elevenlabs.models.ConversationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AgentMode {
    LISTENING,
    SPEAKING
}

enum class AgentStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class ElevenLabsWebRtcManager {

    companion object {
        private const val TAG = "ElevenLabsWebRtc"
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }

    private val _status = MutableStateFlow(AgentStatus.DISCONNECTED)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _mode = MutableStateFlow(AgentMode.LISTENING)
    val mode: StateFlow<AgentMode> = _mode.asStateFlow()

    private val _vadScore = MutableStateFlow(0f)
    val vadScore: StateFlow<Float> = _vadScore.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _lastAgentMessage = MutableStateFlow<String?>(null)
    val lastAgentMessage: StateFlow<String?> = _lastAgentMessage.asStateFlow()

    private val _lastUserMessage = MutableStateFlow<String?>(null)
    val lastUserMessage: StateFlow<String?> = _lastUserMessage.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var session: ConversationSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timeoutJob: Job? = null
    private var lastAgentId: String? = null
    private var lastContext: Context? = null

    fun startConversation(context: Context, agentId: String) {
        if (session != null) {
            Log.w(TAG, "Session already active, disconnect first")
            return
        }

        lastAgentId = agentId
        lastContext = context
        _status.value = AgentStatus.CONNECTING
        _errorMessage.value = null

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_status.value == AgentStatus.CONNECTING) {
                Log.e(TAG, "Connection timeout after ${CONNECTION_TIMEOUT_MS}ms")
                _status.value = AgentStatus.ERROR
                _errorMessage.value = "Connessione scaduta. Tocca per riprovare."
                _isConnected.value = false
                try { session?.endSession() } catch (_: Exception) {}
                session = null
            }
        }

        val config = ConversationConfig(
            agentId = agentId,
            onConnect = { conversationId ->
                Log.i(TAG, "Connected: conversationId=$conversationId")
                timeoutJob?.cancel()
                _conversationId.value = conversationId
                _status.value = AgentStatus.CONNECTED
                _isConnected.value = true
                _isListening.value = true
                _isMuted.value = false
                _mode.value = AgentMode.LISTENING
                _errorMessage.value = null
            },
            onMessage = { source, messageJson ->
                Log.d(TAG, "Message [$source]: $messageJson")
                try {
                    val msg = extractMessageText(messageJson)
                    if (msg != null) {
                        when (source) {
                            "ai" -> _lastAgentMessage.value = msg
                            "user" -> _lastUserMessage.value = msg
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse message: ${e.message}")
                }
            },
            onModeChange = { mode ->
                Log.d(TAG, "Mode changed: $mode")
                when (mode) {
                    ConversationMode.SPEAKING -> {
                        _mode.value = AgentMode.SPEAKING
                        _isSpeaking.value = true
                        _isListening.value = false
                    }
                    ConversationMode.LISTENING -> {
                        _mode.value = AgentMode.LISTENING
                        _isSpeaking.value = false
                        _isListening.value = true
                    }
                    else -> {
                        Log.d(TAG, "Unknown mode: $mode")
                    }
                }
            },
            onStatusChange = { status ->
                Log.d(TAG, "Status changed: $status")
                when (status) {
                    ConversationStatus.CONNECTED -> {
                        _status.value = AgentStatus.CONNECTED
                        _errorMessage.value = null
                    }
                    ConversationStatus.CONNECTING -> _status.value = AgentStatus.CONNECTING
                    ConversationStatus.DISCONNECTED -> {
                        _status.value = AgentStatus.DISCONNECTED
                        _isConnected.value = false
                        _isListening.value = false
                        _isSpeaking.value = false
                        timeoutJob?.cancel()
                    }
                    else -> {
                        Log.d(TAG, "Unhandled status: $status")
                    }
                }
            },
            onVadScore = { score ->
                _vadScore.value = score
            },
        )

        scope.launch {
            try {
                session = ConversationClient.startSession(config, context)
                Log.i(TAG, "Session started for agentId=$agentId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session: ${e.message}", e)
                timeoutJob?.cancel()
                _status.value = AgentStatus.ERROR
                _errorMessage.value = when {
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connect", ignoreCase = true) == true ->
                        "Impossibile connettersi. Verifica la connessione internet."
                    e.message?.contains("permission", ignoreCase = true) == true ->
                        "Permesso microfono necessario."
                    e.message?.contains("agent", ignoreCase = true) == true ->
                        "Agente non disponibile. Verifica la configurazione."
                    else -> "Errore di connessione: ${e.localizedMessage ?: "Errore sconosciuto"}"
                }
                _isConnected.value = false
                session = null
            }
        }
    }

    fun retry() {
        val ctx = lastContext ?: return
        val id = lastAgentId ?: return
        disconnect()
        scope.launch {
            delay(300)
            startConversation(ctx, id)
        }
    }

    fun sendTextMessage(text: String) {
        val s = session
        if (s == null) {
            Log.w(TAG, "Cannot send message: no active session")
            return
        }
        try {
            s.sendUserMessage(text)
            _lastUserMessage.value = text
            Log.i(TAG, "Text message sent: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}", e)
        }
    }

    fun toggleMute() {
        val s = session ?: return
        scope.launch {
            try {
                s.toggleMute()
                _isMuted.value = !_isMuted.value
                Log.i(TAG, "Mute toggled: ${_isMuted.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle mute: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting session")
        timeoutJob?.cancel()
        val currentSession = session
        session = null
        _status.value = AgentStatus.DISCONNECTED
        _isConnected.value = false
        _isListening.value = false
        _isSpeaking.value = false
        _vadScore.value = 0f
        _conversationId.value = null
        _lastAgentMessage.value = null
        _lastUserMessage.value = null
        _isMuted.value = false
        _errorMessage.value = null
        _mode.value = AgentMode.LISTENING
        if (currentSession != null) {
            scope.launch {
                try {
                    Log.i(TAG, "Ending ElevenLabs session...")
                    currentSession.endSession()
                    Log.i(TAG, "ElevenLabs session ended successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Error ending session: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private val gson = Gson()

    private fun extractMessageText(json: String): String? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            obj?.get("text")?.asString
        } catch (_: Exception) {
            null
        }
    }
}
