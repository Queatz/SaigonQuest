package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Friend
import models.Position
import kotlin.time.Clock


/**
 * Repository for friend operations
 *
 * This repository provides methods for saving, retrieving, and managing friendships.
 */

class FriendRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.FRIENDS_COLLECTION)
    
    /**
     * Create a new friendship
     *
     * @param friend The friendship to create
     * @return The created friendship with its key
     */
    fun create(friend: Friend): Friend {
        val result: DocumentCreateEntity<Friend> = collection.insertDocument(
            friend,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Get a friendship by its ID
     *
     * @param id The ID of the friendship
     * @return The friendship, or null if not found
     */
    fun getById(id: String): Friend? {
        return try {
            collection.getDocument(
                id,
                Friend::class.java,
                DocumentReadOptions()
            )
        } catch (e: Throwable) {
            null
        }
    }
    
    /**
     * Get all friendships for a user
     *
     * @param userId The ID of the user
     * @return A list of friendships for the user
     */
    fun getByUserId(userId: String): List<Friend> {
        val query = """
            FOR f IN @@collection
            FILTER f.userId == @userId
            RETURN f
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.FRIENDS_COLLECTION,
            "userId" to userId
        )
        
        return ArangoConfig.db.query(query, Friend::class.java, bindVars).toList()
    }
    
    /**
     * Check if a friendship exists between two users
     *
     * @param userId The ID of the first user
     * @param friendId The ID of the second user
     * @return True if a friendship exists, false otherwise
     */
    fun existsFriendship(userId: String, friendId: String): Boolean {
        val query = """
            FOR f IN @@collection
            FILTER (f.userId == @userId AND f.friendId == @friendId) OR
                   (f.userId == @friendId AND f.friendId == @userId)
            LIMIT 1
            RETURN f
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.FRIENDS_COLLECTION,
            "userId" to userId,
            "friendId" to friendId
        )
        
        val results = ArangoConfig.db.query(query, Friend::class.java, bindVars).toList()
        return results.isNotEmpty()
    }
    
    /**
     * Delete a friendship
     *
     * @param userId The ID of the first user
     * @param friendId The ID of the second user
     * @return True if the friendship was deleted, false otherwise
     */
    fun deleteFriendship(userId: String, friendId: String): Boolean {
        val query = """
            FOR f IN @@collection
            FILTER (f.userId == @userId AND f.friendId == @friendId) OR
                   (f.userId == @friendId AND f.friendId == @userId)
            REMOVE f IN @@collection
            RETURN OLD
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.FRIENDS_COLLECTION,
            "userId" to userId,
            "friendId" to friendId
        )
        
        val results = ArangoConfig.db.query(query, Friend::class.java, bindVars).toList()
        return results.isNotEmpty()
    }
    
    /**
     * Update a friend's last active status, world, and position
     *
     * @param userId The ID of the user
     * @param lastWorldId The ID of the world
     * @param lastWorldName The name of the world
     * @param lastPosition The position in the world
     */
    fun updateFriendStatus(
        userId: String,
        lastWorldId: String,
        lastWorldName: String,
        lastPosition: Position?
    ) {
        val query = """
            FOR f IN @@collection
            FILTER f.friendId == @userId
            UPDATE f WITH { 
                lastActive: @lastActive, 
                lastWorldId: @lastWorldId, 
                lastWorldName: @lastWorldName,
                lastPosition: @lastPosition
            } IN @@collection
            RETURN NEW
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.FRIENDS_COLLECTION,
            "userId" to userId,
            "lastActive" to Clock.System.now(),
            "lastWorldId" to lastWorldId,
            "lastWorldName" to lastWorldName,
            "lastPosition" to lastPosition
        )
        
        ArangoConfig.db.query(query, Friend::class.java, bindVars).toList()
    }
    
    /**
     * Update a friend's note
     *
     * @param userId The ID of the user who owns the record
     * @param friendId The ID of the friend
     * @param note The new note content
     * @return True if successful
     */
    fun updateNote(userId: String, friendId: String, note: String): Boolean {
        val query = """
            FOR f IN @@collection
            FILTER f.userId == @userId AND f.friendId == @friendId
            UPDATE f WITH { note: @note } IN @@collection
            RETURN NEW
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.FRIENDS_COLLECTION,
            "userId" to userId,
            "friendId" to friendId,
            "note" to note
        )
        
        val results = ArangoConfig.db.query(query, Friend::class.java, bindVars).toList()
        return results.isNotEmpty()
    }
}
