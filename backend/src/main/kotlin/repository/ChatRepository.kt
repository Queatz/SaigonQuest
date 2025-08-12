package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.ChatMessage
import models.ChatReadMarker

/**
 * Repository for chat message operations
 *
 * This repository provides methods for saving, retrieving, updating, and deleting chat messages,
 * as well as tracking unread messages per world.
 */
class ChatRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.CHAT_MESSAGES_COLLECTION)
    private val readMarkersCollection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.CHAT_READ_MARKERS_COLLECTION)

    /**
     * Save a chat message to the database
     *
     * @param message The chat message to save
     * @return The saved chat message with its key
     */
    fun saveMessage(message: ChatMessage): ChatMessage {
        val result: DocumentCreateEntity<ChatMessage> = collection.insertDocument(
            message,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Get chat messages for a specific world
     *
     * @param worldId The ID of the world to get messages for
     * @param limit The maximum number of messages to return (default: 100)
     * @param olderThan The timestamp to get messages older than (default: current time)
     * @return A list of chat messages for the world
     */
    fun getMessagesByWorldId(
        worldId: String,
        limit: Int = 100,
        olderThan: Long = System.currentTimeMillis()
    ): List<ChatMessage> {
        val query = """
            FOR m IN @@collection
            FILTER m.worldId == @worldId AND m.timestamp <= @olderThan
            SORT m.timestamp DESC
            LIMIT @limit
            RETURN m
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.CHAT_MESSAGES_COLLECTION,
            "worldId" to worldId,
            "limit" to limit,
            "olderThan" to olderThan
        )
        
        return ArangoConfig.db.query(query, ChatMessage::class.java, bindVars).toList()
    }

    /**
     * Get unread message count for a user in a specific world
     *
     * @param readMarker The ChatReadMarker object containing the user ID, world ID, and last read timestamp
     * @return The number of unread messages
     */
    fun getUnreadMessageCount(readMarker: ChatReadMarker): Int {
        val query = """
            FOR m IN @@collection
            FILTER m.worldId == @worldId 
               AND m.timestamp > @lastReadTimestamp
               AND m.userId != @userId
            COLLECT WITH COUNT INTO count
            RETURN count
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.CHAT_MESSAGES_COLLECTION,
            "worldId" to readMarker.worldId,
            "lastReadTimestamp" to readMarker.lastReadTimestamp,
            "userId" to readMarker.userId
        )
        
        val results = ArangoConfig.db.query(query, Int::class.java, bindVars).toList()
        return results.firstOrNull() ?: 0
    }
    
    /**
     * Update the last read timestamp for a user in a world
     *
     * @param userId The ID of the user
     * @param worldId The ID of the world
     * @param timestamp The timestamp to set as last read
     * @return True if the operation was successful, false otherwise
     */
    fun updateLastReadTimestamp(userId: String, worldId: String, timestamp: Long): Boolean {
        val readMarker = ChatReadMarker(
            userId = userId,
            worldId = worldId,
            lastReadTimestamp = timestamp
        )
        return updateLastReadTimestamp(readMarker)
    }
    
    /**
     * Update the last read timestamp for a user in a world
     *
     * @param readMarker The ChatReadMarker object containing the user ID, world ID, and timestamp
     * @return True if the operation was successful, false otherwise
     */
    fun updateLastReadTimestamp(readMarker: ChatReadMarker): Boolean {
        try {
            // Try to find an existing read marker
            val query = """
                FOR m IN @@collection
                FILTER m.userId == @userId AND m.worldId == @worldId
                RETURN m
            """.trimIndent()
            
            val bindVars = mapOf(
                "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
                "userId" to readMarker.userId,
                "worldId" to readMarker.worldId
            )
            
            val existingMarkers = ArangoConfig.db.query(query, ChatReadMarker::class.java, bindVars).toList()
            
            if (existingMarkers.isNotEmpty()) {
                // Update existing marker
                val existingMarker = existingMarkers.first()
                existingMarker.lastReadTimestamp = readMarker.lastReadTimestamp
                readMarkersCollection.updateDocument(existingMarker.key, existingMarker)
            } else {
                // Create new marker
                readMarkersCollection.insertDocument(readMarker)
            }
            
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    /**
     * Get the last read timestamp for a user in a world
     *
     * @param readMarker The ChatReadMarker object containing the user ID and world ID
     * @return The last read timestamp, or 0 if not found
     */
    fun getLastReadTimestamp(readMarker: ChatReadMarker): Long {
        try {
            val query = """
                FOR m IN @@collection
                FILTER m.userId == @userId AND m.worldId == @worldId
                RETURN m
            """.trimIndent()
            
            val bindVars = mapOf(
                "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
                "userId" to readMarker.userId,
                "worldId" to readMarker.worldId
            )
            
            val markers = ArangoConfig.db.query(query, ChatReadMarker::class.java, bindVars).toList()
            return markers.firstOrNull()?.lastReadTimestamp ?: 0
        } catch (e: Throwable) {
            return 0
        }
    }
    
    /**
     * Update a chat message
     *
     * @param messageKey The key of the message to update
     * @param updatedMessage The updated message data
     * @return The updated chat message, or null if the update failed
     */
    fun updateMessage(messageKey: String, updatedMessage: ChatMessage): ChatMessage? {
        try {
            // Ensure the message exists and belongs to the user trying to update it
            val existingMessage = collection.getDocument(messageKey, ChatMessage::class.java)
            
            // Only update if the user IDs match
            if (existingMessage.userId != updatedMessage.userId) {
                return null
            }
            
            // Update the message
            collection.updateDocument(messageKey, updatedMessage)
            
            // Return the updated message
            return collection.getDocument(messageKey, ChatMessage::class.java)
        } catch (e: Throwable) {
            return null
        }
    }
    
    /**
     * Delete a chat message (mark as deleted)
     *
     * @param messageKey The key of the message to delete
     * @param userId The ID of the user trying to delete the message
     * @return The updated (deleted) chat message, or null if the deletion failed
     */
    fun deleteMessage(messageKey: String, userId: String): ChatMessage? {
        try {
            // Ensure the message exists and belongs to the user trying to delete it
            val existingMessage = collection.getDocument(messageKey, ChatMessage::class.java)
            
            // Only delete if the user IDs match
            if (existingMessage.userId != userId) {
                return null
            }
            
            // Mark as deleted and clear message content
            val deletedMessage = existingMessage.copy(
                message = "",
                deleted = true,
                edited = false
            )
            
            // Update the message
            collection.updateDocument(messageKey, deletedMessage)
            
            // Return the updated message
            return collection.getDocument(messageKey, ChatMessage::class.java)
        } catch (e: Throwable) {
            return null
        }
    }
}
