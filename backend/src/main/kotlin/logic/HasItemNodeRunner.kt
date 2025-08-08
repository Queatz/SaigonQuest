package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.InventoryRepository
import utils.json

/**
 * Runner for HasItem nodes
 *
 * This node runner checks if the player has a specific item in their inventory.
 * It sets an outputProperty in the eventData based on the result:
 * - PropertyKey.YES if the player has the item
 * - PropertyKey.NO if the player does not have the item
 * 
 * The NodeLogicRunner uses this outputProperty to select the correct connection to follow.
 */
class HasItemNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val inventoryRepository = InventoryRepository()
        
        WebSocketDebug.logMessage("HAS_ITEM_NODE_EXECUTE", "Executing HasItem node with data: ${node.data}", context.session)
        
        try {
            // Get the JSON data string from the node data
            val jsonData = node.data["data"] ?: "{}"
            WebSocketDebug.logMessage("HAS_ITEM_NODE_JSON_DATA", "Node JSON data: $jsonData", context.session)
            
            // Parse the JSON data to extract the item ID and quantity
            // Try to parse as PropertyBasedNodeData
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)
            
            // Extract item ID (required)
            val itemId = nodeData.properties.find { it.key == PropertyKey.ITEM_ID }?.value ?: run {
                WebSocketDebug.logError("Missing itemId in HasItem node", null, context.session)
                return false
            }
            
            // Extract quantity (default to 1 if not specified)
            val quantity = nodeData.properties.find { it.key == PropertyKey.QUANTITY }?.value?.toIntOrNull() ?: 1
            
            WebSocketDebug.logMessage("HAS_ITEM_CHECK", "Checking if user ${context.connection.userId} has $quantity of item $itemId", context.session)
            
            // Check if the player has the item
            val hasItem = run {
                // Get the player's inventory
                val inventory = inventoryRepository.getByUserId(context.connection.userId)
                
                if (inventory == null) {
                    WebSocketDebug.logMessage("HAS_ITEM_NO_INVENTORY", "User has no inventory, returning false", context.session)
                    false
                } else {
                    // Get the total quantity of the item in the user's inventory
                    val totalQuantity = inventoryRepository.getTotalItemQuantity(
                        userId = context.connection.userId,
                        itemId = itemId
                    )
                    
                    // Check if the total quantity is enough
                    val hasEnough = totalQuantity >= quantity
                    
                    WebSocketDebug.logMessage("HAS_ITEM_RESULT", "User has total of $totalQuantity of item $itemId (needed: $quantity): $hasEnough", context.session)
                    hasEnough
                }
            }
            
            // Set the outputProperty based on whether the player has the item
            val outputProperty = if (hasItem) PropertyKey.YES else PropertyKey.NO
            WebSocketDebug.logMessage("HAS_ITEM_OUTPUT_PROPERTY", "Setting output property to $outputProperty (${if (hasItem) "Yes" else "No"})", context.session)
            
            // Set the outputProperty in the eventData
            // This is used by NodeLogicRunner to determine which connection to follow
            eventData["outputProperty"] = outputProperty
            
            WebSocketDebug.logMessage("HAS_ITEM_NODE_SUCCESS", "HasItem node execution completed successfully", context.session)
            return true
        } catch (e: Exception) {
            WebSocketDebug.logError("Error executing HasItem node", e, context.session)
            return false
        }
    }
}
