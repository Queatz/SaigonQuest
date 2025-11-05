package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.DocHistory

class DocHistoryRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.DOC_HISTORY_COLLECTION)

    fun insert(history: DocHistory): DocHistory {
        val result: DocumentCreateEntity<DocHistory> = collection.insertDocument(
            history,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    fun listByDocId(docId: String, limit: Int = 200): List<DocHistory> {
        val aql = """
            FOR h IN @@collection
                FILTER h.docId == @docId
                SORT h.createdAt DESC
                LIMIT @limit
                RETURN h
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.DOC_HISTORY_COLLECTION,
            "docId" to docId,
            "limit" to limit,
        )
        return ArangoConfig.db.query(aql, DocHistory::class.java, bind).toList()
    }

    fun getById(id: String): DocHistory? {
        return try {
            collection.getDocument(id, DocHistory::class.java)
        } catch (_: Throwable) {
            null
        }
    }
}
