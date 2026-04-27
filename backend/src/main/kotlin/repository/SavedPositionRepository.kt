package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.SavedPosition

class SavedPositionRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.SAVED_POSITIONS_COLLECTION)

    fun create(savedPosition: SavedPosition): SavedPosition {
        val result: DocumentCreateEntity<SavedPosition> = collection.insertDocument(
            savedPosition,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    fun getById(id: String): SavedPosition? {
        return try {
            collection.getDocument(
                id,
                SavedPosition::class.java,
                DocumentReadOptions()
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun getByWorldId(worldId: String): List<SavedPosition> {
        val query = "FOR p IN @@collection FILTER p.worldId == @worldId RETURN p"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.SAVED_POSITIONS_COLLECTION,
            "worldId" to worldId
        )
        return ArangoConfig.db.query(query, SavedPosition::class.java, bindVars).toList()
    }

    fun update(id: String, savedPosition: SavedPosition): SavedPosition? {
        val existing = getById(id) ?: return null
        savedPosition.key = existing.key
        val result: DocumentUpdateEntity<SavedPosition> = collection.updateDocument(
            id,
            savedPosition,
            DocumentUpdateOptions().returnNew(true)
        )
        return result.new
    }

    fun delete(id: String): Boolean {
        return try {
            collection.deleteDocument(id)
            true
        } catch (e: Throwable) {
            false
        }
    }
}
