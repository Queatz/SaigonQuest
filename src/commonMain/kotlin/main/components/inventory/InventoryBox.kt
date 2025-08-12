package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.document
import kotlinx.browser.window
import main.api.ApiClient
import main.theme.InventoryStyles
import main.theme.Styles
import main.utils.DataType
import models.Item
import org.jetbrains.compose.web.attributes.Draggable
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * An inventory box that can display an item.
 * Can also show a loading state when items are being loaded.
 * Displays the quantity of the item if greater than 1.
 */
@Composable
fun InventoryBox(
    onClick: () -> Unit,
    item: Item? = null,
    content: String = "",
    isLoading: Boolean = false,
    quantity: Int = 0,
    inventoryItemKey: String? = null,
) {
    LaunchedEffect(item) {
        console.log("[Inventory] item= $item, quantity= $quantity")
    }

    Div(
        attrs = {
            classes(InventoryStyles.inventoryBox)
            onClick { if (!isLoading) onClick() }
            
            // Add draggable attribute for items
            if (item != null) {
                draggable(Draggable.True)
                
                // Add drag start event handler
                onDragStart { event ->
                    // If we have inventory context, send InventoryDragPayload; otherwise fall back to Item
                    if (inventoryItemKey != null) {
                        val payload = InventoryDragPayload(
                            inventoryItemKey = inventoryItemKey,
                            itemKey = item.key,
                            quantity = quantity,
                            inventoryId = null,
                        )
                        event.dataTransfer?.setData("application/json", ApiClient.json.encodeToString(InventoryDragPayload.serializer(), payload))
                        event.dataTransfer?.setData("application/x-data-type", DataType.INVENTORY_ITEM.name)
                    } else {
                        console.error("[Inventory] inventoryItemKey is null")
                    }
                    
                    // Create a custom drag image (optional)
                    val dragIcon = document.createElement("div") as HTMLElement
                    // Style the drag icon to match IconRenderer rendering
                    dragIcon.style.position = "absolute"
                    dragIcon.style.top = "-1000px"
                    dragIcon.style.width = "32px"
                    dragIcon.style.height = "32px"
                    dragIcon.style.setProperty("pointer-events", "none")

                    // If the item has a file, render the correct sprite frame
                    if (item.file.isNotEmpty()) {
                        val url = "/raw/data/spritesheets/${item.file}.png"
                        dragIcon.style.setProperty("background-image", "url(\"$url\")")
                        dragIcon.style.setProperty("background-size", "320px 320px")
                        val col = item.assetIndex % 10
                        val row = item.assetIndex / 10
                        dragIcon.style.setProperty("background-position", "-${col * 32}px -${row * 32}px")
                    }

                    document.body?.appendChild(dragIcon)

                    // Set the drag image
                    event.dataTransfer?.setDragImage(dragIcon, 15, 15)

                    // Remove the drag icon after it's been used
                    window.setTimeout({
                        document.body?.removeChild(dragIcon)
                    }, 0)
                }
            }
            
            // Add cursor style based on loading state and draggability
            style {
                cursor(
                    when {
                        isLoading -> "wait"
                        item != null -> "grab"
                        else -> "pointer"
                    }
                )
            }
        }
    ) {
        if (isLoading) {
            // Show loading indicator
            Div(
                attrs = {
                    classes(InventoryStyles.loadingIndicator)
                }
            ) {
                // Loading dots
                Div(
                    attrs = {
                        classes(InventoryStyles.loadingDots)
                    }
                ) {
                    Text("...")
                }
            }
        } else if (item != null) {
            // Render the item if we have one
            ItemRenderer(item = item, size = 32)
            
            // Show quantity indicator if quantity is greater than 1
            if (quantity > 1) {
                Div(
                    attrs = {
                        classes(InventoryStyles.quantityIndicator)
                    }
                ) {
                    Text(quantity.toString())
                }
            }
        } else if (content.isNotEmpty()) {
            // Otherwise show the content text
            Text(content)
        }
    }
}
