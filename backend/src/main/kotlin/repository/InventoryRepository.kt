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
import models.Inventory
import models.InventoryItem
import utils.json
import kotlin.time.ExperimentalTime

/**
 * Repository for managing inventories and inventory items.
 */
class InventoryRepository {
    private val inventoriesCollection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.INVENTORIES_COLLECTION)
    private val inventoryItemsCollection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.INVENTORY_ITEMS_COLLECTION)
    private val json = utils.json
    
    /**
     * Creates a new inventory in the database
     */
    fun create(inventory: Inventory): Inventory {
        val result: DocumentCreateEntity<Inventory> = inventoriesCollection.insertDocument(
            inventory,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Gets an inventory by its ID (key)
     */
    fun getById(id: String): Inventory? {
        return try {
            inventoriesCollection.getDocument(
                id,
                Inventory::class.java,
                DocumentReadOptions()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets an inventory by user ID
     */
    fun getByUserId(userId: String): Inventory? {
        val query = "FOR i IN @@collection FILTER i.userId == @userId RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.INVENTORIES_COLLECTION,
            "userId" to userId
        )
        
        val inventories = ArangoConfig.db.query(query, Inventory::class.java, bindVars).toList()
        return inventories.firstOrNull()
    }
    
    /**
     * Updates an inventory in the database
     */
    fun update(id: String, inventory: Inventory): Inventory? {
        val existingInventory = getById(id) ?: return null
        
        // Preserve the original key and creation date
        inventory.key = existingInventory.key
        
        val result: DocumentUpdateEntity<Inventory> = inventoriesCollection.updateDocument(
            id,
            inventory,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Creates a new inventory item in the database
     */
    fun createInventoryItem(inventoryItem: InventoryItem): InventoryItem {
        val result: DocumentCreateEntity<InventoryItem> = inventoryItemsCollection.insertDocument(
            inventoryItem,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Gets an inventory item by its ID (key)
     */
    fun getInventoryItemById(id: String): InventoryItem? {
        return try {
            inventoryItemsCollection.getDocument(
                id,
                InventoryItem::class.java,
                DocumentReadOptions()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets inventory items by inventory ID
     */
    fun getInventoryItemsByInventoryId(inventoryId: String): List<InventoryItem> {
        val query = "FOR i IN @@collection FILTER i.inventoryId == @inventoryId RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.INVENTORY_ITEMS_COLLECTION,
            "inventoryId" to inventoryId
        )
        
        return ArangoConfig.db.query(query, InventoryItem::class.java, bindVars).toList()
    }
    
    /**
     * Updates an inventory item in the database
     */
    fun updateInventoryItem(id: String, inventoryItem: InventoryItem): InventoryItem? {
        val existingItem = getInventoryItemById(id) ?: return null
        
        // Preserve the original key and creation date
        inventoryItem.key = existingItem.key
        
        val result: DocumentUpdateEntity<InventoryItem> = inventoryItemsCollection.updateDocument(
            id,
            inventoryItem,
            DocumentUpdateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Deletes an inventory item from the database
     */
    fun deleteInventoryItem(id: String): Boolean {
        return try {
            inventoryItemsCollection.deleteDocument(id)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Creates a new inventory for a user if one doesn't exist
     */
    fun createInventoryForUser(userId: String): Inventory {
        // Check if user already has an inventory
        val existingInventory = getByUserId(userId)
        if (existingInventory != null) {
            return existingInventory
        }
        
        // Create a new inventory for the user
        val inventory = Inventory(
            userId = userId,
            itemOrder = mutableListOf()
        )
        
        return create(inventory)
    }
    
    /**
     * Adds an item to a user's inventory
     */
    fun addItemToInventory(userId: String, inventoryItem: InventoryItem): Inventory {
        // Get or create the user's inventory
        val inventory = getByUserId(userId) ?: createInventoryForUser(userId)

        inventoryItem.inventoryId = inventory.key

        // Create the inventory item
        val createdItem = createInventoryItem(inventoryItem)
        
        // Update the inventory's item order
        val updatedItemOrder = inventory.itemOrder.toMutableList()
        updatedItemOrder.add(createdItem.key)
        
        // Update the inventory
        val updatedInventory = inventory.copy(itemOrder = updatedItemOrder)
        return update(inventory.key, updatedInventory) ?: inventory
    }
    
    /**
     * Removes an item from a user's inventory
     */
    fun removeItemFromInventory(userId: String, itemId: String): Inventory {
        // Get the user's inventory
        val inventory = getByUserId(userId) ?: return createInventoryForUser(userId)
        
        // Update the inventory's item order
        val updatedItemOrder = inventory.itemOrder.toMutableList()
        updatedItemOrder.remove(itemId)
        
        // Delete the inventory item
        deleteInventoryItem(itemId)
        
        // Update the inventory
        val updatedInventory = inventory.copy(itemOrder = updatedItemOrder)
        return update(inventory.key, updatedInventory) ?: inventory
    }
    
    /**
     * Updates an item in a user's inventory
     */
    fun updateItemInInventory(userId: String, itemId: String, inventoryItem: InventoryItem): Inventory {
        // Get the user's inventory
        val inventory = getByUserId(userId) ?: return createInventoryForUser(userId)
        
        // Update the inventory item
        updateInventoryItem(itemId, inventoryItem)
        
        return inventory
    }
    
    /**
     * Gets the total quantity of a specific item in a user's inventory
     * 
     * This method sums up quantities across all inventory items of the same type.
     * 
     * @param userId The ID of the user
     * @param itemId The ID of the item to check
     * @return The total quantity of the item in the user's inventory, or 0 if the user has none
     */
    fun getTotalItemQuantity(userId: String, itemId: String): Int {
        // Get the user's inventory
        val inventory = getByUserId(userId) ?: return 0
        
        // Get all inventory items for this inventory
        val inventoryItems = getInventoryItemsByInventoryId(inventory.key)
        
        // Filter items that match the specified itemId and sum up their quantities
        return inventoryItems
            .filter { it.item == itemId }
            .sumOf { it.quantity }
    }
    
    /**
     * Updates the item order in a user's inventory
     * 
     * @param userId The ID of the user
     * @param itemOrder The new order of items
     * @return The updated inventory
     */
    fun updateInventoryItemOrder(userId: String, itemOrder: List<String>): Inventory {
        // Get or create the user's inventory
        val inventory = getByUserId(userId) ?: createInventoryForUser(userId)
        
        // Update the inventory with the new item order
        val updatedInventory = inventory.copy(itemOrder = itemOrder)
        
        // Save and return the updated inventory
        return update(inventory.key, updatedInventory) ?: inventory
    }
}
