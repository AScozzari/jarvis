package it.edgvoip.jarvis.data.repository

import android.util.Log
import it.edgvoip.jarvis.data.api.JarvisApi
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.db.NotificationDao
import it.edgvoip.jarvis.data.db.NotificationEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val api: JarvisApi,
    private val notificationDao: NotificationDao,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "NotificationRepository"
    }

    fun getNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications()
    }

    fun getUnreadCount(): Flow<Int> {
        return notificationDao.getUnreadCount()
    }

    suspend fun syncNotifications() {
        try {
            val slug = tokenManager.getTenantSlug() ?: return
            val response = api.getNotifications(slug)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                    val entities = body.data.notifications.map { notification ->
                        val createdAt = try {
                            dateFormat.parse(notification.createdAt) ?: Date()
                        } catch (e: Exception) {
                            Date()
                        }

                        NotificationEntity(
                            id = notification.id,
                            title = notification.title,
                            body = notification.body,
                            type = notification.type,
                            isRead = notification.read,
                            createdAt = createdAt
                        )
                    }

                    notificationDao.deleteAllNotifications()
                    notificationDao.insertNotifications(entities)
                    Log.i(TAG, "Sincronizzate ${entities.size} notifiche")
                }
            } else {
                Log.w(TAG, "Errore sincronizzazione notifiche: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore sincronizzazione notifiche: ${e.message}", e)
        }
    }

    suspend fun markAsRead(id: String) {
        try {
            notificationDao.markAsRead(id)

            val slug = tokenManager.getTenantSlug() ?: return
            val response = api.markNotificationRead(slug, id)
            if (!response.isSuccessful) {
                Log.w(TAG, "Errore API markAsRead: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore markAsRead: ${e.message}", e)
        }
    }

    suspend fun markAllAsRead() {
        try {
            notificationDao.markAllAsRead()

            val slug = tokenManager.getTenantSlug() ?: return
            val response = api.markAllNotificationsRead(slug)
            if (!response.isSuccessful) {
                Log.w(TAG, "Errore API markAllAsRead: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore markAllAsRead: ${e.message}", e)
        }
    }

    suspend fun deleteNotification(id: String) {
        try {
            val entity = notificationDao.getNotificationById(id)
            if (entity != null) {
                notificationDao.deleteNotification(entity)
            }

            val slug = tokenManager.getTenantSlug() ?: return
            val response = api.deleteNotification(slug, id)
            if (!response.isSuccessful) {
                Log.w(TAG, "Errore API deleteNotification: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore deleteNotification: ${e.message}", e)
        }
    }
}
