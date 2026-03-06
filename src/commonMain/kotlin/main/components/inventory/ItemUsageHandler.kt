package main.components.inventory

import Translations.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.updatePlayer
import main.components.translated
import models.Item
import models.ItemWearableLocation
import models.Sex
import models.User

/**
 * Enum representing different item types.
 * This can be extended in the future to support more item types.
 */
enum class ItemType {
    WEARABLE,
    STANDARD,
    // Future types can be added here: CONSUMABLE, QUEST_ITEM, etc.
}

/**
 * Extension property to determine the type of an item based on its properties.
 */
val Item.itemType: ItemType
    get() = when {
        wearable -> ItemType.WEARABLE
        else -> ItemType.STANDARD
    }

/**
 * A handler for item usage that centralizes the logic for using different types of items.
 * This provides a consistent way to handle item usage across different components.
 */
object ItemUsageHandler {
    
    /**
     * Uses an item based on its type.
     * 
     * @param item The item to use
     * @param currentUser The current user
     * @param coroutineScope The coroutine scope to launch async operations
     * @param onSuccess Callback for when the item is successfully used
     * @param onError Callback for when there's an error using the item
     */
    fun useItem(
        item: Item,
        currentUser: User?,
        coroutineScope: CoroutineScope,
        onSuccess: (feedbackMessage: String) -> Unit = {},
        onError: (feedbackMessage: String) -> Unit = {}
    ) {
        when (item.itemType) {
            ItemType.WEARABLE -> {
                // Handle wearable items
                if (currentUser == null) {
                    onError(str("You must be logged in to wear items"))
                    return
                }

                // Check if the item matches the user's sex
                val itemSex = item.wearableSex ?: Sex.Boy
                val userSex = currentUser.sex ?: Sex.Boy
                if (itemSex != userSex) {
                    onError(str("It doesn't fit."))
                    return
                }
                
                // Check if the item is already worn
                val isWorn = currentUser.wornItems.contains(item.key)
                
                if (isWorn) {
                    // Remove the item if it's already worn
                    coroutineScope.launch {
                        try {
                            // Remove from worn items
                            val updatedWornItems = currentUser.wornItems.toMutableList()
                            updatedWornItems.remove(item.key)
                            
                            // Update the user locally and on the server
                            currentUser.wornItems = updatedWornItems
                            val updatedUser = UserApi.updateUserWornItems(currentUser)
                            
                            // Broadcast the change to other players
                            WebSocketApi.updatePlayer(
                                name = updatedUser.username,
                                isPlayer = updatedUser.isPlayer,
                                status = updatedUser.status
                            )
                            
                            // Refresh inventory state to update CharacterRenderer
                            InventoryState.refreshInventory(
                                coroutineScope = coroutineScope,
                                onSuccess = {
                                    console.log("[DEBUG_LOG] Successfully refreshed inventory after removing worn item")
                                },
                                onError = { error ->
                                    console.error("[DEBUG_LOG] Error refreshing inventory after removing worn item: $error")
                                }
                            )
                            
                            onSuccess("${str("Removed")} ${item.title}")
                        } catch (error: Throwable) {
                            console.error("Error removing worn item", error)
                            onError("${str("Couldn't remove")} ${item.title}: ${error.message}")
                        }
                    }
                } else {
                    // Wear the item if it's not already worn
                    coroutineScope.launch {
                        try {
                            // Add to worn items
                            val updatedWornItems = currentUser.wornItems.toMutableList()
                            
                            // Remove all other worn items at the same location
                            val itemLocation = item.wearableLocation ?: ItemWearableLocation.Body
                            
                            // Identify which items to remove based on their locations
                            val itemsToRemove = updatedWornItems.filter { wornItemKey ->
                                val wornItem = InventoryState.items[wornItemKey]
                                if (wornItem != null) {
                                    val wornLocation = wornItem.wearableLocation ?: ItemWearableLocation.Body
                                    wornLocation == itemLocation
                                } else {
                                    false
                                }
                            }
                            
                            updatedWornItems.removeAll(itemsToRemove)
                            updatedWornItems.add(item.key)

                            // Update the user locally and on the server
                            currentUser.wornItems = updatedWornItems
                            val updatedUser = UserApi.updateUserWornItems(currentUser)

                            // Broadcast the change to other players
                            WebSocketApi.updatePlayer(
                                name = updatedUser.username,
                                isPlayer = updatedUser.isPlayer,
                                status = updatedUser.status
                            )

                            // Refresh inventory state to update CharacterRenderer
                            InventoryState.refreshInventory(
                                coroutineScope = coroutineScope,
                                onSuccess = {
                                    console.log("[DEBUG_LOG] Successfully refreshed inventory after wearing item")
                                },
                                onError = { error ->
                                    console.error("[DEBUG_LOG] Error refreshing inventory after wearing item: $error")
                                }
                            )
                            
                            onSuccess("${str("Wearing")} ${item.title}")
                        } catch (error: Throwable) {
                            console.error("Error wearing item", error)
                            onError("${str("Couldn't wear")} ${item.title}: ${error.message}")
                        }
                    }
                }
            }
            
            ItemType.STANDARD -> {
                // For now, just log that the item was used
                // This can be extended in the future to handle different types of standard items
                console.log("Used standard item: ${item.title}")
                onSuccess("${str("Used")} ${item.title}")
            }
            
            // Add cases for future item types here
        }
    }
    
    /**
     * Checks if an item is currently worn by the user.
     * 
     * @param item The item to check
     * @param currentUser The current user
     * @return True if the item is worn, false otherwise
     */
    fun isItemWorn(item: Item, currentUser: User?): Boolean {
        if (currentUser == null || !item.wearable) {
            return false
        }
        
        return currentUser.wornItems.contains(item.key)
    }
}
