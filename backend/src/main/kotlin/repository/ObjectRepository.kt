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
import models.Object

class ObjectRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.OBJECTS_COLLECTION)
    private val json = Json { ignoreUnknownKeys = true }
    
    fun create(obj: Object): Object {
        // Use the model directly instead of document.addAttribute
        val result: DocumentCreateEntity<Object> = collection.insertDocument(
            obj,
            DocumentCreateOptions().returnNew(true)
        )
        
        // Return the created document with its key
        return result.new
    }
    
    fun getById(id: String): Object? {
        return try {
            // Get the document directly as an Object object
            collection.getDocument(
                id,
                Object::class.java,
                DocumentReadOptions()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun getByWorldId(worldId: String): List<Object> {
        val query = "FOR o IN @@collection FILTER o.worldId == @worldId RETURN o"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.OBJECTS_COLLECTION,
            "worldId" to worldId
        )
        
        return ArangoConfig.db.query(query, Object::class.java, bindVars).toList()
    }
    
    fun update(id: String, obj: Object): Object? {
        val existingObject = getById(id) ?: return null
        
        // Preserve the original key and creation date
        obj.key = existingObject.key
        
        // Update the document directly using the Object object
        val result: DocumentUpdateEntity<Object> = collection.updateDocument(
            id,
            obj,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    fun delete(id: String): Boolean {
        return try {
            collection.deleteDocument(id)
            true
        } catch (e: Exception) {
            false
        }
    }
}
