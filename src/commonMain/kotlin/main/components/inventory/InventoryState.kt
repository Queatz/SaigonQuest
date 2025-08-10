package main.components.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.ItemApi
import main.api.UserApi
import models.Inventory
import models.Item
import models.User

/**
 * A singleton object that manages the shared inventory state across components.
 * This allows changes made in one component to be reflected in others without requiring a manual refresh.
 */
object InventoryState {
    // State for inventory items and user
    var inventory by mutableStateOf<Inventory?>(null)
    var items by mutableStateOf<Map<String, Item>>(emptyMap())
    var currentUser by mutableStateOf<User?>(null)
    var isLoading by mutableStateOf(false)
    var lastError by mutableStateOf<String?>(null)
    
    // Mapping from InventoryItem keys to Item keys
    var inventoryItemToItemMap by mutableStateOf<Map<String, String>>(emptyMap())
    
    // Mapping from InventoryItem keys to quantities
    var inventoryItemQuantities by mutableStateOf<Map<String, Int>>(emptyMap())
    
    // Callback for when inventory is refreshed
    private var onRefreshCallbacks = mutableListOf<() -> Unit>()
    
    /**
     * Registers a callback to be called when the inventory is refreshed.
     * 
     * @param callback The callback to register
     */
    fun addOnRefreshCallback(callback: () -> Unit) {
        onRefreshCallbacks.add(callback)
    }
    
    /**
     * Removes a previously registered callback.
     * 
     * @param callback The callback to remove
     */
    fun removeOnRefreshCallback(callback: () -> Unit) {
        onRefreshCallbacks.remove(callback)
    }
    
    /**
     * Loads the inventory data from the server.
     * 
     * @param coroutineScope The coroutine scope to use for async operations
     * @param onSuccess Callback for when the inventory is successfully loaded
     * @param onError Callback for when there's an error loading the inventory
     */
    fun loadInventory(
        coroutineScope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isLoading) return
        
        isLoading = true
        lastError = null
        
        coroutineScope.launch {
            try {
                // Get current user if not already loaded
                if (currentUser == null) {
                    currentUser = UserApi.getCurrentUser()
                }
                
                // Get user inventory
                val userInventory = InventoryApi.getUserInventory()
                inventory = userInventory
                
                // Get items by their IDs
                if (userInventory.itemOrder.isNotEmpty()) {
                    // First, get the InventoryItem objects
                    val inventoryItems = InventoryApi.getInventoryItemsByIds(userInventory.itemOrder)
                    
                    // Create mapping from InventoryItem keys to Item keys
                    val inventoryItemToItemMapping = inventoryItems.associate { it.key to it.item }
                    inventoryItemToItemMap = inventoryItemToItemMapping
                    
                    // Create mapping from InventoryItem keys to quantities
                    val quantityMapping = inventoryItems.associate { it.key to it.quantity }
                    inventoryItemQuantities = quantityMapping
                    
                    // Extract the actual Item keys from the InventoryItem objects
                    val itemKeys = inventoryItems.map { it.item }
                    
                    // Now fetch the actual Item objects using these keys
                    if (itemKeys.isNotEmpty()) {
                        val itemsList = ItemApi.getItemsByIds(itemKeys)
                        items = itemsList.associateBy { it.key }
                    } else {
                        items = emptyMap()
                    }
                } else {
                    items = emptyMap()
                    inventoryItemToItemMap = emptyMap()
                    inventoryItemQuantities = emptyMap()
                }
                
                isLoading = false
                onSuccess()
                
                // Notify all registered callbacks
                onRefreshCallbacks.forEach { it() }
            } catch (error: Throwable) {
                console.error("Error loading inventory", error)
                lastError = error.message ?: "Failed to load inventory"
                isLoading = false
                onError(lastError ?: "Unknown error")
            }
        }
    }
    
    /**
     * Refreshes the inventory data from the server.
     * 
     * @param coroutineScope The coroutine scope to use for async operations
     * @param onSuccess Callback for when the inventory is successfully refreshed
     * @param onError Callback for when there's an error refreshing the inventory
     */
    fun refreshInventory(
        coroutineScope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isLoading) return
        
        isLoading = true
        lastError = null
        
        coroutineScope.launch {
            try {
                // Refresh user data to get updated worn items
                currentUser = UserApi.fetchCurrentUser()
                
                // Get user inventory
                val userInventory = InventoryApi.getUserInventory()
                inventory = userInventory
                
                // Get items by their IDs
                if (userInventory.itemOrder.isNotEmpty()) {
                    // First, get the InventoryItem objects
                    val inventoryItems = InventoryApi.getInventoryItemsByIds(userInventory.itemOrder)
                    
                    // Create mapping from InventoryItem keys to Item keys
                    val inventoryItemToItemMapping = inventoryItems.associate { it.key to it.item }
                    inventoryItemToItemMap = inventoryItemToItemMapping
                    
                    // Create mapping from InventoryItem keys to quantities
                    val quantityMapping = inventoryItems.associate { it.key to it.quantity }
                    inventoryItemQuantities = quantityMapping
                    
                    // Extract the actual Item keys from the InventoryItem objects
                    val itemKeys = inventoryItems.map { it.item }
                    
                    // Now fetch the actual Item objects using these keys
                    if (itemKeys.isNotEmpty()) {
                        val itemsList = ItemApi.getItemsByIds(itemKeys)
                        items = itemsList.associateBy { it.key }
                    } else {
                        items = emptyMap()
                    }
                } else {
                    items = emptyMap()
                    inventoryItemToItemMap = emptyMap()
                    inventoryItemQuantities = emptyMap()
                }
                
                isLoading = false
                onSuccess()
                
                // Notify all registered callbacks
                onRefreshCallbacks.forEach { it() }
            } catch (error: Throwable) {
                console.error("Error refreshing inventory", error)
                lastError = error.message ?: "Failed to refresh inventory"
                isLoading = false
                onError(lastError ?: "Unknown error")
            }
        }
    }
    
    /**
     * Gets the first N items from the inventory.
     * 
     * @param count The number of items to get
     * @return A list of the first N items, or an empty list if the inventory is not loaded
     */
    fun getFirstNItems(count: Int): List<Item> {
        val inv = inventory ?: return emptyList()
        val itemIds = inv.itemOrder.take(count)
        
        return itemIds.mapNotNull { getItemForInventoryItemKey(it) }
    }
    
    /**
     * Gets the Item for a given InventoryItem key.
     * 
     * @param inventoryItemKey The key of the InventoryItem
     * @return The Item object, or null if not found
     */
    fun getItemForInventoryItemKey(inventoryItemKey: String): Item? {
        // Get the Item key from the mapping
        val itemKey = inventoryItemToItemMap[inventoryItemKey] ?: return null
        
        // Get the Item object from the items map
        return items[itemKey]
    }
    
    /**
     * Gets all inventory items with their corresponding Item objects.
     * 
     * @return A list of pairs of InventoryItem key and Item object
     */
    fun getInventoryItems(): List<Pair<String, Item>> {
        val inv = inventory ?: return emptyList()
        val result = mutableListOf<Pair<String, Item>>()
        
        for (inventoryItemKey in inv.itemOrder) {
            val item = getItemForInventoryItemKey(inventoryItemKey)
            if (item != null) {
                result.add(inventoryItemKey to item)
            }
        }
        
        return result
    }
    
    /**
     * Gets the quantity for a given inventory item key.
     * 
     * @param inventoryItemKey The key of the inventory item
     * @return The quantity of the item, or 1 if not found
     */
    fun getQuantityForInventoryItemKey(inventoryItemKey: String): Int {
        return inventoryItemQuantities[inventoryItemKey] ?: 1
    }
}
