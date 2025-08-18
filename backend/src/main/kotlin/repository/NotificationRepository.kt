package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Notification

class NotificationRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.NOTIFICATIONS_COLLECTION)

    fun create(notification: Notification): Notification {
        val result: DocumentCreateEntity<Notification> = collection.insertDocument(
            notification,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
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
              FILTER n.userId == @userId AND n.isRead == false
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
            FILTER n.userId == @userId AND n.isRead == false
            UPDATE n WITH { isRead: true } IN ${CollectionsConfig.NOTIFICATIONS_COLLECTION}
            COLLECT WITH COUNT INTO length
            RETURN length
        """.trimIndent()
        val bind = mapOf("userId" to userId)
        val list = ArangoConfig.db.query(query, Int::class.java, bind).toList()
        return list.firstOrNull() ?: 0
    }
}
