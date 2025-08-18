package repository

import com.arangodb.ArangoCursor
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.UserMedia

class UserMediaRepository {
    private val db = ArangoConfig.db
    private val collectionName = CollectionsConfig.USER_MEDIA_COLLECTION

    fun insert(media: UserMedia): UserMedia {
        val result: DocumentCreateEntity<UserMedia> = db.collection(collectionName)
            .insertDocument(media, DocumentCreateOptions().returnNew(true))
        return result.new
    }

    fun listForUser(userId: String, limit: Int = 100, offset: Int = 0): List<UserMedia> {
        val query = """
            FOR m IN $collectionName
            FILTER m.userId == @userId
            SORT m.createdAt DESC
            LIMIT @offset, @limit
            RETURN m
        """.trimIndent()
        val bindVars = mapOf(
            "userId" to userId,
            "limit" to limit,
            "offset" to offset
        )
        val cursor: ArangoCursor<UserMedia> = db.query(query, UserMedia::class.java, bindVars)
        return cursor.asListRemaining()
    }
}

