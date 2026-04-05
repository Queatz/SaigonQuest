package repository

import com.arangodb.ArangoCollection
import database.ArangoConfig
import database.CollectionsConfig
import models.UserEmojiHistory

class UserEmojiHistoryRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USER_EMOJI_HISTORY_COLLECTION)

    fun getByUserId(userId: String): UserEmojiHistory? {
        val query = "FOR h IN @@collection FILTER h.userId == @userId RETURN h"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.USER_EMOJI_HISTORY_COLLECTION,
            "userId" to userId
        )
        return ArangoConfig.db.query(query, UserEmojiHistory::class.java, bindVars).toList().firstOrNull()
    }

    fun recordEmoji(userId: String, emoji: String) {
        val existing = getByUserId(userId)
        val history = existing ?: UserEmojiHistory(userId = userId)
        
        val currentEmojis = history.emojis.toMutableList()
        
        // If an emoji already exists in the history when it's added, simply move it to the top
        currentEmojis.remove(emoji)
        currentEmojis.add(0, emoji)
        
        // Let's only show the last 20 emojis
        val updatedEmojis = currentEmojis.take(20)
        
        history.emojis = updatedEmojis
        
        if (existing == null) {
            collection.insertDocument(history)
        } else {
            collection.updateDocument(history.key, history)
        }
    }
}
