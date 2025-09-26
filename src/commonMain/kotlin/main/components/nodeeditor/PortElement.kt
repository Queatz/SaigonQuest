package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import main.theme.NodeEditorStyles
import main.utils.getBoundingClientRectSafe
import models.nodeeditor.PortData
import models.nodeeditor.PortType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.Touch
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event

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

                    // Touch support for starting a connection from output ports
                    var onWindowTouchMove: ((Event) -> Unit)? = null
                    var onWindowTouchEnd: ((Event) -> Unit)? = null

                    val touchStartHandler: (Event) -> Unit = { ev ->
                        val te = ev as? TouchEvent
                        if (!isInput && te != null && te.touches.length == 1) {
                            val t: Touch = te.touches.item(0)!!
                            isDragging = true

                            // Use the center of the connector as the start position
                            val rect = (ev.currentTarget as? HTMLElement)?.getBoundingClientRectSafe()
                                ?: element.getBoundingClientRectSafe()
                            val portX = (rect.left + rect.width / 2.0).toFloat()
                            val portY = (rect.top + rect.height / 2.0).toFloat()

                            onPortDragStart(nodeId, port, portX, portY)

                            // Prevent native gestures while dragging
                            (ev.currentTarget as? HTMLElement)?.style?.setProperty("touch-action", "none")
                            (ev.currentTarget as? HTMLElement)?.style?.setProperty("user-select", "none")

                            val move: (Event) -> Unit = { mev ->
                                if (isDragging) {
                                    val mte = mev as TouchEvent
                                    val touch = mte.touches.item(0)
                                    if (touch != null) {
                                        onPortDragMove(touch.clientX.toFloat(), touch.clientY.toFloat())
                                        mev.preventDefault()
                                        mev.stopPropagation()
                                    }
                                }
                            }
                            val ender: (Event) -> Unit = { e2 ->
                                val cte = e2 as TouchEvent
                                val changed = cte.changedTouches.item(0)
                                val endX = changed?.clientX?.toFloat() ?: 0f
                                val endY = changed?.clientY?.toFloat() ?: 0f
                                if (isDragging) {
                                    isDragging = false
                                    onPortDragEnd(endX, endY)
                                    onWindowTouchMove?.let { window.removeEventListener("touchmove", it, false) }
                                    onWindowTouchEnd?.let {
                                        window.removeEventListener("touchend", it, false)
                                        window.removeEventListener("touchcancel", it, false)
                                    }
                                    (ev.currentTarget as? HTMLElement)?.style?.removeProperty("touch-action")
                                    (ev.currentTarget as? HTMLElement)?.style?.removeProperty("user-select")
                                    onWindowTouchMove = null
                                    onWindowTouchEnd = null
                                    e2.preventDefault()
                                    e2.stopPropagation()
                                }
                            }

                            onWindowTouchMove = move
                            onWindowTouchEnd = ender
                            window.addEventListener("touchmove", move, false)
                            window.addEventListener("touchend", ender, false)
                            window.addEventListener("touchcancel", ender, false)

                            ev.preventDefault()
                            ev.stopPropagation()
                        }
                    }

                    element.addEventListener("touchstart", touchStartHandler, false)
                    
                    onDispose {
                        println("[NODE_CONNECTIONS] Port element disposed: ${nodeId}:${port.id} (${port.type.name})")
                        onPortElementDisposed?.invoke(element)
                        element.removeEventListener("touchstart", touchStartHandler, false)
                        onWindowTouchMove?.let { window.removeEventListener("touchmove", it, false) }
                        onWindowTouchEnd?.let {
                            window.removeEventListener("touchend", it, false)
                            window.removeEventListener("touchcancel", it, false)
                        }
                        onWindowTouchMove = null
                        onWindowTouchEnd = null
                        (element as? HTMLElement)?.style?.removeProperty("touch-action")
                        (element as? HTMLElement)?.style?.removeProperty("user-select")
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
