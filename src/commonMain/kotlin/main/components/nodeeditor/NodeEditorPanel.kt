package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.components.Translated
import main.components.attrs.onTouchDrop
import main.theme.NodeEditorStyles
import models.NodeCategory
import models.NodeId
import main.utils.DataType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.events.SyntheticKeyboardEvent

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
    // Register DOM elements with the controller as soon as they mount, no timeout
    // Node editor canvas wrapper
    Div(
        attrs = {
            classes(NodeEditorStyles.nodeEditorCanvasWrapper)
            attr("data-wrapper", "node-editor-canvas-wrapper")
            // Register wrapper element immediately via ref
            ref { element ->
                controller.setWrapperElement(element)

                onDispose { controller.setWrapperElement(null) }
            }
            
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

                // Optional data type validation from MIME hint
                val typeHint = event.dataTransfer?.getData("application/x-data-type") ?: ""
                if (typeHint.isNotEmpty() && typeHint != DataType.OBJECT.name) {
                    // Not an object payload; ignore to avoid errors
                    return@onDrop
                }

                // Get the data from the drag operation
                val data = event.dataTransfer?.getData("text/plain") ?: ""
                if (data.isNotEmpty() && onNodeAdd != null) {
                    // Parse the data (format: "type:id:label"; label may contain colons)
                    val parts = data.split(":")
                    if (parts.size >= 2) {
                        val typeStr = parts[0].trim()
                        val idStr = parts[1].trim()
                        val label = if (parts.size > 2) parts.subList(2, parts.size).joinToString(":") else ""
                        try {
                            val type = NodeCategory.valueOf(typeStr)
                            val id = NodeId.valueOf(idStr)

                            // Convert screen coordinates to canvas coordinates
                            val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                                event.clientX.toFloat(),
                                event.clientY.toFloat()
                            )

                            // Add the node
                            onNodeAdd(type, id, label, canvasX, canvasY)
                        } catch (t: Throwable) {
                            console.error("[NodeEditor] Drop parse error for data='$data'", t)
                        }
                    }
                }
            }

            // Touch drop handler for adding new nodes on mobile
            onTouchDrop { clientX, clientY, data, dataType ->
                // Only accept object payloads
                if (dataType != DataType.OBJECT) return@onTouchDrop

                if (data.isNotEmpty() && onNodeAdd != null) {
                    val parts = data.split(":")
                    if (parts.size >= 2) {
                        val typeStr = parts[0].trim()
                        val idStr = parts[1].trim()
                        val label = if (parts.size > 2) parts.subList(2, parts.size).joinToString(":") else ""

                        try {
                            val type = NodeCategory.valueOf(typeStr)
                            val id = NodeId.valueOf(idStr)

                            val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                                clientX.toFloat(),
                                clientY.toFloat()
                            )

                            onNodeAdd(type, id, label, canvasX, canvasY)
                        } catch (t: Throwable) {
                            console.error("[NodeEditor] Touch drop parse error for data='$data'", t)
                        }
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
                // Register canvas element immediately via ref
                ref { element ->
                    controller.setCanvasElement(element)

                    onDispose { controller.setCanvasElement(null) }
                }
                
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
                    Translated("Drag items from the left panel to create nodes")
                }
            }
            
            // Render nodes
            NodeOperations(controller)
        }
    }
}
