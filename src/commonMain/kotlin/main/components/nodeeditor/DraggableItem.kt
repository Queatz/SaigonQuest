package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * A draggable item in the left panel.
 * 
 * @param label The display label for the item
 * @param type The type of node this item creates (event, dialogue, action, etc.)
 * @param id The unique identifier for the node type
 */
@Composable
fun DraggableItem(label: String, type: String, id: String) {
    Div(
        attrs = {
            classes(NodeEditorStyles.draggableItem)
            // Add draggable attributes
            attr("draggable", "true")
            attr("data-type", type)
            attr("data-id", id)
            attr("data-label", label)
            
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
        Text(label)
    }
}
