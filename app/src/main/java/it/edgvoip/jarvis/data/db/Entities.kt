package it.edgvoip.jarvis.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["agent_id"])]
)
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "agent_name") val agentName: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "last_message") val lastMessage: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Date,
    @ColumnInfo(name = "updated_at") val updatedAt: Date
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation_id"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: Date
)

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["direction"]),
        Index(value = ["start_time"])
    ]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "caller") val caller: String,
    @ColumnInfo(name = "callee") val callee: String,
    @ColumnInfo(name = "start_time") val startTime: Date,
    @ColumnInfo(name = "duration") val duration: Int,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "disposition") val disposition: String
)

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["is_read"]),
        Index(value = ["created_at"])
    ]
)
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Date
)

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["email"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "phone") val phone: String?,
    @ColumnInfo(name = "email") val email: String?,
    @ColumnInfo(name = "company") val company: String?
)
