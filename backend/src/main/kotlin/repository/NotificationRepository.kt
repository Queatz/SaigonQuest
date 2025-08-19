package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import debug.WebSocketDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import models.Notification
import models.NotificationData
import models.WebSocketAction
import push.PushNotificationService
import routes.WebSocketMessage
import routes.activeConnections
import routes.handlers.WebSocketUtils
import utils.json

class NotificationRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.NOTIFICATIONS_COLLECTION)

    private val scope = CoroutineScope(Dispatchers.Default)

    fun create(notification: Notification): Notification {
        val result: DocumentCreateEntity<Notification> = collection.insertDocument(
            notification,
            DocumentCreateOptions().returnNew(true)
        )
        val createdNotification = result.new

        // Send WebSocket notification to the user using a coroutine
        scope.launch {
            try {
                // Create NotificationData from the created notification
                val notificationData = NotificationData(
                    id = createdNotification.key,
                    userId = createdNotification.userId,
                    title = createdNotification.title,
                    description = createdNotification.description,
                    isRead = createdNotification.isRead,
                    type = createdNotification.type,
                    worldId = createdNotification.worldId,
                    objectId = createdNotification.objectId,
                    otherUserId = createdNotification.otherUserId,
                    timestamp = createdNotification.timestamp
                )

                // Create WebSocketMessage with NOTIFICATION action and NotificationData
                val webSocketMessage = WebSocketMessage(
                    action = WebSocketAction.NOTIFICATION,
                    data = json.encodeToString(notificationData)
                )

                // Convert WebSocketMessage to JSON string
                val messageString = json.encodeToString(webSocketMessage)

                // Send WebSocket message to the user
                WebSocketUtils.broadcastToUser(
                    userId = createdNotification.userId,
                    message = messageString,
                    activeConnections = activeConnections
                )

                WebSocketDebug.logBroadcast(
                    "NOTIFICATION_SENT",
                    "Notification sent to user ${createdNotification.userId}: ${createdNotification.title}",
                    "notification"
                )

                // Send Web Push notification if no WebSocket is connected and keys are configured
                try {
                    PushNotificationService.sendIfEligible(createdNotification)
                } catch (e: Throwable) {
                    WebSocketDebug.logBroadcast(
                        "PUSH_SEND_ERROR",
                        "Error queueing web push for user ${createdNotification.userId}: ${e.message}",
                        "notification"
                    )
                }
            } catch (e: Throwable) {
                WebSocketDebug.logBroadcast(
                    "NOTIFICATION_ERROR",
                    "Error sending notification to user ${createdNotification.userId}: ${e.message}",
                    "notification"
                )
            }
        }

        return createdNotification
    }

    fun getByUserId(userId: String, limit: Int = 100): List<Notification> {
        val query = """
            FOR n IN ${CollectionsConfig.NOTIFICATIONS_COLLECTION}
            FILTER n.userId == @userId
            SORT n.timestamp DESC
            LIMIT @limit
            RETURN n
        """.trimIndent()
        val bind = mapOf("userId" to userId, "limit" to limit)
        return ArangoConfig.db.query(query, Notification::class.java, bind).toList()
    }

    fun getUnreadCount(userId: String): Int {
        val query = """
            RETURN LENGTH(
              FOR n IN ${CollectionsConfig.NOTIFICATIONS_COLLECTION}
              FILTER n.userId == @userId AND n.read != true
              RETURN 1
            )
        """.trimIndent()
        val bind = mapOf("userId" to userId)
        val list = ArangoConfig.db.query(query, Int::class.java, bind).toList()
        return list.firstOrNull() ?: 0
    }

    fun getById(id: String): Notification? {
        return try { collection.getDocument(id, Notification::class.java) } catch (_: Throwable) { null }
    }

    fun markAsRead(id: String): Notification? {
        val existing = getById(id) ?: return null
        existing.isRead = true
        return collection.updateDocument(id, existing, com.arangodb.model.DocumentUpdateOptions().returnNew(true)).new
    }

    fun markAllAsRead(userId: String): Int {
        val query = """
            FOR n IN ${CollectionsConfig.NOTIFICATIONS_COLLECTION}
            FILTER n.userId == @userId AND n.read == false
            UPDATE n WITH { read: true } IN ${CollectionsConfig.NOTIFICATIONS_COLLECTION}
            COLLECT WITH COUNT INTO length
            RETURN length
        """.trimIndent()
        val bind = mapOf("userId" to userId)
        val list = ArangoConfig.db.query(query, Int::class.java, bind).toList()
        return list.firstOrNull() ?: 0
    }
}
