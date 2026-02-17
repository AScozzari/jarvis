package it.edgvoip.jarvis.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: String? = null
)

data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("tenantSlug") val tenantSlug: String,
    @SerializedName("role") val role: String,
    /** Estensione SIP interna abbinata all'utente (se fornita dal backend). */
    @SerializedName("extension") val extension: String? = null
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("forceLogin") val forceLogin: Boolean? = null
)

data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user") val user: User,
    @SerializedName(value = "sipConfig", alternate = ["sip_config"]) val sipConfig: SipConfig? = null,
    @SerializedName(value = "apiBaseUrl", alternate = ["api_base_url"]) val apiBaseUrl: String? = null
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class RefreshResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class SipConfig(
    @SerializedName("server") val server: String? = null,
    @SerializedName("port") val port: Int? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("extension") val extension: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("realm") val realm: String? = null,
    @SerializedName("transport") val transport: String? = "WSS",
    @SerializedName("codecs") val codecs: List<String>? = null,
    @SerializedName(value = "caller_id_number", alternate = ["callerIdNumber"]) val callerIdNumber: String? = null,
    @SerializedName(value = "callerIdName", alternate = ["caller_id_name"]) val callerIdName: String? = null,
    @SerializedName(value = "display_name", alternate = ["displayName"]) val displayName: String? = null,
    @SerializedName(value = "stunServer", alternate = ["stun_server"]) val stunServer: String? = null,
    @SerializedName(value = "wsUrl", alternate = ["ws_url"]) val wsUrl: String? = null
) {
    val effectivePort: Int get() = port ?: 5060
    val effectiveRealm: String get() = realm?.takeIf { it.isNotBlank() } ?: (server ?: "")
    val sipUsername: String get() = extension?.takeIf { it.isNotBlank() } ?: username.orEmpty()
    val effectiveWsUrl: String get() = wsUrl?.takeIf { it.isNotBlank() } ?: "wss://ws.edgvoip.it:8443/ws"
    fun isValid(): Boolean = !server.isNullOrBlank() && (!username.isNullOrBlank() || !extension.isNullOrBlank())
}

data class DeviceRegisterRequest(
    @SerializedName("fcmToken") val fcmToken: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("deviceName") val deviceName: String
)

/** Risposta API che può avere la lista agenti in "data" o "agents". */
data class AgentsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<ChatbotAgent>? = null,
    @SerializedName("agents") val agents: List<ChatbotAgent>? = null,
    @SerializedName("error") val error: String? = null
) {
    /** Lista agenti (da data o agents per compatibilità backend). */
    fun agentsOrData(): List<ChatbotAgent> = data ?: agents ?: emptyList()
}

/**
 * Agente AI: può essere chatbot interno (agent_type = "chatbot") o ElevenLabs (agent_type = "elevenlabs").
 * L'endpoint /chatbot/agents restituisce una lista unificata con entrambi i tipi.
 */
data class ChatbotAgent(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName(value = "is_active", alternate = ["isActive"]) val isActive: Boolean = true,
    @SerializedName("agent_type") val agentType: String? = null,
    @SerializedName("voice_name") val voiceName: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("llm_model") val llmModel: String? = null,
    @SerializedName("elevenlabs_agent_id") val elevenLabsAgentId: String? = null,
    @SerializedName("channel_webrtc") val channelWebrtc: Boolean? = null,
    @SerializedName("channel_sip") val channelSip: Boolean? = null,
    @SerializedName("first_message") val firstMessage: String? = null
) {
    val isElevenLabs: Boolean get() = agentType?.lowercase() in listOf("elevenlabs", "eleven_labs")
    val isChatbot: Boolean get() = agentType == "chatbot" || agentType.isNullOrBlank()
    val isAssistantOrElevenLabs: Boolean get() = when (agentType?.lowercase()) {
        "elevenlabs", "eleven_labs", "chatbot", "assistant" -> true
        null, "" -> true
        "real_voice" -> false
        else -> true
    }
    val supportsVoice: Boolean get() = isElevenLabs && channelWebrtc == true && !elevenLabsAgentId.isNullOrBlank()
    val supportsText: Boolean get() = isChatbot || (isElevenLabs && !elevenLabsAgentId.isNullOrBlank())
}

data class ChatbotConfigResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("welcomeMessage") val welcomeMessage: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("systemPrompt") val systemPrompt: String? = null
)

data class MessageRequest(
    @SerializedName("message") val message: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("conversation_history") val conversationHistory: List<ChatMessage>
)

data class MessageResponse(
    @SerializedName("message") val message: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ChatConversation(
    @SerializedName("id") val id: String,
    @SerializedName("agentId") val agentId: String,
    @SerializedName("title") val title: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class Contact(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("company") val company: String? = null
)

data class CallRecord(
    @SerializedName("caller_id_number") val callerIdNumber: String? = null,
    @SerializedName("caller_id_name") val callerIdName: String? = null,
    @SerializedName("destination_number") val destinationNumber: String? = null,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("call_direction") val callDirection: String? = null,
    @SerializedName("direction") val direction: String? = null,
    @SerializedName("hangup_disposition") val hangupDisposition: String? = null,
    @SerializedName("disposition") val disposition: String? = null
) {
    val caller: String get() = callerIdNumber?.takeIf { it.isNotBlank() } ?: callerIdName.orEmpty()
    val callee: String get() = destinationNumber.orEmpty()
    val directionNormalized: String get() = callDirection?.takeIf { it.isNotBlank() } ?: direction.orEmpty()
    val dispositionNormalized: String get() = hangupDisposition?.takeIf { it.isNotBlank() } ?: disposition.orEmpty()
}

data class Notification(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("type") val type: String,
    @SerializedName("read") val read: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class NotificationsPageResponse(
    @SerializedName("notifications") val notifications: List<Notification> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20,
    @SerializedName("has_more") val hasMore: Boolean = false
)
