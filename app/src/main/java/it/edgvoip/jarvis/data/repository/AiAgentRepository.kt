package it.edgvoip.jarvis.data.repository

import android.util.Log
import it.edgvoip.jarvis.BuildConfig
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.db.ConversationDao
import it.edgvoip.jarvis.data.db.ConversationEntity
import it.edgvoip.jarvis.data.db.MessageDao
import it.edgvoip.jarvis.data.db.MessageEntity
import it.edgvoip.jarvis.data.model.ChatMessage
import it.edgvoip.jarvis.data.model.ChatbotAgent
import it.edgvoip.jarvis.data.model.MessageRequest
import it.edgvoip.jarvis.data.model.MessageResponse
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAgentRepository @Inject constructor(
    private val api: JarvisApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val tokenManager: TokenManager
) {

    companion object {
        private const val TAG = "AiAgentRepository"
    }

    suspend fun getAvailableAgents(): Result<List<ChatbotAgent>> {
        return try {
            val slug = tokenManager.getTenantSlug()
                ?: return Result.failure(Exception("Nessun tenant configurato"))
            val response = api.getChatbotAgents(slug)
            if (BuildConfig.DEBUG) {
                val body = response.body()
                val errBody = response.errorBody()?.string()
                Log.d(TAG, "getChatbotAgents: code=${response.code()}, success=${body?.success}, data.size=${body?.data?.size}, agents.size=${body?.agents?.size}, error=${body?.error}, errorBody=${errBody?.take(200)}")
                if (body != null && body.success) {
                    body.agentsOrData().forEachIndexed { i, a ->
                        Log.d(TAG, "  agent[$i]: id=${a.id}, name=${a.name}, agentType=${a.agentType}, isActive=${a.isActive}")
                    }
                }
            }
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.agentsOrData())
                } else {
                    Result.failure(Exception(body?.error ?: "Errore nel caricamento degli agenti"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("Errore del server (${response.code()})${errorBody?.take(100)?.let { ": $it" }.orEmpty()}"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Impossibile connettersi al server"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Timeout della connessione"))
        } catch (e: Exception) {
            Result.failure(Exception("Errore di rete: ${e.localizedMessage}"))
        }
    }

    suspend fun sendMessage(
        agentId: String,
        message: String,
        sessionId: String,
        history: List<ChatMessage>
    ): Result<MessageResponse> {
        return try {
            val request = MessageRequest(
                message = message,
                sessionId = sessionId,
                conversationHistory = history
            )
            val response = api.sendChatbotMessage(agentId, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.error ?: "Errore nella risposta dell'agente"))
                }
            } else {
                Result.failure(Exception("Errore del server (${response.code()})"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Impossibile connettersi al server"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Timeout della connessione"))
        } catch (e: Exception) {
            Result.failure(Exception("Errore di rete: ${e.localizedMessage}"))
        }
    }

    fun getConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByConversation(conversationId)
    }

    suspend fun getConversationById(id: String): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }

    suspend fun createConversation(
        agentId: String,
        agentName: String,
        firstMessage: String
    ): ConversationEntity {
        val now = Date()
        val title = if (firstMessage.length > 40) {
            firstMessage.take(40).trim() + "..."
        } else {
            firstMessage.trim()
        }
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            agentId = agentId,
            agentName = agentName,
            title = title,
            sessionId = UUID.randomUUID().toString(),
            lastMessage = "",
            createdAt = now,
            updatedAt = now
        )
        conversationDao.insertConversation(conversation)
        return conversation
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String
    ): MessageEntity {
        val now = Date()
        val message = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = now
        )
        val id = messageDao.insertMessage(message)
        conversationDao.updateLastMessage(conversationId, content, now)
        return message.copy(id = id)
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        conversationDao.updateTitle(id, newTitle)
    }

    suspend fun deleteConversation(id: String) {
        messageDao.deleteMessagesByConversation(id)
        conversationDao.deleteConversationById(id)
    }

    suspend fun searchConversations(query: String): List<ConversationEntity> {
        return conversationDao.searchConversations(query)
    }

    suspend fun clearConversationHistory(id: String) {
        messageDao.deleteMessagesByConversation(id)
        conversationDao.updateLastMessage(id, "", Date())
    }

    fun getTenantSlug(): String? = tokenManager.getTenantSlug()
}
