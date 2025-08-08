package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Notification

/**
 * Repository for notification operations
 *
 * This repository provides methods for saving, retrieving, and managing notifications,
 * as well as tracking unread notifications.
 */
class NotificationRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.NOTIFICATIONS_COLLECTION)

    /**
     * Create a new notification
     *
     * @param notification The notification to create
     * @return The created notification with its key
     */
    fun create(notification: Notification): Notification {
        val result: DocumentCreateEntity<Notification> = collection.insertDocument(
            notification,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Get a notification by its ID
     *
     * @param id The ID of the notification
     * @return The notification, or null if not found
     */
    fun getById(id: String): Notification? {
        return try {
            collection.getDocument(
                id,
                Notification::class.java,
                DocumentReadOptions()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all notifications for a user
     *
     * @param userId The ID of the user
     * @param limit The maximum number of notifications to return (default: 100)
     * @return A list of notifications for the user
     */
    fun getByUserId(userId: String, limit: Int = 100): List<Notification> {
        val query = """
            FOR n IN @@collection
            FILTER n.userId == @userId
            SORT n.timestamp DESC
            LIMIT @limit
            RETURN n
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.NOTIFICATIONS_COLLECTION,
            "userId" to userId,
            "limit" to limit
        )
        
        return ArangoConfig.db.query(query, Notification::class.java, bindVars).toList()
    }
    
    /**
     * Mark a notification as read
     *
     * @param id The ID of the notification
     * @return The updated notification, or null if not found
     */
    fun markAsRead(id: String): Notification? {
        val notification = getById(id) ?: return null
        
        notification.isRead = true
        
        val result = collection.updateDocument(
            id,
            notification,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Mark all notifications for a user as read
     *
     * @param userId The ID of the user
     * @return The number of notifications marked as read
     */
    fun markAllAsRead(userId: String): Int {
        val query = """
            FOR n IN @@collection
            FILTER n.userId == @userId AND n.read != true
            UPDATE n WITH { read: true } IN @@collection
            RETURN OLD
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.NOTIFICATIONS_COLLECTION,
            "userId" to userId
        )
        
        return ArangoConfig.db.query(query, Notification::class.java, bindVars).toList().size
    }
    
    /**
     * Get the count of unread notifications for a user
     *
     * @param userId The ID of the user
     * @return The number of unread notifications
     */
    fun getUnreadCount(userId: String): Int {
        val query = """
            FOR n IN @@collection
            FILTER n.userId == @userId AND n.isRead == false
            COLLECT WITH COUNT INTO count
            RETURN count
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.NOTIFICATIONS_COLLECTION,
            "userId" to userId
        )
        
        val results = ArangoConfig.db.query(query, Int::class.java, bindVars).toList()
        return results.firstOrNull() ?: 0
    }
}
