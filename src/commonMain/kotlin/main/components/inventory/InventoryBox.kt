package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import main.theme.InventoryStyles
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

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
    quantity: Int = 0
) {
    LaunchedEffect(item) {
        console.log("[Inventory] item= $item, quantity= $quantity")
    }

    Div(
        attrs = {
            classes(InventoryStyles.inventoryBox)
            onClick { if (!isLoading) onClick() }
            
            // Add cursor style based on loading state
            style {
                property("cursor", if (isLoading) "wait" else "pointer")
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
