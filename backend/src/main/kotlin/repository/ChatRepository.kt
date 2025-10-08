package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.ChatMessage
import models.ChatMessagePin
import models.ChatReadMarker
import models.World
import models.VisitedWorld
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Repository for chat message operations
 *
 * This repository provides methods for saving, retrieving, updating, and deleting chat messages,
 * as well as tracking unread messages per world.
 */

class ChatRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.CHAT_MESSAGES_COLLECTION)
    private val readMarkersCollection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.CHAT_READ_MARKERS_COLLECTION)
    private val pinsCollection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.CHAT_MESSAGE_PINS_COLLECTION)
    private val worldsCollection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.WORLDS_COLLECTION)

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
        olderThan: Instant = Clock.System.now(),
        minTimestamp: Instant? = null,
    ): List<ChatMessage> {
        val query = """
            FOR m IN @@collection
            FILTER m.worldId == @worldId 
               AND m.timestamp <= @olderThan
               AND (IS_NULL(@minTimestamp) OR m.timestamp >= @minTimestamp)
            SORT m.timestamp DESC
            LIMIT @limit
            RETURN m
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.CHAT_MESSAGES_COLLECTION,
            "worldId" to worldId,
            "limit" to limit,
            "olderThan" to olderThan,
            "minTimestamp" to minTimestamp
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
               AND NOT (m.senderType == "USER" AND m.senderId == @userId)
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
    fun updateLastReadTimestamp(userId: String, worldId: String, timestamp: Instant): Boolean {
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
        return try {
            val query = """
                UPSERT { userId: @userId, worldId: @worldId }
                INSERT { userId: @userId, worldId: @worldId, lastReadTimestamp: @timestamp, notificationSent: false, subscribed: true }
                UPDATE { lastReadTimestamp: @timestamp, notificationSent: false }
                IN @@collection
                RETURN NEW
            """.trimIndent()

            val bindVars = mapOf(
                "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
                "userId" to readMarker.userId,
                "worldId" to readMarker.worldId,
                "timestamp" to readMarker.lastReadTimestamp
            )

            val results = ArangoConfig.db.query(query, Map::class.java, bindVars).toList()
            results.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Get the last read timestamp for a user in a world
     *
     * @param readMarker The ChatReadMarker object containing the user ID and world ID
     * @return The last read timestamp, or 0 if not found
     */
    fun getLastReadTimestamp(readMarker: ChatReadMarker): Instant {
        return try {
            val query = """
                FOR m IN @@collection
                FILTER m.userId == @userId AND m.worldId == @worldId
                SORT m.lastReadTimestamp DESC
                LIMIT 1
                RETURN m
            """.trimIndent()

            val bindVars = mapOf(
                "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
                "userId" to readMarker.userId,
                "worldId" to readMarker.worldId
            )

            val marker = ArangoConfig.db.query(query, ChatReadMarker::class.java, bindVars).toList().firstOrNull()
            marker?.lastReadTimestamp ?: Instant.DISTANT_PAST
        } catch (e: Throwable) {
            e.printStackTrace()
            Instant.DISTANT_PAST
        }
    }
    
    /**
     * Update a chat message
     *
     * @param messageKey The key of the message to update
     * @param updatedMessage The updated message data
     * @return The updated chat message, or null if the update failed
     */
    fun updateMessage(messageKey: String, updatedMessage: ChatMessage, requesterId: String): ChatMessage? {
        try {
            // Load the existing message
            val existingMessage = collection.getDocument(messageKey, ChatMessage::class.java)

            // Permission checks
            val isAuthor = (existingMessage.senderType == models.ChatSenderType.USER && existingMessage.senderId == requesterId)
            val isWorldOwner = try {
                val world = worldsCollection.getDocument(existingMessage.worldId, World::class.java)
                world.userId == requesterId
            } catch (_: Throwable) { false }

            val canEdit = when (existingMessage.senderType) {
                models.ChatSenderType.USER -> isAuthor || isWorldOwner
                models.ChatSenderType.OBJECT, models.ChatSenderType.SYSTEM -> isWorldOwner
            }
            if (!canEdit) return null

            // Prevent changing sender identity/world
            val toSave = updatedMessage.copy(
                senderType = existingMessage.senderType,
                senderId = existingMessage.senderId,
                senderName = existingMessage.senderName,
                worldId = existingMessage.worldId,
            )

            // Update the message
            collection.updateDocument(messageKey, toSave)

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
    fun deleteMessage(messageKey: String, requesterId: String): ChatMessage? {
        try {
            // Load the message
            val existingMessage = collection.getDocument(messageKey, ChatMessage::class.java)

            // Allow deletion if the requester is permitted
            val isAuthor = (existingMessage.senderType == models.ChatSenderType.USER && existingMessage.senderId == requesterId)
            val isWorldOwner = try {
                val world = worldsCollection.getDocument(existingMessage.worldId, World::class.java)
                world.userId == requesterId
            } catch (_: Throwable) { false }

            val canDelete = when (existingMessage.senderType) {
                models.ChatSenderType.USER -> isAuthor || isWorldOwner
                models.ChatSenderType.OBJECT, models.ChatSenderType.SYSTEM -> isWorldOwner
            }
            if (!canDelete) return null

            // Mark as deleted and clear message content
            val deletedMessage = existingMessage.copy(
                message = "",
                deleted = true,
                edited = false
            )

            // Update the message
            collection.updateDocument(messageKey, deletedMessage)

            // Clean up any pins referencing this message
            try {
                removePinsForMessage(messageKey)
            } catch (_: Throwable) {}

            // Return the updated message
            return collection.getDocument(messageKey, ChatMessage::class.java)
        } catch (e: Throwable) {
            return null
        }
    }

    /**
     * List pinned messages for a world, newest first
     */
    fun listPins(worldId: String, limit: Int = 50, offset: Int = 0): List<ChatMessagePin> {
        val query = """
            FOR p IN @@pins
            FILTER p.worldId == @worldId
            SORT p.pinnedAt DESC
            LIMIT @offset, @limit
            RETURN p
        """.trimIndent()

        val bindVars = mapOf(
            "@pins" to CollectionsConfig.CHAT_MESSAGE_PINS_COLLECTION,
            "worldId" to worldId,
            "limit" to limit,
            "offset" to offset
        )

        return ArangoConfig.db.query(query, ChatMessagePin::class.java, bindVars).toList()
    }

    /**
     * Pin a message in a world. Idempotent: returns existing doc if already pinned.
     */
    fun pinMessage(worldId: String, messageId: String, userId: String): ChatMessagePin? {
        // Validate the message exists and belongs to the world
        val msg = try {
            collection.getDocument(messageId, ChatMessage::class.java)
        } catch (e: Throwable) { null }
        if (msg == null || msg.worldId != worldId) return null

        val query = """
            UPSERT { worldId: @worldId, messageId: @messageId }
            INSERT { worldId: @worldId, messageId: @messageId, pinnedByUserId: @userId, pinnedAt: DATE_ISO8601(DATE_NOW()) }
            UPDATE { pinnedByUserId: @userId, pinnedAt: DATE_ISO8601(DATE_NOW()) }
            IN @@pins
            RETURN NEW
        """.trimIndent()

        val bindVars = mapOf(
            "@pins" to CollectionsConfig.CHAT_MESSAGE_PINS_COLLECTION,
            "worldId" to worldId,
            "messageId" to messageId,
            "userId" to userId
        )

        val results = ArangoConfig.db.query(query, ChatMessagePin::class.java, bindVars).toList()
        return results.firstOrNull()
    }

    /**
     * Unpin a message in a world. Returns true if a pin was removed.
     */
    fun unpinMessage(worldId: String, messageId: String): Boolean {
        val query = """
            FOR p IN @@pins
            FILTER p.worldId == @worldId AND p.messageId == @messageId
            REMOVE p IN @@pins
            RETURN 1
        """.trimIndent()

        val bindVars = mapOf(
            "@pins" to CollectionsConfig.CHAT_MESSAGE_PINS_COLLECTION,
            "worldId" to worldId,
            "messageId" to messageId
        )

        val results = ArangoConfig.db.query(query, Int::class.java, bindVars).toList()
        return results.isNotEmpty()
    }

    /**
     * Remove all pins referencing a specific message ID (used on delete)
     */
    fun removePinsForMessage(messageId: String): Long {
        val query = """
            FOR p IN @@pins
            FILTER p.messageId == @messageId
            REMOVE p IN @@pins
            RETURN 1
        """.trimIndent()

        val bindVars = mapOf(
            "@pins" to CollectionsConfig.CHAT_MESSAGE_PINS_COLLECTION,
            "messageId" to messageId
        )

        return ArangoConfig.db.query(query, Int::class.java, bindVars).count().toLong()
    }

    /**
     * Get all read markers for a world
     */
    fun getReadMarkersByWorld(worldId: String): List<ChatReadMarker> {
        val query = """
            FOR m IN @@collection
            FILTER m.worldId == @worldId
            RETURN m
        """.trimIndent()
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
            "worldId" to worldId,
        )
        return ArangoConfig.db.query(query, ChatReadMarker::class.java, bindVars).toList()
    }

    /**
     * Set or clear the notificationSent flag for a specific user/world marker
     */
    fun setNotificationSent(userId: String, worldId: String, sent: Boolean): Boolean {
        val query = """
            FOR m IN @@collection
            FILTER m.userId == @userId AND m.worldId == @worldId
            UPDATE m WITH { notificationSent: @sent } IN @@collection
            RETURN NEW
        """.trimIndent()
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
            "userId" to userId,
            "worldId" to worldId,
            "sent" to sent,
        )
        val results = ArangoConfig.db.query(query, Map::class.java, bindVars).toList()
        return results.isNotEmpty()
    }

    /**
     * Set subscription status for a user in a world. Creates marker if missing.
     */
    fun setSubscription(userId: String, worldId: String, subscribed: Boolean): Boolean {
        return try {
            val query = """
                UPSERT { userId: @userId, worldId: @worldId }
                INSERT { userId: @userId, worldId: @worldId, lastReadTimestamp: DATE_ISO8601(DATE_NOW()), notificationSent: false, subscribed: @subscribed }
                UPDATE { subscribed: @subscribed } IN @@collection
                RETURN NEW
            """.trimIndent()
            val bindVars = mapOf(
                "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
                "userId" to userId,
                "worldId" to worldId,
                "subscribed" to subscribed,
            )
            val results = ArangoConfig.db.query(query, Map::class.java, bindVars).toList()
            results.isNotEmpty()
        } catch (_: Throwable) { false }
    }

    /**
     * Get subscription status for a user in a world. Defaults to true if no marker exists.
     */
    fun getSubscription(userId: String, worldId: String): Boolean {
        return try {
            val query = """
                FOR m IN @@collection
                FILTER m.userId == @userId AND m.worldId == @worldId
                LIMIT 1
                RETURN m.subscribed
            """.trimIndent()
            val bindVars = mapOf(
                "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
                "userId" to userId,
                "worldId" to worldId,
            )
            val result = ArangoConfig.db.query(query, Boolean::class.java, bindVars).toList().firstOrNull()
            result ?: true
        } catch (_: Throwable) { true }
    }

    /**
     * List visited worlds for a user, newest first
     */
    fun listVisitedWorlds(userId: String): List<VisitedWorld> {
        val query = """
            FOR m IN @@markers
            FILTER m.userId == @userId
            LET w = DOCUMENT(@@worlds, m.worldId)
            FILTER w != null
            SORT m.lastReadTimestamp DESC
            RETURN {
                world: w,
                lastVisited: m.lastReadTimestamp,
                subscribed: m.subscribed
            }
        """.trimIndent()
        val bindVars = mapOf(
            "@markers" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
            "@worlds" to CollectionsConfig.WORLDS_COLLECTION,
            "userId" to userId,
        )
        return ArangoConfig.db.query(query, VisitedWorld::class.java, bindVars).toList()
    }

    /**
     * Delete a read marker for a user/world (remove visited record)
     */
    fun deleteReadMarker(userId: String, worldId: String): Boolean {
        val query = """
            FOR m IN @@collection
            FILTER m.userId == @userId AND m.worldId == @worldId
            REMOVE m IN @@collection
            RETURN 1
        """.trimIndent()
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.CHAT_READ_MARKERS_COLLECTION,
            "userId" to userId,
            "worldId" to worldId,
        )
        val results = ArangoConfig.db.query(query, Int::class.java, bindVars).toList()
        return results.isNotEmpty()
    }
}
