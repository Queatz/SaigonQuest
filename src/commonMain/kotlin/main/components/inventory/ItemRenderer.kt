package main.components.inventory

import androidx.compose.runtime.Composable
import models.Item
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
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
    Div(
        attrs = {
            style {
                width(size.px)
                height(size.px)
                display(DisplayStyle.Block)
                backgroundColor(rgba(0, 0, 0, 0.1))
                
                // If we have an item with a file, set up the sprite sheet
                if (item != null && item.file.isNotEmpty()) {
                    property("background-image", "url(${item.file})")
                    property("background-size", "320px 320px") // All files are 320x320 pixel pngs
                    
                    // Calculate position based on assetIndex
                    // assetIndex moves horizontally and wraps vertically
                    val col = item.assetIndex % 10 // 10 columns (320px / 32px = 10)
                    val row = item.assetIndex / 10
                    property("background-position", "-${col * 32}px -${row * 32}px")
                }
                
                // Add border radius for a nicer look
                property("border-radius", "4px")
            }
            
            // Add click handler if provided
            onClick?.let { handler ->
                onClick { handler() }
            }
        }
    ) {}
}
