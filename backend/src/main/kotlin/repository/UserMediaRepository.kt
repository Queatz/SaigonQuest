package repository

import com.arangodb.ArangoCursor
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.MediaType
import models.UserMedia

class UserMediaRepository {
    private val db = ArangoConfig.db
    private val collectionName = CollectionsConfig.USER_MEDIA_COLLECTION

    fun insert(media: UserMedia): UserMedia {
        val result: DocumentCreateEntity<UserMedia> = db.collection(collectionName)
            .insertDocument(media, DocumentCreateOptions().returnNew(true))
        return result.new
    }

    fun listForUser(userId: String, type: MediaType? = null, limit: Int = 100, offset: Int = 0): List<UserMedia> {
        val typeFilter = if (type != null) "FILTER m.type == @type" else ""
        val query = """
            FOR m IN $collectionName
            FILTER m.userId == @userId
            $typeFilter
            SORT m.favorite DESC, m.createdAt DESC
            LIMIT @offset, @limit
            RETURN m
        """.trimIndent()
        val bindVars = mutableMapOf<String, Any>(
            "userId" to userId,
            "limit" to limit,
            "offset" to offset
        )
        if (type != null) {
            bindVars["type"] = type.name
        }
        val cursor: ArangoCursor<UserMedia> = db.query(query, UserMedia::class.java, bindVars)
        return cursor.asListRemaining()
    }

    fun deleteForUser(userId: String, key: String): Boolean {
        val query = """
            FOR m IN $collectionName
            FILTER m.userId == @userId AND m._key == @key
            REMOVE m IN $collectionName
            RETURN 1
        """.trimIndent()
        val bindVars = mapOf(
            "userId" to userId,
            "key" to key,
        )
        val cursor: ArangoCursor<Int> = db.query(query, Int::class.java, bindVars)
        return cursor.hasNext()
    }

    fun updateFavoriteForUser(userId: String, key: String, favorite: Boolean): UserMedia? {
        val query = """
            FOR m IN $collectionName
            FILTER m.userId == @userId AND m._key == @key
            UPDATE m WITH { favorite: @favorite } IN $collectionName
            RETURN NEW
        """.trimIndent()
        val bindVars = mapOf(
            "userId" to userId,
            "key" to key,
            "favorite" to favorite,
        )
        val cursor: ArangoCursor<UserMedia> = db.query(query, UserMedia::class.java, bindVars)
        return if (cursor.hasNext()) cursor.next() else null
    }

    fun updateNameForUser(userId: String, key: String, newName: String): UserMedia? {
        val query = """
            FOR m IN $collectionName
            FILTER m.userId == @userId AND m._key == @key
            UPDATE m WITH { originalFileName: @newName } IN $collectionName
            RETURN NEW
        """.trimIndent()
        val bindVars = mapOf(
            "userId" to userId,
            "key" to key,
            "newName" to newName,
        )
        val cursor: ArangoCursor<UserMedia> = db.query(query, UserMedia::class.java, bindVars)
        return if (cursor.hasNext()) cursor.next() else null
    }
}

