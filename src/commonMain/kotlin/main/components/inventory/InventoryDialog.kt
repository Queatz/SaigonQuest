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
import main.theme.Styles
import main.theme.Theme
import models.Inventory
import models.InventoryItem
import models.Item
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

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
    
    Div(
        attrs = {
            classes(Styles.overlayBase)
            // Add click outside to close
            onClick { onClose() }
        }
    ) {
        Div(
            attrs = {
                style {
                    width(90.percent)
                    property("max-width", "600px")
                    height(80.vh)
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Inventory header
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                H3(
                    attrs = {
                        style {
                            margin(0.px)
                            color(Color.white)
                        }
                    }
                ) {
                    Text("Inventory")
                }
                
                // Close button
                Span(
                    attrs = {
                        style {
                            fontSize(24.px)
                            color(rgba(255, 255, 255, 0.7))
                            cursor("pointer")
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Inventory content
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
                        Text("Your inventory is empty.")
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
                                // Item image
                                Div(
                                    attrs = {
                                        style {
                                            width(48.px)
                                            height(48.px)
                                            backgroundColor(rgba(100, 100, 100, 0.5))
                                            marginRight(12.px)
                                            property("border-radius", "4px")
                                            
                                            // Use assetIndex to display the correct sprite
                                            if (item.file.isNotEmpty()) {
                                                property("background-image", "url(${item.file})")
                                                property("background-size", "320px 320px")
                                                
                                                // Calculate position based on assetIndex
                                                val col = item.assetIndex % 10
                                                val row = item.assetIndex / 10
                                                property("background-position", "-${col * 32}px -${row * 32}px")
                                            }
                                        }
                                    }
                                ) {}
                                
                                // Item details
                                Div(
                                    attrs = {
                                        style {
                                            flexGrow(1)
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
                                Button(
                                    attrs = {
                                        style {
                                            padding(6.px, 12.px)
                                            backgroundColor(Theme.colors.primary)
                                            color(Theme.colors.onPrimary)
                                            property("border", "0")
                                            property("border-radius", "4px")
                                            cursor("pointer")
                                        }
                                        onClick { handleUseItem(itemId) }
                                    }
                                ) {
                                    Text("Use")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
