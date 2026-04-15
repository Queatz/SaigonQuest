package main.components.inventory

import androidx.compose.runtime.Composable
import models.Item
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

/**
 * A reusable component to display an item's icon from a spritesheet.
 */
@Composable
fun ItemIcon(
    item: Item,
    size: CSSNumeric = 32.px,
    showBackground: Boolean = true,
    attrs: (org.jetbrains.compose.web.attributes.AttrsScope<org.w3c.dom.HTMLDivElement>.() -> Unit)? = null
) {
    Div(attrs = {
        if (attrs != null) attrs()
        
        style {
            width(size)
            height(size)
            if (showBackground) {
                backgroundColor(rgba(255, 255, 255, 0.1))
                borderRadius(4.px)
            }
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            justifyContent(JustifyContent.Center)
            
            if (item.file.isNotEmpty()) {
                val url = "/raw/data/spritesheets/${item.file}.png"
                property("background-image", "url(\"$url\")")
                
                val scale = (size.asDynamic().value as Double) / 32.0
                property("background-size", "${320.0 * scale}px ${320.0 * scale}px")
                
                val col = item.assetIndex % 10
                val row = item.assetIndex / 10
                property("background-position", "-${col * 32.0 * scale}px -${(9 - row) * 32.0 * scale}px")
                property("background-repeat", "no-repeat")
            }
        }
    })
}
