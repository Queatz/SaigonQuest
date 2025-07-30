package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element
import main.utils.getBoundingClientRectSafe
import main.utils.getAttributeSafe

/**
 * A port element in a node.
 * 
 * @param port The port data
 * @param nodeId The ID of the node this port belongs to
 * @param onPortClick Callback when the port is clicked
 * @param onPortDragStart Callback when dragging starts from an output port
 * @param onPortDragMove Callback when dragging moves from an output port
 * @param onPortDragEnd Callback when dragging ends from an output port
 */
@Composable
fun PortElement(
    port: PortData,
    nodeId: String,
    onPortClick: (String, PortData) -> Unit,
    onPortDragStart: (String, PortData, Float, Float) -> Unit = { _, _, _, _ -> },
    onPortDragMove: (Float, Float) -> Unit = { _, _ -> },
    onPortDragEnd: (Float, Float) -> Unit = { _, _ -> }
) {
    val isInput = port.type == PortType.INPUT
    
    // State for tracking drag operation
    var isDragging by remember { mutableStateOf(false) }
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    
    Div(
        attrs = {
            classes(NodeEditorStyles.portContainer)
            classes(if (isInput) NodeEditorStyles.portContainerInput else NodeEditorStyles.portContainerOutput)
        }
    ) {
        // Port connector
        Div(
            attrs = {
                classes(NodeEditorStyles.portConnector)
                classes(NodeEditorStyles.portConnectorHover)
                classes(if (isInput) NodeEditorStyles.portConnectorInput else NodeEditorStyles.portConnectorOutput)
                
                // Add data attributes for connection handling
                attr("data-port-id", port.id)
                attr("data-port-type", port.type.name.lowercase())
                attr("data-port-node", nodeId)
                
                // Add click handler for port
                onClick { event ->
                    event.stopPropagation() // Prevent node selection
                    if (!isDragging) {
                        onPortClick(nodeId, port)
                    }
                }
                
                // Add drag handlers for output ports
                if (!isInput) {
                    onMouseDown { event ->
                        if (event.button.toInt() == 0) { // Left mouse button
                            isDragging = true
                            dragStartX = event.clientX.toFloat()
                            dragStartY = event.clientY.toFloat()
                            
                            // Get the port element's position
                            val rect = event.currentTarget?.getBoundingClientRectSafe()
                            val portX = (rect?.left ?: 0.0).toFloat() + (rect?.width ?: 0.0).toFloat() / 2
                            val portY = (rect?.top ?: 0.0).toFloat() + (rect?.height ?: 0.0).toFloat() / 2
                            
                            onPortDragStart(nodeId, port, portX, portY)
                            event.stopPropagation()
                            event.preventDefault()
                        }
                    }
                    
                    onMouseMove { event ->
                        if (isDragging) {
                            onPortDragMove(event.clientX.toFloat(), event.clientY.toFloat())
                            event.stopPropagation()
                            event.preventDefault()
                        }
                    }
                    
                    onMouseUp { event ->
                        if (isDragging) {
                            isDragging = false
                            onPortDragEnd(event.clientX.toFloat(), event.clientY.toFloat())
                            event.stopPropagation()
                            event.preventDefault()
                        }
                    }
                    
                    // We don't end dragging on mouse leave anymore
                    // This allows the user to drag connections outside the port
                }
            }
        )
        
        // Port label
        Span(
            attrs = {
                classes(NodeEditorStyles.portLabel)
            }
        ) {
            Text(port.name)
        }
    }
}

/**
 * Get the center position of a port element.
 * 
 * @param element The DOM element of the port
 * @return A pair of (x, y) coordinates representing the center of the port
 */
fun getPortCenter(element: Element?): Pair<Float, Float> {
    if (element == null) return Pair(0f, 0f)
    
    val rect = element.getBoundingClientRectSafe() ?: return Pair(0f, 0f)
    val x = (rect.left + rect.width / 2).toFloat()
    val y = (rect.top + rect.height / 2).toFloat()
    
    return Pair(x, y)
}
