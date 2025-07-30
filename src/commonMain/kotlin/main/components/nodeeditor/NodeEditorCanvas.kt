package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import main.utils.getBoundingClientRectSafe
import main.utils.getDocument
import main.utils.getAttributeSafe
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.events.SyntheticKeyboardEvent
import web.cssom.AtRules.Companion.property

/**
 * Canvas component for the Node Editor.
 * Handles rendering of nodes, connections, and interaction with the canvas.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param onKeyDown Callback for keyboard events
 */
@Composable
fun NodeEditorCanvas(
    controller: NodeEditorController,
    onKeyDown: ((SyntheticKeyboardEvent) -> Unit)? = null
) {
    // Canvas wrapper with event handlers
    Div(
        attrs = {
            classes(NodeEditorStyles.nodeEditorCanvasWrapper)
            
            // Add keyboard event listener
            if (onKeyDown != null) {
                attr("tabindex", "0") // Make div focusable to receive keyboard events
                onKeyDown { event -> onKeyDown(event) }
            }
            
            // Add mouse wheel event for zooming
            onWheel { event ->
                // Prevent default behavior (page scrolling)
                event.preventDefault()
                
                // Calculate zoom delta based on wheel movement and current zoom level
                // Make delta proportional to current zoom level for more gradual zooming
                // Use the magnitude of deltaY to make zooming speed proportional to wheel movement
                val wheelMagnitude = kotlin.math.min(kotlin.math.abs(event.deltaY) / 100, 1.0).toFloat()
                val baseSpeed = 0.05f // Smaller base delta for finer control
                val zoomSpeed = baseSpeed * controller.zoomLevel * wheelMagnitude
                
                val delta = if (event.deltaY < 0) zoomSpeed else -zoomSpeed
                
                // Update zoom level with constraints
                controller.zoom(delta, event.clientX.toFloat(), event.clientY.toFloat())
            }
            
            // Add drop event handlers for adding new nodes
            onDragOver { event ->
                // Prevent default to allow drop
                event.preventDefault()
                // Set the drop effect
                event.dataTransfer?.dropEffect = "copy"
            }
            
            onDrop { event ->
                // Prevent default behavior
                event.preventDefault()
                
                // Get the data from the drag operation
                val data = event.dataTransfer?.getData("text/plain") ?: ""
                if (data.isNotEmpty()) {
                    // Parse the data (format: "type:id:label")
                    val parts = data.split(":")
                    if (parts.size >= 3) {
                        val type = parts[0]
                        val id = parts[1]
                        val label = parts[2]
                        
                        // Convert screen coordinates to canvas coordinates
                        val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                            event.clientX.toFloat(),
                            event.clientY.toFloat()
                        )
                        
                        // Add the node
                        controller.addNode(type, id, label, canvasX, canvasY)
                    }
                }
            }
        }
    ) {
        // Node editor canvas
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeEditorCanvas)
                classes(NodeEditorStyles.nodeEditorCanvasZoomed)
                
                // Add a specific ID for the canvas element
                id("nodeEditorCanvas")
                
                // Apply zoom and pan transformations
                style {
                    property("transform", "scale(${controller.zoomLevel}) translate(${controller.panX}px, ${controller.panY}px)")
                }
                
                // Mouse handlers for panning and connection dragging
                onMouseMove { event ->
                    if (controller.isDraggingConnection) {
                        controller.updateConnectionPreview(event.clientX.toFloat(), event.clientY.toFloat())
                        event.stopPropagation()
                        event.preventDefault()
                    }
                }
                
                // Handle mouse up globally for connection creation
                onMouseUp { event ->
                    if (controller.isDraggingConnection) {
                        controller.completeConnection(event.clientX.toFloat(), event.clientY.toFloat())
                        event.stopPropagation()
                        event.preventDefault()
                    }
                }
                
                // Add mouse events for panning (middle mouse button or left mouse button on empty areas)
                onMouseDown { event ->
                    // Check if we're clicking on an empty area (target is the canvas itself)
                    val isEmptyArea = event.target == event.currentTarget
                    
                    // Middle mouse button (button 1) or left mouse button (button 0) on empty area
                    if (event.button.toInt() == 1 || (event.button.toInt() == 0 && isEmptyArea)) {
                        controller.startPanning(event.clientX.toFloat(), event.clientY.toFloat(), isEmptyArea)
                        event.preventDefault()
                        
                        // If it's the left mouse button on an empty area, stop propagation
                        // to prevent other handlers from processing this event
                        if (event.button.toInt() == 0 && isEmptyArea) {
                            event.stopPropagation()
                        }
                    }
                }
                
                onMouseMove { event ->
                    if (controller.isDraggingCanvas) {
                        controller.updatePan(event.clientX.toFloat(), event.clientY.toFloat())
                        event.preventDefault()
                    }
                }
                
                onMouseUp { event ->
                    if (controller.isDraggingCanvas) {
                        controller.stopPanning()
                        event.preventDefault()
                    }
                }
                
                onMouseLeave { event ->
                    if (controller.isDraggingCanvas) {
                        controller.stopPanning()
                    }
                }
            }
        ) {
            // Canvas background - only show when empty
            if (controller.nodes.isEmpty()) {
                Div(
                    attrs = {
                        classes(NodeEditorStyles.emptyCanvasMessage)
                    }
                ) {
                    Text("Drag items from the left panel to create nodes")
                }
            }
            
            // Render nodes using NodeOperations
            NodeOperations(controller)
            
            // Render connections using ConnectionHandler
            ConnectionHandler(controller)
        }
    }
}
