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
import main.components.BaseDialog
import main.components.DialogButton
import models.Inventory
import models.Item
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.height
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
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Load inventory and items when the dialog is opened
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        try {
            // Get user inventory
            val userInventory = InventoryApi.getUserInventory()
            inventory = userInventory
            
            // Get items by their IDs
            if (userInventory.itemOrder.isNotEmpty()) {
                val itemsList = ItemApi.getItemsByIds(userInventory.itemOrder)
                items = itemsList.associateBy { it.key }
            }
            
            isLoading = false
        } catch (error: Exception) {
            console.error("Error loading inventory", error)
            errorMessage = error.message ?: "Failed to load inventory"
            isLoading = false
        }
    }
    
    // Function to handle using an item
    fun handleUseItem(itemId: String) {
        // Implement item usage logic here
        val item = items[itemId]
        if (item != null) {
            console.log("Using item: ${item.title}")
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
    
    // Function to handle drop
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
                } else if (inventory?.itemOrder?.isEmpty() == true) {
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
                                    // Item title
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
                                
                                // Use button
                                DialogButton(
                                    onClick = { handleUseItem(itemId) },
                                    isPrimary = true
                                ) {
                                    Text("Use")
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
