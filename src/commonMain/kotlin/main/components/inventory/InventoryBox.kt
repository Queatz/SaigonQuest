package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.document
import kotlinx.browser.window
import main.components.FramedBorder
import main.components.attrs.touchDraggable
import main.theme.InventoryStyles
import main.theme.Styles
import main.utils.DataType
import models.Item
import models.Sex
import org.jetbrains.compose.web.attributes.Draggable
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import utils.json

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
    isWorn: Boolean = false,
    sex: Sex? = null,
    useBorder: Boolean = true,
    draggable: Boolean = true,
) {
    LaunchedEffect(item) {
        console.log("[Inventory] item= $item, quantity= $quantity, isWorn= $isWorn, sex= $sex")
    }

    val boxOnClick = onClick
    val contentAttrs: AttrBuilderContext<out HTMLElement> = {
        classes(InventoryStyles.inventoryBox)
        if (isWorn) {
            when (sex) {
                Sex.Boy -> classes(InventoryStyles.inventoryBoxWornBoy)
                Sex.Girl -> classes(InventoryStyles.inventoryBoxWornGirl)
                else -> {}
            }
        }
        onClick { if (!isLoading) boxOnClick() }

        // Add draggable attribute for items
        if (item != null) {
            if (draggable) {
                draggable(Draggable.True)

                // Add drag start event handler
                onDragStart { event ->
                    // Always set text/plain if we have an item
                    event.dataTransfer?.setData("text/plain", inventoryItemKey ?: item.key)

                    // If we have inventory context, send InventoryDragPayload; otherwise fall back to Item
                    if (inventoryItemKey != null) {
                        val payload = InventoryDragPayload(
                            inventoryItemKey = inventoryItemKey,
                            itemKey = item.key,
                            quantity = quantity,
                            inventoryId = null,
                        )
                        event.dataTransfer?.setData(
                            "application/json",
                            json.encodeToString(InventoryDragPayload.serializer(), payload)
                        )
                        event.dataTransfer?.setData("application/x-data-type", DataType.INVENTORY_ITEM.name)
                    } else {
                        console.log(
                            "[DEBUG_LOG] [InventoryBox] inventoryItemKey is null, using item.key instead for text/plain"
                        )
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
                        dragIcon.style.setProperty("background-position", "-${col * 32}px -${(9 - row) * 32}px")
                        dragIcon.style.setProperty("background-repeat", "no-repeat")
                    }

                    document.body?.appendChild(dragIcon)

                    // Set the drag image
                    event.dataTransfer?.setDragImage(dragIcon, 15, 15)

                    // Remove the drag icon after it's been used
                    window.setTimeout({
                        document.body?.removeChild(dragIcon)
                    }, 0)
                }
            } else {
                // If not draggable, explicitly set to false to avoid default behavior interference
                draggable(Draggable.False)
            }

            // Touch drag support - always added if we have an item and inventoryItemKey
            if (inventoryItemKey != null) {
                touchDraggable(
                    dataProvider = {
                        val payload = InventoryDragPayload(
                            inventoryItemKey = inventoryItemKey,
                            itemKey = item.key,
                            quantity = quantity,
                            inventoryId = null,
                        )
                        json.encodeToString(InventoryDragPayload.serializer(), payload)
                    },
                    dataType = DataType.INVENTORY_ITEM,
                    previewBuilder = { preview ->
                        preview.style.width = "32px"
                        preview.style.height = "32px"
                        preview.style.setProperty("pointer-events", "none")
                        if (item.file.isNotEmpty()) {
                            val url = "/raw/data/spritesheets/${item.file}.png"
                            preview.style.setProperty("background-image", "url(\"$url\")")
                            preview.style.setProperty("background-size", "320px 320px")
                            val col = item.assetIndex % 10
                            val row = item.assetIndex / 10
                            preview.style.setProperty("background-position", "-${col * 32}px -${(9 - row) * 32}px")
                            preview.style.setProperty("background-repeat", "no-repeat")
                        }
                    },
                )
            }
        }

        // Add cursor style based on loading state and draggability
        style {
            cursor(
                when {
                    isLoading -> "wait"
                    item != null && draggable -> "grab"
                    item != null -> "pointer"
                    else -> "pointer"
                }
            )
        }
    }

    if (useBorder) {
        FramedBorder(
            borderImage = "/raw/data/ui/borders/input.png",
            backgroundImage = "/raw/data/ui/backgrounds/whitestone.png",
            darkBackgroundImage = "/raw/data/ui/backgrounds/chalkboard.png",
            attrs = {
                classes(Styles.dialogInputWrapper)
                contentAttrs()
            }
        ) {
            InventoryBoxContent(isLoading, item, quantity, content)
        }
    } else {
        Div(
            attrs = contentAttrs
        ) {
            InventoryBoxContent(isLoading, item, quantity, content)
        }
    }
}

@Composable
private fun InventoryBoxContent(
    isLoading: Boolean,
    item: Item?,
    quantity: Int,
    content: String,
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
