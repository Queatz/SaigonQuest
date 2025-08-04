@file:OptIn(ExperimentalTime::class)

package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Item
import kotlin.time.ExperimentalTime

/**
 * Repository for managing items.
 */
class ItemRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.ITEMS_COLLECTION)
    
    /**
     * Creates a new item in the database
     */
    fun create(item: Item): Item {
        val result: DocumentCreateEntity<Item> = collection.insertDocument(
            item,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Gets an item by its ID (key)
     */
    fun getItemById(id: String): Item? {
        return try {
            collection.getDocument(
                id,
                Item::class.java,
                DocumentReadOptions()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets all items
     */
    fun getAllItems(): List<Item> {
        val query = "FOR i IN @@collection RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.ITEMS_COLLECTION
        )
        
        return ArangoConfig.db.query(query, Item::class.java, bindVars).toList()
    }
    
    /**
     * Gets items by their IDs
     */
    fun getItemsByIds(itemIds: List<String>): List<Item> {
        if (itemIds.isEmpty()) {
            return emptyList()
        }
        
        val query = "FOR id IN @itemIds FOR i IN @@collection FILTER i._key == id RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.ITEMS_COLLECTION,
            "itemIds" to itemIds
        )
        
        return ArangoConfig.db.query(query, Item::class.java, bindVars).toList()
    }
    
    /**
     * Updates an item in the database
     */
    fun update(id: String, item: Item): Item? {
        val existingItem = getItemById(id) ?: return null
        
        // Preserve the original key and creation date
        item.key = existingItem.key
        
        val result: DocumentUpdateEntity<Item> = collection.updateDocument(
            id,
            item,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Deletes an item from the database
     */
    fun delete(id: String): Boolean {
        return try {
            collection.deleteDocument(id)
            true
        } catch (e: Exception) {
            false
        }
    }
}
