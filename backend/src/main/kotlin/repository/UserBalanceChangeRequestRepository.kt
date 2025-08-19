package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.UserBalanceChangeRequest
import models.UserBalanceChangeStatus

class UserBalanceChangeRequestRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USER_BALANCE_CHANGE_REQUESTS_COLLECTION)

    fun create(req: UserBalanceChangeRequest): UserBalanceChangeRequest {
        val result: DocumentCreateEntity<UserBalanceChangeRequest> = collection.insertDocument(
            req,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    fun updateStatus(id: String, status: UserBalanceChangeStatus, notes: String? = null): UserBalanceChangeRequest? {
        val existing = getById(id) ?: return null
        existing.status = status
        existing.resolved = status != UserBalanceChangeStatus.Received
        if (notes != null) existing.notes = notes
        return collection.updateDocument(existing.key, existing, DocumentUpdateOptions().returnNew(true)).new
    }

    fun getById(id: String): UserBalanceChangeRequest? {
        return try { collection.getDocument(id, UserBalanceChangeRequest::class.java) } catch (_: Throwable) { null }
    }

    fun listForUser(userId: String, statuses: Set<UserBalanceChangeStatus>? = null): List<UserBalanceChangeRequest> {
        val query = buildString {
            append("FOR r IN ${CollectionsConfig.USER_BALANCE_CHANGE_REQUESTS_COLLECTION} FILTER r.userId == @userId ")
            if (!statuses.isNullOrEmpty()) append("AND r.status IN @statuses ")
            append("SORT r.createdAt DESC RETURN r")
        }
        val bind = mutableMapOf<String, Any>("userId" to userId)
        statuses?.let { bind["statuses"] = it.toList() }
        return ArangoConfig.db.query(query, UserBalanceChangeRequest::class.java, bind).toList()
    }

    fun listPending(limit: Int = 100): List<UserBalanceChangeRequest> {
        val query = """
            FOR r IN ${CollectionsConfig.USER_BALANCE_CHANGE_REQUESTS_COLLECTION}
            FILTER r.status == @status
            SORT r.createdAt ASC
            LIMIT @limit
            RETURN r
        """.trimIndent()
        val bind = mapOf("status" to UserBalanceChangeStatus.Received, "limit" to limit)
        return ArangoConfig.db.query(query, UserBalanceChangeRequest::class.java, bind).toList()
    }

    fun listAdmin(statuses: Set<UserBalanceChangeStatus>?, resolved: Boolean?, limit: Int = 500): List<UserBalanceChangeRequest> {
        val query = buildString {
            append("FOR r IN ${CollectionsConfig.USER_BALANCE_CHANGE_REQUESTS_COLLECTION} ")
            val filters = mutableListOf<String>()
            if (!statuses.isNullOrEmpty()) filters.add("r.status IN @statuses")
            if (resolved != null) filters.add("r.resolved == @resolved")
            if (filters.isNotEmpty()) append("FILTER ${filters.joinToString(" AND ")} ")
            append("SORT r.createdAt DESC ")
            append("LIMIT @limit ")
            append("RETURN r")
        }
        val bind = mutableMapOf<String, Any>("limit" to limit)
        statuses?.let { bind["statuses"] = it.toList() }
        resolved?.let { bind["resolved"] = it }
        return ArangoConfig.db.query(query, UserBalanceChangeRequest::class.java, bind).toList()
    }
}
