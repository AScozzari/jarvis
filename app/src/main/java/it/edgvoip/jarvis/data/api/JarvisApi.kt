package it.edgvoip.jarvis.data.api

import it.edgvoip.jarvis.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface JarvisApi {

    @POST("api/{slug}/login")
    suspend fun login(
        @Path("slug") slug: String,
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginResponse>>

    @POST("api/{slug}/refresh-token")
    suspend fun refreshToken(
        @Path("slug") slug: String,
        @Body request: RefreshRequest
    ): Response<ApiResponse<RefreshResponse>>

    @POST("api/{slug}/logout")
    suspend fun logout(
        @Path("slug") slug: String,
        @Body request: LogoutRequest
    ): Response<ApiResponse<Unit>>

    @GET("api/{slug}/mobile/sip/mobile-config")
    suspend fun getSipConfig(
        @Path("slug") slug: String
    ): Response<ApiResponse<SipConfig>>

    @POST("api/{slug}/mobile/devices/register")
    suspend fun registerDevice(
        @Path("slug") slug: String,
        @Body request: DeviceRegisterRequest
    ): Response<ApiResponse<Unit>>

    @HTTP(method = "DELETE", path = "api/{slug}/mobile/devices/unregister", hasBody = true)
    suspend fun unregisterDevice(
        @Path("slug") slug: String,
        @Body request: DeviceRegisterRequest
    ): Response<ApiResponse<Unit>>

    @GET("api/{slug}/chatbot/agents")
    suspend fun getChatbotAgents(
        @Path("slug") slug: String
    ): Response<AgentsResponse>

    @GET("api/chatbot/public/{agentId}/config")
    suspend fun getChatbotConfig(
        @Path("agentId") agentId: String
    ): Response<ApiResponse<ChatbotConfigResponse>>

    @POST("api/chatbot/public/{agentId}/message")
    suspend fun sendChatbotMessage(
        @Path("agentId") agentId: String,
        @Body request: MessageRequest
    ): Response<ApiResponse<MessageResponse>>

    @GET("api/{slug}/crm/contacts")
    suspend fun getContacts(
        @Path("slug") slug: String
    ): Response<ApiResponse<List<Contact>>>

    @GET("api/{slug}/cdr")
    suspend fun getCallHistory(
        @Path("slug") slug: String
    ): Response<ApiResponse<List<CallRecord>>>

    @GET("api/{slug}/notifications")
    suspend fun getNotifications(
        @Path("slug") slug: String
    ): Response<ApiResponse<List<Notification>>>

    @PUT("api/{slug}/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Path("slug") slug: String,
        @Path("id") id: String
    ): Response<ApiResponse<Unit>>

    @PUT("api/{slug}/notifications/read-all")
    suspend fun markAllNotificationsRead(
        @Path("slug") slug: String
    ): Response<ApiResponse<Unit>>

    @DELETE("api/{slug}/notifications/{id}")
    suspend fun deleteNotification(
        @Path("slug") slug: String,
        @Path("id") id: String
    ): Response<ApiResponse<Unit>>
}
