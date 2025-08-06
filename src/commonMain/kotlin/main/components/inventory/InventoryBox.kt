package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * An inventory box that can display an item.
 * Can also show a loading state when items are being loaded.
 */
@Composable
fun InventoryBox(
    onClick: () -> Unit,
    item: Item? = null,
    content: String = "",
    isLoading: Boolean = false
) {
    LaunchedEffect(item) {
        console.log("[Inventory] item= $item")
    }

    Div(
        attrs = {
            classes(Styles.inventoryBox)
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
                    style {
                        width(100.percent)
                        height(100.percent)
                        backgroundColor(rgba(200, 200, 200, 0.5))
                        borderRadius(4.px)
                        position(Position.Relative)
                    }
                }
            ) {
                // Loading dots
                Div(
                    attrs = {
                        style {
                            position(Position.Absolute)
                            property("top", "50%")
                            property("left", "50%")
                            property("transform", "translate(-50%, -50%)")
                            property("font-size", "16px")
                            property("letter-spacing", "2px")
                            property("color", "rgba(80, 80, 80, 1.0)")
                        }
                    }
                ) {
                    Text("...")
                }
            }
        } else if (item != null) {
            // Render the item if we have one
            ItemRenderer(item = item, size = 32)
        } else if (content.isNotEmpty()) {
            // Otherwise show the content text
            Text(content)
        }
    }
}
