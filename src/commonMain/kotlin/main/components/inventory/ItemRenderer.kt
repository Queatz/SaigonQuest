package main.components.inventory

import androidx.compose.runtime.Composable
import main.components.IconRenderer
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.dom.Div

/**
 * Renders an item based on its file and assetIndex.
 * 
 * @param item The item to render, or null if no item
 * @param size The size of the rendered item in pixels (default: 32px)
 * @param onClick Optional click handler
 */
@Composable
fun ItemRenderer(
    item: Item?,
    size: Int = 32,
    onClick: (() -> Unit)? = null
) {
    if (item != null && item.file.isNotEmpty()) {
        // If we have an item with a file, use IconRenderer
        IconRenderer(
            url = "/raw/data/spritesheets/${item.file}.png",
            index = item.assetIndex,
            size = size,
            onClick = onClick
        )
    } else {
        // If no item or empty file, just show an empty box
        Div(
            attrs = {
                classes(Styles.iconRenderer)
                style {
                    if (size != 32) {
                        property("width", "${size}px")
                        property("height", "${size}px")
                    }
                }
                
                // Add click handler if provided
                onClick?.let { handler ->
                    onClick { handler() }
                }
            }
        ) {}
    }
}
