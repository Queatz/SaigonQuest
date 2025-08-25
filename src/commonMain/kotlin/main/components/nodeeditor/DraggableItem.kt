package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.components.attrs.touchDraggable
import main.theme.NodeEditorStyles
import main.utils.DataType
import models.LogicNode
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
 * @param node The LogicNode that this item represents
 */
@Composable
fun DraggableItem(node: LogicNode) {
    val label = node.label
    val id = node.id
    val description = node.description
    
    Div(
        attrs = {
            classes(NodeEditorStyles.draggableItem)
            // Add draggable attributes
            attr("draggable", "true")
            attr("data-type", node.category.name)
            attr("data-id", id.name)
            attr("data-label", label)
            attr("data-description", description)
            
            // Add drag event handlers
            onDragStart { event ->
                // Set the drag data
                event.dataTransfer?.setData("text/plain", "${node.category.name}:${id.name}:$label")
                // Provide a data type hint for robust drops
                event.dataTransfer?.setData("application/x-data-type", DataType.OBJECT.name)
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

            // Touch drag support for mobile
            touchDraggable(
                dataProvider = { "${node.category.name}:${id.name}:$label" },
                dataType = DataType.OBJECT,
                previewBuilder = { preview ->
                    preview.textContent = label
                    preview.style.backgroundColor = "rgba(0, 0, 0, 0.6)"
                    preview.style.color = "white"
                    preview.style.padding = "4px 6px"
                    preview.style.borderRadius = "4px"
                    preview.style.fontSize = "12px"
                },
            )
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

