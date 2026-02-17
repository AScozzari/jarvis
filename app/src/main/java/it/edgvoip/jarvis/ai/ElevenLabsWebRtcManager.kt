package it.edgvoip.jarvis.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentMode {
    LISTENING,
    SPEAKING
}

enum class AgentStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class ElevenLabsWebRtcManager {

    companion object {
        private const val TAG = "ElevenLabsWebRtc"
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

    private var session: ConversationSession? = null

    fun startConversation(context: Context, agentId: String) {
        if (session != null) {
            Log.w(TAG, "Session already active, disconnect first")
            return
        }

        _status.value = AgentStatus.CONNECTING

        val config = ConversationConfig(
            agentId = agentId,
            onConnect = { conversationId ->
                Log.i(TAG, "Connected: conversationId=$conversationId")
                _conversationId.value = conversationId
                _status.value = AgentStatus.CONNECTED
                _isConnected.value = true
                _isListening.value = true
                _mode.value = AgentMode.LISTENING
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
            onModeChange = { modeStr ->
                Log.d(TAG, "Mode changed: $modeStr")
                when (modeStr) {
                    "speaking" -> {
                        _mode.value = AgentMode.SPEAKING
                        _isSpeaking.value = true
                        _isListening.value = false
                    }
                    "listening" -> {
                        _mode.value = AgentMode.LISTENING
                        _isSpeaking.value = false
                        _isListening.value = true
                    }
                }
            },
            onStatusChange = { statusStr ->
                Log.d(TAG, "Status changed: $statusStr")
                when (statusStr) {
                    "connected" -> _status.value = AgentStatus.CONNECTED
                    "connecting" -> _status.value = AgentStatus.CONNECTING
                    "disconnected" -> {
                        _status.value = AgentStatus.DISCONNECTED
                        _isConnected.value = false
                        _isListening.value = false
                        _isSpeaking.value = false
                    }
                }
            },
            onVadScore = { score ->
                _vadScore.value = score
            },
        )

        try {
            session = ConversationClient.startSession(config, context)
            Log.i(TAG, "Session started for agentId=$agentId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session: ${e.message}", e)
            _status.value = AgentStatus.DISCONNECTED
            _isConnected.value = false
            session = null
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
        try {
            s.toggleMute()
            _isMuted.value = !_isMuted.value
            Log.i(TAG, "Mute toggled: ${_isMuted.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle mute: ${e.message}", e)
        }
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting session")
        try {
            session?.endSession()
        } catch (e: Exception) {
            Log.w(TAG, "Error ending session: ${e.message}")
        }
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
        _mode.value = AgentMode.LISTENING
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
