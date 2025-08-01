package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * A draggable item in the left panel.
 * 
 * @param label The display label for the item
 * @param type The type of node this item creates (event, dialogue, action, etc.)
 * @param id The unique identifier for the node type
 * @param description A short description of what this item does (optional)
 */
@Composable
fun DraggableItem(label: String, type: String, id: String, description: String = "") {
    Div(
        attrs = {
            classes(NodeEditorStyles.draggableItem)
            // Add draggable attributes
            attr("draggable", "true")
            attr("data-type", type)
            attr("data-id", id)
            attr("data-label", label)
            attr("data-description", description)
            
            // Add drag event handlers
            onDragStart { event ->
                // Set the drag data
                event.dataTransfer?.setData("text/plain", "$type:$id:$label")
                // Set the drag effect
                event.dataTransfer?.effectAllowed = "copy"
            }
            
            onDrag { event ->
                // Prevent default to allow drop
                event.preventDefault()
            }
            
            onDragEnd { event ->
                // Clean up after drag operation
                event.preventDefault()
            }
        }
    ) {
        Div {
            Text(label)
        }
        
        if (description.isNotEmpty()) {
            Div(
                attrs = {
                    style {
                        fontSize(11.px)
                        color(rgba(200, 200, 200, 0.7))
                        marginTop(3.px)
                        property("line-height", "1.3")
                    }
                }
            ) {
                Text(description)
            }
        }
    }
}
