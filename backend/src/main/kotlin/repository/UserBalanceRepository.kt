package repository

import com.arangodb.ArangoCollection
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.UserBalance

class UserBalanceRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USER_BALANCES_COLLECTION)

    fun getByUserId(userId: String): UserBalance? {
        val query = """
            FOR b IN ${CollectionsConfig.USER_BALANCES_COLLECTION}
            FILTER b.userId == @userId
            LIMIT 1
            RETURN b
        """.trimIndent()
        val bind = mapOf("userId" to userId)
        val list = ArangoConfig.db.query(query, UserBalance::class.java, bind).toList()
        return list.firstOrNull()
    }

    fun upsert(userId: String, update: (UserBalance) -> Unit): UserBalance {
        val existing = getByUserId(userId)
        val record = existing ?: UserBalance(userId = userId, balance = 0)
        update(record)
        return if (existing == null) {
            val res = collection.insertDocument(record, DocumentCreateOptions().returnNew(true))
            res.new
        } else {
            collection.updateDocument(record.key, record, DocumentUpdateOptions().returnNew(true)).new
        }
    }
}
