package main.components.inventory

import androidx.compose.runtime.Composable
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * An inventory box that can display an item.
 */
@Composable
fun InventoryBox(
    onClick: () -> Unit,
    item: Item? = null,
    content: String = ""
) {
    Div(
        attrs = {
            classes(Styles.inventoryBox)
            onClick { onClick() }
        }
    ) {
        if (item != null) {
            // Render the item if we have one
            ItemRenderer(item = item, size = 32)
        } else if (content.isNotEmpty()) {
            // Otherwise show the content text
            Text(content)
        }
    }
}
