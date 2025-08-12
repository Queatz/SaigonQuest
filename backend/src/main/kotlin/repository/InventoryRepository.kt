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

    data class TransferResult(
        val fromInventory: Inventory,
        val toInventory: Inventory,
    )

    /**
     * Transfers a quantity of an item (by item key) from one owner to another.
     * This method validates source quantity (via remove), combines on destination add,
     * and returns the updated inventories. Currently not wrapped in a DB transaction,
     * but designed to be easily moved into one later.
     */
    fun transfer(
        fromType: models.InventoryOwnerType,
        fromId: String,
        toType: models.InventoryOwnerType,
        toId: String,
        itemKey: String,
        quantity: Int,
    ): TransferResult {
        require(quantity > 0) { "Quantity must be positive" }
        if (fromType == toType && fromId == toId) {
            val inv = getOrCreateInventory(fromType, fromId)
            return TransferResult(
                fromInventory = inv,
                toInventory = inv,
            )
        }

        // Remove from source; will throw if insufficient
        val updatedFrom = removeQuantityFromInventoryByItemKey(fromType, fromId, itemKey, quantity)
        // Add to destination; combines with existing entries by item key
        val updatedTo = addOrCombineItem(toType, toId, itemKey, quantity)

        return TransferResult(
            fromInventory = updatedFrom,
            toInventory = updatedTo,
        )
    }
    
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
        val query = "FOR i IN @@collection FILTER i.ownerType == @ownerType AND i.ownerId == @ownerId RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.INVENTORIES_COLLECTION,
            "ownerType" to models.InventoryOwnerType.USER.name,
            "ownerId" to userId
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
            ownerType = models.InventoryOwnerType.USER,
            ownerId = userId,
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

    /**
     * Find a single inventory item by inventoryId and item key.
     */
    fun getInventoryItemByInventoryIdAndItemKey(inventoryId: String, itemKey: String): InventoryItem? {
        val query = "FOR i IN @@collection FILTER i.inventoryId == @inventoryId AND i.item == @itemKey LIMIT 1 RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.INVENTORY_ITEMS_COLLECTION,
            "inventoryId" to inventoryId,
            "itemKey" to itemKey,
        )
        return ArangoConfig.db.query(query, InventoryItem::class.java, bindVars).toList().firstOrNull()
    }

    /**
     * Adds quantity of an item to a user's inventory; combines with existing entry by item key.
     * If an entry for itemKey exists, increment its quantity and keep itemOrder unchanged.
     * Otherwise, create a new InventoryItem and append its key to itemOrder.
     */
    fun addOrCombineItemToUserInventory(userId: String, itemKey: String, quantity: Int): Inventory {
        return addOrCombineItem(models.InventoryOwnerType.USER, userId, itemKey, quantity)
    }

    /**
     * Removes a quantity of a specific item (by item key) from the user's inventory.
     * This will decrement across one or more InventoryItem entries of the same item key.
     * Entries reaching 0 will be deleted and removed from itemOrder.
     * Returns the updated Inventory.
     */
    fun removeQuantityFromUserInventoryByItemKey(userId: String, itemKey: String, quantity: Int): Inventory {
        return removeQuantityFromInventoryByItemKey(models.InventoryOwnerType.USER, userId, itemKey, quantity)
    }

    /**
     * Gets an inventory by owner (user or object).
     */
    fun getByOwner(ownerType: models.InventoryOwnerType, ownerId: String): Inventory? {
        val query = "FOR i IN @@collection FILTER i.ownerType == @ownerType AND i.ownerId == @ownerId RETURN i"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.INVENTORIES_COLLECTION,
            "ownerType" to ownerType.name,
            "ownerId" to ownerId,
        )
        val inventories = ArangoConfig.db.query(query, Inventory::class.java, bindVars).toList()
        return inventories.firstOrNull()
    }

    /**
     * Creates a new inventory for an object if one doesn't exist
     */
    fun createInventoryForObject(objectId: String): Inventory {
        val existingInventory = getByOwner(models.InventoryOwnerType.OBJECT, objectId)
        if (existingInventory != null) {
            return existingInventory
        }
        val inventory = Inventory(
            ownerType = models.InventoryOwnerType.OBJECT,
            ownerId = objectId,
            itemOrder = mutableListOf(),
        )
        return create(inventory)
    }

    /**
     * Get or create an inventory for any owner.
     */
    fun getOrCreateInventory(ownerType: models.InventoryOwnerType, ownerId: String): Inventory {
        return getByOwner(ownerType, ownerId)
            ?: when (ownerType) {
                models.InventoryOwnerType.USER -> createInventoryForUser(ownerId)
                models.InventoryOwnerType.OBJECT -> createInventoryForObject(ownerId)
            }
    }

    /**
     * Adds quantity of an item to an owner's inventory; combines by item key.
     */
    fun addOrCombineItem(ownerType: models.InventoryOwnerType, ownerId: String, itemKey: String, quantity: Int): Inventory {
        require(quantity > 0) { "Quantity must be positive" }
        val inventory = getOrCreateInventory(ownerType, ownerId)
        val existing = getInventoryItemByInventoryIdAndItemKey(inventory.key, itemKey)
        return if (existing != null) {
            val updated = existing.copy(quantity = existing.quantity + quantity)
            updateInventoryItem(existing.key, updated)
            inventory
        } else {
            val createdItem = createInventoryItem(
                InventoryItem(
                    inventoryId = inventory.key,
                    quantity = quantity,
                    item = itemKey,
                )
            )
            val newOrder = inventory.itemOrder.toMutableList().apply { add(createdItem.key) }
            update(inventory.key, inventory.copy(itemOrder = newOrder)) ?: inventory
        }
    }

    /**
     * Removes quantity of an item (by item key) from an owner's inventory.
     * Deletes zero-qty entries and updates itemOrder.
     */
    fun removeQuantityFromInventoryByItemKey(ownerType: models.InventoryOwnerType, ownerId: String, itemKey: String, quantity: Int): Inventory {
        require(quantity > 0) { "Quantity must be positive" }
        val inventory = getOrCreateInventory(ownerType, ownerId)
        var remaining = quantity
        val items = getInventoryItemsByInventoryId(inventory.key).filter { it.item == itemKey }
        val order = inventory.itemOrder.toMutableList()
        for (entry in items) {
            if (remaining <= 0) break
            val take = if (entry.quantity <= remaining) entry.quantity else remaining
            val newQty = entry.quantity - take
            remaining -= take
            if (newQty <= 0) {
                deleteInventoryItem(entry.key)
                order.remove(entry.key)
            } else {
                val updated = entry.copy(quantity = newQty)
                updateInventoryItem(entry.key, updated)
            }
        }
        if (remaining > 0) {
            throw IllegalArgumentException("Not enough quantity to remove for item: $itemKey")
        }
        if (order != inventory.itemOrder) {
            update(inventory.key, inventory.copy(itemOrder = order))
        }
        return getById(inventory.key) ?: inventory
    }
}
