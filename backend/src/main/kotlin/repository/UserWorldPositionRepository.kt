package repository

import com.arangodb.ArangoCollection
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.UserWorldPosition

class UserWorldPositionRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USER_WORLD_POSITIONS_COLLECTION)

    fun get(userId: String, worldId: String): UserWorldPosition? {
        val query = "FOR p IN @@collection FILTER p.userId == @userId AND p.worldId == @worldId RETURN p"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.USER_WORLD_POSITIONS_COLLECTION,
            "userId" to userId,
            "worldId" to worldId
        )
        return ArangoConfig.db.query(query, UserWorldPosition::class.java, bindVars).toList().firstOrNull()
    }

    fun update(userId: String, worldId: String, position: models.Position) {
        val existing = get(userId, worldId)
        if (existing != null) {
            existing.position = position
            collection.updateDocument(existing.key, existing)
        } else {
            val newPos = UserWorldPosition(
                userId = userId,
                worldId = worldId,
                position = position
            )
            collection.insertDocument(newPos, DocumentCreateOptions())
        }
    }
}
