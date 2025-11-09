package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.AiPromptHistory

class AiHistoryRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.AI_PROMPT_HISTORY_COLLECTION)

    fun insert(entry: AiPromptHistory): AiPromptHistory {
        val result: DocumentCreateEntity<AiPromptHistory> = collection.insertDocument(
            entry,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    fun listByUserId(userId: String, limit: Int = 50): List<AiPromptHistory> {
        val aql = """
            FOR h IN @@collection
                FILTER h.userId == @userId
                SORT h.createdAt DESC
                LIMIT @limit
                RETURN h
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.AI_PROMPT_HISTORY_COLLECTION,
            "userId" to userId,
            "limit" to limit,
        )
        return ArangoConfig.db.query(aql, AiPromptHistory::class.java, bind).toList()
    }
}
