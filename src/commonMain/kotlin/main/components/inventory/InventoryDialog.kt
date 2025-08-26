package main.components.inventory

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.WebSocketApi
import main.api.addUpdatePlayerListener
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.attrs.onTouchDrop
import main.components.player.CharacterRenderer
import main.components.translated
import main.utils.DataType
import models.Inventory
import models.Item
import models.User
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import utils.json

/**
 * An inventory dialog that displays the user's inventory items.
 */
@Composable
fun InventoryDialog(onClose: () -> Unit) {
    // State for inventory and items
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var items by remember { mutableStateOf<Map<String, Item>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var showCreateItemDialog by remember { mutableStateOf(false) }
    var showMarketplaceDialog by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    
    // State for feedback messages
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Function to show feedback message with timeout
    fun showFeedback(message: String, error: Boolean = false) {
        feedbackMessage = message
        isErrorFeedback = error
        
        coroutineScope.launch {
            delay(3000) // Show message for 3 seconds
            feedbackMessage = null
        }
    }
    
    // Load inventory, items, and user when the dialog is opened
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        // Use the shared inventory state to load data
        InventoryState.loadInventory(
            coroutineScope = coroutineScope,
            onSuccess = {
                // Sync local state with shared state
                inventory = InventoryState.inventory
                items = InventoryState.items
                currentUser = InventoryState.currentUser
                isLoading = false
                
                // Add WebSocket listener for player updates
                if (currentUser != null) {
                    WebSocketApi.addUpdatePlayerListener { updateData ->
                        // Check if this is an update for another player
                        if (updateData.userId != currentUser?.key) {
                            // If we have the updated player's data in our current user, update it
                            if (currentUser?.key == updateData.userId) {
                                // Refresh the user data to get the updated worn items
                                coroutineScope.launch {
                                    try {
                                        InventoryState.refreshInventory(coroutineScope)
                                        currentUser = InventoryState.currentUser
                                    } catch (error: Throwable) {
                                        console.error("Error refreshing user data", error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onError = { message ->
                console.error("Error loading inventory", message)
                errorMessage = message
                isLoading = false
                showFeedback("Failed to load inventory", true)
            }
        )
    }
    
    // Keep local state in sync with shared state
    LaunchedEffect(InventoryState.inventory, InventoryState.items, InventoryState.currentUser, InventoryState.isLoading) {
        inventory = InventoryState.inventory
        items = InventoryState.items
        currentUser = InventoryState.currentUser
        isLoading = InventoryState.isLoading
        if (InventoryState.lastError != null) {
            errorMessage = InventoryState.lastError ?: ""
        }
    }
    
    // Note: WebSocket listeners will be cleaned up when the application is closed
    // or when the user navigates away from the page
    
    // Function to handle using an item
    fun handleUseItem(inventoryItemId: String) {
        console.log("[DEBUG_LOG] handleUseItem: Attempting to use item with inventory ID: $inventoryItemId")
        
        // Get the actual Item using the InventoryState helper method
        val item = InventoryState.getItemForInventoryItemKey(inventoryItemId)
        if (item == null) {
            console.error("[DEBUG_LOG] handleUseItem: Item not found for inventory item ID: $inventoryItemId")
            console.log("[DEBUG_LOG] handleUseItem: Available inventory item mappings: ${InventoryState.inventoryItemToItemMap}")
            console.log("[DEBUG_LOG] handleUseItem: Available items: ${items.keys.joinToString()}")
            showFeedback("Error: Item not found", true)
            return
        }
        
        console.log("[DEBUG_LOG] handleUseItem: Item found: ${item.title}")
        
        // Use the ItemUsageHandler to handle the item usage
        ItemUsageHandler.useItem(
            item = item,
            currentUser = currentUser,
            coroutineScope = coroutineScope,
            onSuccess = { message -> 
                console.log("[DEBUG_LOG] handleUseItem: Successfully used item: ${item.title}")
                showFeedback(message) 
            },
            onError = { message -> 
                console.error("[DEBUG_LOG] handleUseItem: Error using item: ${item.title}, error: $message")
                showFeedback(message, true) 
            }
        )
    }
    
    // Function to handle wearing an item
    fun handleWearItem(inventoryItemId: String) {
        console.log("[DEBUG_LOG] handleWearItem: Attempting to wear item with inventory ID: $inventoryItemId")
        
        // Get the actual Item using the InventoryState helper method
        val item = InventoryState.getItemForInventoryItemKey(inventoryItemId)
        if (item == null) {
            console.error("[DEBUG_LOG] handleWearItem: Item not found for inventory item ID: $inventoryItemId")
            console.log("[DEBUG_LOG] handleWearItem: Available inventory item mappings: ${InventoryState.inventoryItemToItemMap}")
            console.log("[DEBUG_LOG] handleWearItem: Available items: ${items.keys.joinToString()}")
            showFeedback("Error: Item not found", true)
            return
        }
        
        console.log("[DEBUG_LOG] handleWearItem: Item found: ${item.title}, wearable=${item.wearable}")
        
        // Check if the item is wearable
        if (!item.wearable) {
            console.log("[DEBUG_LOG] handleWearItem: Item is not wearable: ${item.title}")
            showFeedback("Item is not wearable: ${item.title}", true)
            return
        }
        
        // Check if user is available
        if (currentUser == null) {
            console.error("[DEBUG_LOG] handleWearItem: Current user is null")
            showFeedback("Error: User data not available", true)
            return
        }
        
        console.log("[DEBUG_LOG] handleWearItem: Using ItemUsageHandler to wear item: ${item.title}")
        
        // Use the ItemUsageHandler to handle wearing the item
        ItemUsageHandler.useItem(
            item = item,
            currentUser = currentUser,
            coroutineScope = coroutineScope,
            onSuccess = { message -> 
                console.log("[DEBUG_LOG] handleWearItem: Successfully wore item: ${item.title}")
                showFeedback(message) 
            },
            onError = { message -> 
                console.error("[DEBUG_LOG] handleWearItem: Error wearing item: ${item.title}, error: $message")
                showFeedback(message, true) 
            }
        )
    }
    
    // Function to handle removing a worn item
    // This can be called with either an inventoryItemId or a direct itemId (when removing from worn items section)
    fun handleRemoveWornItem(id: String, isDirectItemId: Boolean = false) {
        console.log("[DEBUG_LOG] handleRemoveWornItem: Attempting to remove worn item with ID: $id, isDirectItemId=$isDirectItemId")
        
        // Get the item - either directly or via the inventory item mapping
        val item = if (isDirectItemId) {
            // If this is a direct item ID (from worn items), look it up directly in the items map
            items[id].also {
                console.log("[DEBUG_LOG] handleRemoveWornItem: Looking up direct item ID: $id")
            }
        } else {
            // Otherwise, use the inventory item mapping
            InventoryState.getItemForInventoryItemKey(id).also {
                console.log("[DEBUG_LOG] handleRemoveWornItem: Looking up inventory item ID: $id")
            }
        }
        
        if (item == null) {
            console.error("[DEBUG_LOG] handleRemoveWornItem: Item not found for ID: $id")
            console.log("[DEBUG_LOG] handleRemoveWornItem: Available inventory item mappings: ${InventoryState.inventoryItemToItemMap}")
            console.log("[DEBUG_LOG] handleRemoveWornItem: Available items: ${items.keys.joinToString()}")
            showFeedback("Error: Item not found", true)
            return
        }
        
        console.log("[DEBUG_LOG] handleRemoveWornItem: Item found: ${item.title}")
        
        // Check if user is available
        if (currentUser == null) {
            console.error("[DEBUG_LOG] handleRemoveWornItem: Current user is null")
            showFeedback("Error: User data not available", true)
            return
        }
        
        // Get the item key to check if it's worn
        val itemKey = if (isDirectItemId) id else InventoryState.inventoryItemToItemMap[id]
        
        // Check if the item is actually worn
        if (itemKey == null || currentUser?.wornItems?.contains(itemKey) != true) {
            console.log("[DEBUG_LOG] handleRemoveWornItem: Item is not currently worn: ${item.title}")
            showFeedback("Item is not currently worn: ${item.title}", true)
            return
        }
        
        console.log("[DEBUG_LOG] handleRemoveWornItem: Using ItemUsageHandler to remove worn item: ${item.title}")
        
        // Use the ItemUsageHandler to handle removing the worn item
        ItemUsageHandler.useItem(
            item = item,
            currentUser = currentUser,
            coroutineScope = coroutineScope,
            onSuccess = { message -> 
                console.log("[DEBUG_LOG] handleRemoveWornItem: Successfully removed worn item: ${item.title}")
                showFeedback(message) 
            },
            onError = { message -> 
                console.error("[DEBUG_LOG] handleRemoveWornItem: Error removing worn item: ${item.title}, error: $message")
                showFeedback(message, true) 
            }
        )
    }
    
    // Function to handle drag start
    fun handleDragStart(event: dynamic, itemId: String) {
        try {
            console.log("[DEBUG_LOG] handleDragStart: Starting drag for itemId=$itemId")
            
            // Store the dragged item ID in state
            draggedItemId = itemId
            
            // Set the data in the dataTransfer object
            try {
                event.dataTransfer.setData("text/plain", itemId)
                console.log("[DEBUG_LOG] handleDragStart: Set data in dataTransfer: $itemId")
                
                // Set the drag effect
                event.dataTransfer.effectAllowed = "move"
            } catch (e: Throwable) {
                console.error("[DEBUG_LOG] handleDragStart: Error setting dataTransfer data", e)
            }
            
            // Get the item to show its details in the log
            val item = items[itemId]
            if (item != null) {
                console.log("[DEBUG_LOG] handleDragStart: Dragging item: ${item.title}, wearable=${item.wearable}")
            } else {
                console.error("[DEBUG_LOG] handleDragStart: Item not found in items map for ID: $itemId")
            }
            
            // Set a custom drag image (optional)
            try {
                val dragImage = document.createElement("div") as HTMLElement
                dragImage.style.width = "48px"
                dragImage.style.height = "48px"
                dragImage.style.backgroundColor = "rgba(255, 255, 255, 0.3)"
                dragImage.style.borderRadius = "4px"
                
                // Add item title to drag image for better visibility
                if (item != null) {
                    dragImage.style.color = "white"
                    dragImage.style.fontSize = "10px"
                    dragImage.style.textAlign = "center"
                    dragImage.style.padding = "2px"
                    // Use property() instead of direct property access for CSS properties
                    dragImage.style.setProperty("overflow", "hidden")
                    dragImage.textContent = item.title
                }
                
                document.body?.appendChild(dragImage)
                event.dataTransfer.setDragImage(dragImage, 24, 24)
                console.log("[DEBUG_LOG] handleDragStart: Custom drag image set")
                
                // Remove the drag image element after it's been used
                window.setTimeout({ 
                    try {
                        document.body?.removeChild(dragImage)
                    } catch (e: Throwable) {
                        console.error("[DEBUG_LOG] handleDragStart: Error removing drag image", e)
                    }
                }, 0)
            } catch (e: Throwable) {
                console.error("[DEBUG_LOG] handleDragStart: Error setting drag image", e)
            }
        } catch (e: Throwable) {
            console.error("[DEBUG_LOG] handleDragStart: Unexpected error", e)
            // Reset the dragged item ID if an error occurs
            draggedItemId = null
        }
    }
    
    // Function to handle drag over
    fun handleDragOver(event: dynamic) {
        try {
            event.preventDefault()
            
            // Get the dragged item ID to provide context in logs
            val itemId = draggedItemId
            console.log("[DEBUG_LOG] handleDragOver: Dragging over with itemId=$itemId")
            
            try {
                // Set the drop effect to indicate a move operation
                event.dataTransfer.dropEffect = "move"
                
                // If we have a valid dragged item, check if it's wearable
                if (itemId != null) {
                    val item = items[itemId]
                    if (item != null) {
                        if (item.wearable) {
                            // Visual feedback could be added here in the future
                            console.log("[DEBUG_LOG] handleDragOver: Item is wearable: ${item.title}")
                        } else {
                            console.log("[DEBUG_LOG] handleDragOver: Item is not wearable: ${item.title}")
                        }
                    }
                }
            } catch (e: Throwable) {
                console.error("[DEBUG_LOG] handleDragOver: Error setting dropEffect", e)
            }
        } catch (e: Throwable) {
            console.error("[DEBUG_LOG] handleDragOver: Unexpected error", e)
            // Don't prevent default if we encounter an error, to allow normal browser behavior
            try {
                event.preventDefault()
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }
    
    // Function to handle drop on character renderer
    fun handleDropOnCharacter(event: dynamic) {
        event.preventDefault()
        
        console.log("[DEBUG_LOG] handleDropOnCharacter: event triggered")
        
        try {
            val inventoryItemId = try {
                val data = event.dataTransfer.getData("text/plain")
                console.log("[DEBUG_LOG] handleDropOnCharacter: got data from dataTransfer: $data")
                data
            } catch (e: Throwable) {
                console.error("[DEBUG_LOG] handleDropOnCharacter: Error getting data from dataTransfer", e)
                null
            }
            
            if (inventoryItemId == null) {
                console.error("[DEBUG_LOG] handleDropOnCharacter: No item ID found in dataTransfer")
                showFeedback("Error: Could not identify the dragged item", true)
                return
            }
            
            console.log("[DEBUG_LOG] handleDropOnCharacter: inventoryItemId=$inventoryItemId")
            
            // Get the actual Item using the InventoryState helper method
            val item = InventoryState.getItemForInventoryItemKey(inventoryItemId)
            if (item == null) {
                console.error("[DEBUG_LOG] handleDropOnCharacter: Item not found for inventory item ID: $inventoryItemId")
                console.log("[DEBUG_LOG] handleDropOnCharacter: Available inventory item mappings: ${InventoryState.inventoryItemToItemMap}")
                console.log("[DEBUG_LOG] handleDropOnCharacter: Available items: ${items.keys.joinToString()}")
                showFeedback("Error: Item not found", true)
                return
            }
            
            console.log("[DEBUG_LOG] handleDropOnCharacter: Found item: ${item.title}, wearable=${item.wearable}")
            
            // Check if the item is wearable
            if (!item.wearable) {
                console.log("[DEBUG_LOG] handleDropOnCharacter: Item is not wearable: ${item.title}")
                showFeedback("Item is not wearable: ${item.title}", true)
            } else {
                console.log("[DEBUG_LOG] handleDropOnCharacter: Attempting to wear item: ${item.title}")
                
                // Use the ItemUsageHandler to handle wearing the item
                ItemUsageHandler.useItem(
                    item = item,
                    currentUser = currentUser,
                    coroutineScope = coroutineScope,
                    onSuccess = { message -> 
                        console.log("[DEBUG_LOG] handleDropOnCharacter: Successfully wore item: ${item.title}")
                        showFeedback(message) 
                    },
                    onError = { message -> 
                        console.error("[DEBUG_LOG] handleDropOnCharacter: Error wearing item: ${item.title}, error: $message")
                        showFeedback(message, true) 
                    }
                )
            }
        } catch (e: Throwable) {
            console.error("[DEBUG_LOG] handleDropOnCharacter: Unexpected error", e)
            showFeedback("An unexpected error occurred", true)
        } finally {
            draggedItemId = null
        }
    }
    
    // Helper to reorder items using source and target IDs
    fun reorderItems(sourceItemId: String, targetItemId: String) {
        console.log("[DEBUG_LOG] reorderItems: sourceItemId=$sourceItemId, targetItemId=$targetItemId")

        if (sourceItemId.isEmpty()) {
            console.error("[DEBUG_LOG] reorderItems: sourceItemId is empty")
            return
        }
        if (targetItemId.isEmpty()) {
            console.error("[DEBUG_LOG] reorderItems: targetItemId is empty")
            return
        }
        if (sourceItemId == targetItemId) return

        val currentInventory = inventory ?: return
        val currentOrder = currentInventory.itemOrder.toMutableList()

        val sourceIndex = currentOrder.indexOf(sourceItemId)
        val targetIndex = currentOrder.indexOf(targetItemId)
        console.log("[DEBUG_LOG] reorderItems: sourceIndex=$sourceIndex, targetIndex=$targetIndex")

        if (sourceIndex != -1 && targetIndex != -1) {
            currentOrder.removeAt(sourceIndex)
            val insertIndex = if (sourceIndex < targetIndex) targetIndex else targetIndex
            currentOrder.add(insertIndex, sourceItemId)

            // Update local state
            inventory = currentInventory.copy(itemOrder = currentOrder)

            // Save the updated order via API
            coroutineScope.launch {
                try {
                    console.log("[DEBUG_LOG] Saving updated item order: ${currentOrder}")
                    val updatedInventory = InventoryApi.updateInventoryItemOrder(currentOrder)
                    console.log("[DEBUG_LOG] Successfully saved item order")
                    InventoryState.inventory = updatedInventory
                    showFeedback("Item order updated")
                } catch (error: Throwable) {
                    console.error("[DEBUG_LOG] Error saving item order", error)
                    showFeedback("Failed to save item order", true)
                    inventory = currentInventory
                }
            }
        }
    }

    // Function to handle drop for reordering inventory (mouse-based)
    fun handleDrop(event: dynamic, targetItemId: String) {
        event.preventDefault()
        val sourceItemId = draggedItemId ?: return
        reorderItems(sourceItemId, targetItemId)
        draggedItemId = null
    }
    
    // Show Create Item Dialog if needed
    if (showCreateItemDialog) {
        CreateItemDialog(
            onClose = { showCreateItemDialog = false },
            onItemCreated = { _ ->
                // Refresh inventory after item creation using shared state
                InventoryState.refreshInventory(
                    coroutineScope = coroutineScope,
                    onSuccess = {
                        showFeedback("Inventory updated")
                    },
                    onError = { message ->
                        console.error("Error refreshing inventory after create", message)
                        showFeedback(str("Failed to refresh inventory") + ": " + message, true)
                    }
                )
            }
        )
    }
    
    // Marketplace dialog
    if (showMarketplaceDialog) {
        MarketplaceDialog(
            onClose = { showMarketplaceDialog = false },
            onPurchased = {
                InventoryState.refreshInventory(
                    coroutineScope = coroutineScope,
                    onSuccess = { showFeedback(str("Inventory refreshed")) },
                    onError = { message -> showFeedback(str("Failed to refresh inventory") + ": " + message, true) }
                )
            }
        )
    }
    
    // Function to refresh inventory data
    fun refreshInventory() {
        // Use the shared inventory state to refresh data
        InventoryState.refreshInventory(
            coroutineScope = coroutineScope,
            onSuccess = {
                showFeedback(str("Inventory refreshed"))
            },
            onError = { message ->
                showFeedback(str("Failed to refresh inventory") + ": " + message, true)
            }
        )
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Inventory"),
        headerActions = {
            DialogButton(
                onClick = { showMarketplaceDialog = true },
                isPrimary = true,
                color = "rgb(53, 134, 36)"
            ) { Text("🛍️ "); Translated("Marketplace") }
        },
        content = {
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        height(100.percent)
                        property("overflow-y", "auto")
                        property("position", "relative") // For positioning the feedback message
                    }
                }
            ) {
                // Render feedback message if present
                feedbackMessage?.let { message ->
                    Div(
                        attrs = {
                            style {
                                position(Position.Absolute)
                                top(0.px)
                                left(50.percent)
                                property("transform", "translateX(-50%)")
                                padding(8.px, 12.px)
                                backgroundColor(if (isErrorFeedback) rgba(255, 0, 0, 0.7) else rgba(0, 150, 0, 0.7))
                                color(Color.white)
                                fontSize(14.px)
                                property("border-radius", "4px")
                                property("z-index", "10")
                                opacity(0.9)
                            }
                        }
                    ) {
                        Text(message)
                    }
                }
                if (isLoading) {
                    P(
                        attrs = {
                            style {
                                color(Color.white)
                                textAlign("center")
                            }
                        }
                    ) {
                        Translated("Loading inventory...")
                    }
                } else if (errorMessage.isNotEmpty()) {
                    P(
                        attrs = {
                            style {
                                color(rgba(255, 100, 100, 0.9))
                                textAlign("center")
                            }
                        }
                    ) {
                        Text(translated("Error") + ": " + errorMessage)
                    }
                } else {
                    // Character renderer section
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                property("flex-direction", "column")
                                alignItems(AlignItems.Center)
                                marginBottom(20.px)
                                padding(16.px)
                                backgroundColor(rgba(40, 40, 40, 0.5))
                                property("border-radius", "8px")
                            }
                        }
                    ) {
                        // Title
                        H3(
                            attrs = {
                                style {
                                    color(Color.white)
                                    marginBottom(12.px)
                                    fontSize(18.px)
                                }
                            }
                        ) {
                            Translated("Your character")
                        }
                        
                        // Character renderer with drop zone
                        var isDropTargetActive by remember { mutableStateOf(false) }
                        
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.Center)
                                    padding(8.px)
                                    backgroundColor(rgba(30, 30, 30, 0.5))
                                    property("border-radius", "8px")
                                    // Change border style based on whether an item is being dragged over
                                    if (isDropTargetActive) {
                                        property("border", "3px dashed rgba(100, 200, 255, 0.8)")
                                        property("box-shadow", "0 0 10px rgba(100, 200, 255, 0.5)")
                                        property("transition", "all 0.2s ease")
                                    } else {
                                        property("border", "2px dashed rgba(100, 100, 100, 0.5)")
                                        property("transition", "all 0.2s ease")
                                    }
                                    cursor("pointer")
                                }
                                // Make droppable
                                onDragOver { event -> 
                                    handleDragOver(event.nativeEvent)
                                    // Check if the dragged item is wearable before activating the drop target
                                    val itemId = draggedItemId
                                    if (itemId != null) {
                                        val item = items[itemId]
                                        isDropTargetActive = item?.wearable == true
                                    }
                                }
                                onDragLeave {
                                    console.log("[DEBUG_LOG] onDragLeave: Drag left character drop zone")
                                    isDropTargetActive = false
                                }
                                onDrop { event -> 
                                    handleDropOnCharacter(event.nativeEvent)
                                    isDropTargetActive = false
                                }
                                onTouchDrop { _, _, data, dataType ->
                                    if (dataType == DataType.INVENTORY_ITEM) {
                                        try {
                                            val payload = json.decodeFromString(InventoryDragPayload.serializer(), data)
                                            handleWearItem(payload.inventoryItemKey)
                                        } catch (e: Throwable) {
                                            console.error("[DEBUG_LOG] onTouchDrop character: error decoding payload", e)
                                            showFeedback("Error: Could not equip item", true)
                                        } finally {
                                            isDropTargetActive = false
                                        }
                                    }
                                }
                            }
                        ) {
                            // Character renderer
                            currentUser?.let { user ->
                                CharacterRenderer(
                                    bodyType = user.bodyType,
                                    skinTone = user.skinTone,
                                    eyeColor = user.eyeColor,
                                    hairStyle = user.hairStyle
                                )
                            }
                            
                            // Show a hint message when a wearable item is being dragged over
                            if (isDropTargetActive) {
                                Div(
                                    attrs = {
                                        style {
                                            position(Position.Absolute)
                                            property("bottom", "8px")
                                            backgroundColor(rgba(0, 0, 0, 0.7))
                                            color(rgba(100, 200, 255, 0.9))
                                            padding(4.px, 8.px)
                                            property("border-radius", "4px")
                                            fontSize(12.px)
                                            textAlign("center")
                                        }
                                    }
                                ) {
                                    Translated("Drop to wear item")
                                }
                            }
                        }
                        
                        // Instruction text
                        P(
                            attrs = {
                                style {
                                    color(rgba(200, 200, 200, 0.8))
                                    fontSize(14.px)
                                    marginTop(8.px)
                                    textAlign("center")
                                }
                            }
                        ) {
                            Translated("Drag wearable items here to equip them")
                        }
                    }
                    
                    // Worn items section
                    Div(
                        attrs = {
                            style {
                                marginBottom(20.px)
                            }
                        }
                    ) {
                        // Section title
                        H3(
                            attrs = {
                                style {
                                    color(Color.white)
                                    marginBottom(12.px)
                                    fontSize(18.px)
                                }
                            }
                        ) {
                            Translated("Worn items")
                        }
                        
                        // Display worn items or a message if none
                        if (currentUser?.wornItems?.isEmpty() == true) {
                            P(
                                attrs = {
                                    style {
                                        color(rgba(200, 200, 200, 0.8))
                                        fontSize(14.px)
                                        textAlign("center")
                                        padding(12.px)
                                        backgroundColor(rgba(40, 40, 40, 0.3))
                                        property("border-radius", "4px")
                                    }
                                }
                            ) {
                                Translated("No items worn. Drag wearable items to your character to wear them.")
                            }
                        } else {
                            // Display worn items
                            Div(
                                attrs = {
                                    style {
                                        display(DisplayStyle.Flex)
                                        property("flex-direction", "row")
                                        property("flex-wrap", "wrap")
                                        property("gap", "8px")
                                    }
                                }
                            ) {
                                currentUser?.wornItems?.forEach { itemId ->
                                    val item = items[itemId]
                                    if (item != null) {
                                        Div(
                                            attrs = {
                                                style {
                                                    display(DisplayStyle.Flex)
                                                    property("flex-direction", "column")
                                                    alignItems(AlignItems.Center)
                                                    padding(8.px)
                                                    backgroundColor(rgba(60, 60, 60, 0.5))
                                                    property("border-radius", "4px")
                                                    cursor("pointer")
                                                }
                                                onClick { handleRemoveWornItem(itemId, isDirectItemId = true) }
                                            }
                                        ) {
                                            // Item icon
                                            InventoryBox(
                                                onClick = { /* Handled by parent */ },
                                                item = item,
                                                isLoading = isLoading,
                                                quantity = 1 // Worn items always have quantity 1
                                            )
                                            
                                            // Item name
                                            Div(
                                                attrs = {
                                                    style {
                                                        color(Color.white)
                                                        fontSize(12.px)
                                                        marginTop(4.px)
                                                        textAlign("center")
                                                    }
                                                }
                                            ) {
                                                Text(item.title)
                                            }
                                            
                                            // Remove text
                                            Div(
                                                attrs = {
                                                    style {
                                                        color(rgba(255, 100, 100, 0.9))
                                                        fontSize(10.px)
                                                        marginTop(2.px)
                                                    }
                                                }
                                            ) {
                                                Translated("Click to remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Inventory items section
                    H3(
                        attrs = {
                            style {
                                color(Color.white)
                                marginBottom(12.px)
                                fontSize(18.px)
                            }
                        }
                    ) {
                        Translated("Inventory items")
                    }
                    
                    if (inventory?.itemOrder?.isEmpty() == true) {
                        P(
                            attrs = {
                                style {
                                    color(Color.white)
                                    textAlign("center")
                                }
                            }
                        ) {
                            Translated("Your inventory is empty. Click '+ New' to create an item.")
                        }
                    } else {
                        // Display inventory items
                        inventory?.itemOrder?.forEach { itemId ->
                            val item = InventoryState.getItemForInventoryItemKey(itemId)
                            if (item != null) {
                                Div(
                                    attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            padding(12.px)
                                            marginBottom(8.px)
                                            backgroundColor(rgba(50, 50, 50, 0.5))
                                            property("border-radius", "4px")
                                            alignItems(AlignItems.Center)
                                            cursor("grab")
                                        }
                                        // Make draggable
                                        attr("draggable", "true")
                                        onDragStart { event -> handleDragStart(event.nativeEvent, itemId) }
                                        onDragOver { event -> handleDragOver(event.nativeEvent) }
                                        onDrop { event -> handleDrop(event.nativeEvent, itemId) }
                                        onTouchDrop { _, _, data, dataType ->
                                            if (dataType == DataType.INVENTORY_ITEM) {
                                                try {
                                                    val payload = json.decodeFromString(InventoryDragPayload.serializer(), data)
                                                    reorderItems(payload.inventoryItemKey, itemId)
                                                } catch (e: Throwable) {
                                                    console.error("[DEBUG_LOG] onTouchDrop row: error decoding payload", e)
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    // Use InventoryBox to render the item
                                    InventoryBox(
                                        onClick = { /* No action on click */ },
                                        item = item,
                                        isLoading = isLoading,
                                        quantity = InventoryState.getQuantityForInventoryItemKey(itemId),
                                        inventoryItemKey = itemId,
                                    )
                                    
                                    // Item details
                                    Div(
                                        attrs = {
                                            style {
                                                flexGrow(1)
                                                marginLeft(12.px)
                                            }
                                        }
                                    ) {
                                        // Item title with wearable indicator
                                        Div(
                                            attrs = {
                                                style {
                                                    display(DisplayStyle.Flex)
                                                    alignItems(AlignItems.Center)
                                                }
                                            }
                                        ) {
                                            Div(
                                                attrs = {
                                                    style {
                                                        color(Color.white)
                                                        fontSize(16.px)
                                                        fontWeight("bold")
                                                    }
                                                }
                                            ) {
                                                Text(item.title)
                                            }
                                            
                                            // Wearable indicator
                                            if (item.wearable) {
                                                Div(
                                                    attrs = {
                                                        style {
                                                            margin(8.px)
                                                            padding(2.px, 6.px)
                                                            backgroundColor(rgba(100, 200, 255, 0.2))
                                                            color(rgba(100, 200, 255, 0.9))
                                                            fontSize(10.px)
                                                            property("border-radius", "4px")
                                                        }
                                                    }
                                                ) {
                                                    Translated("Wearable")
                                                }
                                            }
                                        }
                                        
                                        // Item description
                                        Div(
                                            attrs = {
                                                style {
                                                    color(rgba(200, 200, 200, 0.8))
                                                    fontSize(14.px)
                                                    marginTop(4.px)
                                                }
                                            }
                                        ) {
                                            Text(item.description)
                                        }
                                    }
                                    
                                    // Action buttons
                                    Div(
                                        attrs = {
                                            style {
                                                display(DisplayStyle.Flex)
                                                property("gap", "8px")
                                            }
                                        }
                                    ) {
                                        // Use button
                                        DialogButton(
                                            onClick = {
                                                if (item.wearable) {
                                                    handleWearItem(itemId)
                                                } else {
                                                    handleUseItem(itemId)
                                                }
                                            },
                                            isPrimary = true
                                        ) {
                                            Translated(
                                                when {
                                                    item.wearable && currentUser?.wornItems?.any { it == item.key } == true -> "Take off"
                                                    item.wearable -> "Wear"
                                                    else -> "Use"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
