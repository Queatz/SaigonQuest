package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.UserBalanceHistory

class UserBalanceHistoryRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USER_BALANCE_HISTORY_COLLECTION)

    fun add(entry: UserBalanceHistory): UserBalanceHistory {
        val result: DocumentCreateEntity<UserBalanceHistory> = collection.insertDocument(
            entry,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    fun listRecent(userId: String, limit: Int = 5): List<UserBalanceHistory> {
        val query = """
            FOR h IN ${CollectionsConfig.USER_BALANCE_HISTORY_COLLECTION}
            FILTER h.userId == @userId
            SORT h.createdAt DESC
            LIMIT @limit
            RETURN h
        """.trimIndent()
        val bind = mapOf("userId" to userId, "limit" to limit)
        return ArangoConfig.db.query(query, UserBalanceHistory::class.java, bind).toList()
    }
}
