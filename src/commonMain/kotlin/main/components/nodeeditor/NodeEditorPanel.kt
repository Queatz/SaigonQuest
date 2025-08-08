package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import main.theme.NodeEditorStyles
import main.utils.getBoundingClientRectSafe
import main.utils.getDocument
import models.NodeCategory
import models.NodeId
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.events.SyntheticKeyboardEvent
import org.w3c.dom.HTMLElement

/**
 * Node editor panel component that contains the canvas for editing nodes and connections.
 * 
 * @param controller The NodeEditorController that manages state and operations
 * @param onKeyDown Callback for keyboard events
 * @param onNodeAdd Callback for adding a new node
 * @param onNodeDelete Callback for deleting a node
 */
@Composable
fun NodeEditorPanel(
    controller: NodeEditorController,
    onKeyDown: ((SyntheticKeyboardEvent) -> Unit)? = null,
    onNodeAdd: ((type: NodeCategory, nodeId: NodeId, label: String, x: Float, y: Float) -> Unit)? = null,
    onNodeDelete: ((nodeId: String) -> Unit)? = null
) {
    // Effect to find and register DOM elements with the controller
    DisposableEffect(Unit) {
        // Use a small delay to ensure the DOM elements are rendered
        val timeoutId = window.setTimeout({
            // Find the canvas element
            val canvasElement = document.querySelector("[data-canvas='node-editor-canvas']") as? HTMLElement
            if (canvasElement != null) {
                controller.setCanvasElement(canvasElement)
            }
            
            // Find the wrapper element
            val wrapperElement = document.querySelector("[data-wrapper='node-editor-canvas-wrapper']") as? HTMLElement
            if (wrapperElement != null) {
                controller.setWrapperElement(wrapperElement)
            }
        }, 100) // 100ms delay
        
        onDispose {
            // Clear the timeout when the component is disposed
            window.clearTimeout(timeoutId)
        }
    }
    // Node editor canvas wrapper
    Div(
        attrs = {
            classes(NodeEditorStyles.nodeEditorCanvasWrapper)
            attr("data-wrapper", "node-editor-canvas-wrapper")
            
            // Add keyboard event listener
            if (onKeyDown != null) {
                attr("tabindex", "0") // Make div focusable to receive keyboard events
                onKeyDown { event -> onKeyDown(event) }
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
                if (data.isNotEmpty() && onNodeAdd != null) {
                    // Parse the data (format: "type:id:label")
                    val parts = data.split(":")
                    if (parts.size >= 3) {
                        val type = NodeCategory.valueOf(parts[0])
                        val id = NodeId.valueOf(parts[1])
                        val label = parts[2]
                        
                        // Convert screen coordinates to canvas coordinates
                        val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                            event.clientX.toFloat(),
                            event.clientY.toFloat()
                        )
                        
                        // Add the node
                        onNodeAdd(type, id, label, canvasX, canvasY)
                    }
                }
            }
            
            // Handle mouse handlers for connection dragging
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
        }
    ) {
        // Node editor canvas
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeEditorCanvas)
                
                // Add a data attribute to identify the canvas element
                attr("data-canvas", "node-editor-canvas")
                
                // Add click handler to deselect nodes and connections when clicking on empty areas
                onClick { event ->
                    // Only deselect if we're not in the middle of a connection drag
                    if (!controller.isDraggingConnection) {
                        controller.selectNode(null)
                        event.stopPropagation()
                    }
                }
            }
        ) {
            // Render connections
            ConnectionHandler(controller)

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
            
            // Render nodes
            NodeOperations(controller)
        }
    }
}
