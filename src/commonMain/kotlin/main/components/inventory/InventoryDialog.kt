package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.ItemApi
import main.api.UserApi
import main.api.WebSocketApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.player.CharacterRenderer
import models.Inventory
import models.Item
import models.User
import models.UpdatePlayerData
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

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
    var currentUser by remember { mutableStateOf<User?>(null) }
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Load inventory, items, and user when the dialog is opened
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        try {
            // Get current user
            val user = UserApi.getCurrentUser()
            currentUser = user
            
            // Get user inventory
            val userInventory = InventoryApi.getUserInventory()
            inventory = userInventory
            
            // Get items by their IDs
            if (userInventory.itemOrder.isNotEmpty()) {
                val itemsList = ItemApi.getItemsByIds(userInventory.itemOrder)
                items = itemsList.associateBy { it.key }
            }
            
            // Add WebSocket listener for player updates
            WebSocketApi.addUpdatePlayerListener { updateData ->
                // Check if this is an update for another player
                if (updateData.userId != user.key) {
                    // If we have the updated player's data in our current user, update it
                    if (currentUser?.key == updateData.userId) {
                        // Refresh the user data to get the updated worn items
                        coroutineScope.launch {
                            try {
                                val updatedUser = UserApi.fetchCurrentUser()
                                currentUser = updatedUser
                            } catch (error: Exception) {
                                console.error("Error refreshing user data", error)
                            }
                        }
                    }
                }
            }
            
            isLoading = false
        } catch (error: Exception) {
            console.error("Error loading inventory", error)
            errorMessage = error.message ?: "Failed to load inventory"
            isLoading = false
        }
    }
    
    // Note: WebSocket listeners will be cleaned up when the application is closed
    // or when the user navigates away from the page
    
    // Function to handle using an item
    fun handleUseItem(itemId: String) {
        // Implement item usage logic here
        val item = items[itemId]
        if (item != null) {
            console.log("Using item: ${item.title}")
        }
    }
    
    // Function to handle wearing an item
    fun handleWearItem(itemId: String) {
        val user = currentUser ?: return
        val item = items[itemId] ?: return
        
        // Check if the item is wearable
        if (!item.wearable) {
            console.log("Item is not wearable: ${item.title}")
            return
        }
        
        // Add the item to the user's worn items if not already worn
        if (!user.wornItems.contains(itemId)) {
            val updatedWornItems = user.wornItems.toMutableList()
            updatedWornItems.add(itemId)
            
            // Update the user locally
            user.wornItems = updatedWornItems
            currentUser = user
            
            // Update the user on the server
            coroutineScope.launch {
                try {
                    val updatedUser = UserApi.updateUserWornItems(user)
                    currentUser = updatedUser
                    
                    // Broadcast the change to other players
                    WebSocketApi.updatePlayer(
                        name = user.username,
                        isPlayer = user.isPlayer,
                        status = user.status
                    )
                } catch (error: Exception) {
                    console.error("Error updating worn items", error)
                }
            }
        }
    }
    
    // Function to handle removing a worn item
    fun handleRemoveWornItem(itemId: String) {
        val user = currentUser ?: return
        
        // Remove the item from the user's worn items
        if (user.wornItems.contains(itemId)) {
            val updatedWornItems = user.wornItems.toMutableList()
            updatedWornItems.remove(itemId)
            
            // Update the user locally
            user.wornItems = updatedWornItems
            currentUser = user
            
            // Update the user on the server
            coroutineScope.launch {
                try {
                    val updatedUser = UserApi.updateUserWornItems(user)
                    currentUser = updatedUser
                    
                    // Broadcast the change to other players
                    WebSocketApi.updatePlayer(
                        name = user.username,
                        isPlayer = user.isPlayer,
                        status = user.status
                    )
                } catch (error: Exception) {
                    console.error("Error updating worn items", error)
                }
            }
        }
    }
    
    // Function to handle drag start
    fun handleDragStart(event: dynamic, itemId: String) {
        draggedItemId = itemId
        event.dataTransfer.setData("text/plain", itemId)
        
        // Set a custom drag image (optional)
        val dragImage = document.createElement("div") as HTMLElement
        dragImage.style.width = "48px"
        dragImage.style.height = "48px"
        dragImage.style.backgroundColor = "rgba(255, 255, 255, 0.3)"
        dragImage.style.borderRadius = "4px"
        document.body?.appendChild(dragImage)
        event.dataTransfer.setDragImage(dragImage, 24, 24)
        
        // Remove the drag image element after it's been used
        window.setTimeout({ document.body?.removeChild(dragImage) }, 0)
    }
    
    // Function to handle drag over
    fun handleDragOver(event: dynamic) {
        event.preventDefault()
        event.dataTransfer.dropEffect = "move"
    }
    
    // Function to handle drop on character renderer
    fun handleDropOnCharacter(event: dynamic) {
        event.preventDefault()
        
        val itemId = event.dataTransfer.getData("text/plain") ?: return
        handleWearItem(itemId)
        
        draggedItemId = null
    }
    
    // Function to handle drop for reordering inventory
    fun handleDrop(event: dynamic, targetItemId: String) {
        event.preventDefault()
        
        val sourceItemId = draggedItemId ?: return
        if (sourceItemId == targetItemId) return
        
        // Get current inventory
        val currentInventory = inventory ?: return
        val currentOrder = currentInventory.itemOrder.toMutableList()
        
        // Find indices
        val sourceIndex = currentOrder.indexOf(sourceItemId)
        val targetIndex = currentOrder.indexOf(targetItemId)
        
        if (sourceIndex != -1 && targetIndex != -1) {
            // Remove source item
            currentOrder.removeAt(sourceIndex)
            
            // Insert at target position
            val insertIndex = if (sourceIndex < targetIndex) targetIndex else targetIndex
            currentOrder.add(insertIndex, sourceItemId)
            
            // Update local state
            inventory = currentInventory.copy(itemOrder = currentOrder)
        }
        
        draggedItemId = null
    }
    
    // Show Create Item Dialog if needed
    if (showCreateItemDialog) {
        CreateItemDialog(
            onClose = { showCreateItemDialog = false },
            onItemCreated = { newItem ->
                // Refresh inventory after item creation
                coroutineScope.launch {
                    try {
                        val userInventory = InventoryApi.getUserInventory()
                        inventory = userInventory
                        
                        if (userInventory.itemOrder.isNotEmpty()) {
                            val itemsList = ItemApi.getItemsByIds(userInventory.itemOrder)
                            items = itemsList.associateBy { it.key }
                        }
                    } catch (error: Exception) {
                        console.error("Error refreshing inventory", error)
                    }
                }
            }
        )
    }
    
    // Header actions for the dialog
    val headerActions: @Composable () -> Unit = {
        Button(
            attrs = {
                style {
                    backgroundColor(rgba(0, 120, 255, 0.7))
                    color(Color.white)
                    property("border", "0")
                    property("border-radius", "4px")
                    padding(4.px, 8.px)
                    marginRight(8.px)
                    cursor("pointer")
                    fontSize(14.px)
                }
                onClick { showCreateItemDialog = true }
            }
        ) {
            Text("+ New")
        }
    }
    
    BaseDialog(
        onClose = onClose,
        title = "Inventory",
        headerActions = headerActions,
        maxWidth = "600px",
        content = {
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        height(100.percent)
                        property("overflow-y", "auto")
                    }
                }
            ) {
                if (isLoading) {
                    P(
                        attrs = {
                            style {
                                color(Color.white)
                                textAlign("center")
                            }
                        }
                    ) {
                        Text("Loading inventory...")
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
                        Text("Error: $errorMessage")
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
                            Text("Your Character")
                        }
                        
                        // Character renderer with drop zone
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.Center)
                                    padding(8.px)
                                    backgroundColor(rgba(30, 30, 30, 0.5))
                                    property("border-radius", "8px")
                                    property("border", "2px dashed rgba(100, 100, 100, 0.5)")
                                    cursor("pointer")
                                }
                                // Make droppable
                                onDragOver { event -> handleDragOver(event.nativeEvent) }
                                onDrop { event -> handleDropOnCharacter(event.nativeEvent) }
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
                            Text("Drag wearable items here to equip them")
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
                            Text("Worn Items")
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
                                Text("No items worn. Drag wearable items to your character to wear them.")
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
                                                onClick { handleRemoveWornItem(itemId) }
                                            }
                                        ) {
                                            // Item icon
                                            InventoryBox(
                                                onClick = { /* Handled by parent */ },
                                                item = item
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
                                                Text("(Click to remove)")
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
                        Text("Inventory Items")
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
                            Text("Your inventory is empty. Click '+ New' to create an item.")
                        }
                    } else {
                        // Display inventory items
                        inventory?.itemOrder?.forEach { itemId ->
                            val item = items[itemId]
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
                                            // Highlight wearable items
                                            if (item.wearable) {
                                                property("border-left", "4px solid rgba(100, 200, 255, 0.7)")
                                            }
                                        }
                                        // Make draggable
                                        attr("draggable", "true")
                                        onDragStart { event -> handleDragStart(event.nativeEvent, itemId) }
                                        onDragOver { event -> handleDragOver(event.nativeEvent) }
                                        onDrop { event -> handleDrop(event.nativeEvent, itemId) }
                                    }
                                ) {
                                    // Use InventoryBox to render the item
                                    InventoryBox(
                                        onClick = { /* No action on click */ },
                                        item = item
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
                                                            marginLeft(8.px)
                                                            padding(2.px, 6.px)
                                                            backgroundColor(rgba(100, 200, 255, 0.2))
                                                            color(rgba(100, 200, 255, 0.9))
                                                            fontSize(10.px)
                                                            property("border-radius", "4px")
                                                        }
                                                    }
                                                ) {
                                                    Text("Wearable")
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
                                            onClick = { handleUseItem(itemId) },
                                            isPrimary = true
                                        ) {
                                            Text("Use")
                                        }
                                        
                                        // Wear button for wearable items
                                        if (item.wearable) {
                                            DialogButton(
                                                onClick = { handleWearItem(itemId) },
                                                isPrimary = false
                                            ) {
                                                Text("Wear")
                                            }
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
