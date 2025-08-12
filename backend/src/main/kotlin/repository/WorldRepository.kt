package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import kotlinx.serialization.json.Json
import models.World

class WorldRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.WORLDS_COLLECTION)

    fun create(world: World): World {
        // Use the model directly instead of document.addAttribute
        val result: DocumentCreateEntity<World> = collection.insertDocument(
            world,
            DocumentCreateOptions().returnNew(true)
        )
        
        // Return the created document with its key
        return result.new
    }
    
    fun getById(id: String): World? {
        return try {
            // Get the document directly as a World object
            collection.getDocument(
                id,
                World::class.java,
                DocumentReadOptions()
            )
        } catch (e: Throwable) {
            null
        }
    }
    
    fun getByUrl(url: String): World? {
        val query = "FOR w IN @@collection FILTER w.url == @url RETURN w"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.WORLDS_COLLECTION,
            "url" to url
        )
        
        val results = ArangoConfig.db.query(query, World::class.java, bindVars).toList()
        return results.firstOrNull()
    }
    
    fun getByUserId(userId: String): List<World> {
        val query = "FOR w IN @@collection FILTER w.userId == @userId RETURN w"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.WORLDS_COLLECTION,
            "userId" to userId
        )
        
        return ArangoConfig.db.query(query, World::class.java, bindVars).toList()
    }
    
    fun update(id: String, world: World): World? {
        val existingWorld = getById(id) ?: return null
        
        // Preserve the original key and creation date
        world.key = existingWorld.key
        
        // Update the document directly using the World object
        val result: DocumentUpdateEntity<World> = collection.updateDocument(
            id,
            world,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
}
