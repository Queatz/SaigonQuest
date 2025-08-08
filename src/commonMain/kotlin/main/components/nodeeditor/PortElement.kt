package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.NodeEditorStyles
import main.utils.getBoundingClientRectSafe
import models.nodeeditor.PortData
import models.nodeeditor.PortType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

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
    onPortDragEnd: (Float, Float) -> Unit = { _, _ -> },
    onPortElementCreated: ((HTMLElement) -> Unit)? = null,
    onPortElementDisposed: ((HTMLElement) -> Unit)? = null,
) {
    val isInput = port.type == PortType.INPUT
    
    // State for tracking drag operation
    var isDragging by remember { mutableStateOf(false) }
    
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
                
                // Get a reference to the port element
                ref { element ->
                    println("[NODE_CONNECTIONS] Port element created: ${nodeId}:${port.id} (${port.type.name})")
                    // Notify the parent component that the port element has been created
                    onPortElementCreated?.invoke(element)
                    
                    onDispose {
                        println("[NODE_CONNECTIONS] Port element disposed: ${nodeId}:${port.id} (${port.type.name})")
                        onPortElementDisposed?.invoke(element)
                    }
                }
                
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
