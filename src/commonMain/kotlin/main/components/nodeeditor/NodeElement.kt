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
import models.toTypeString
import org.w3c.dom.HTMLElement
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

/**
 * A node element in the node editor.
 * 
 * @param id The unique identifier of the node
 * @param node The node data
 * @param isSelected Whether the node is currently selected
 * @param onSelect Callback when the node is selected
 * @param onEdit Callback when the node is edited
 * @param onPortClick Callback when a port on the node is clicked
 * @param onPortDragStart Callback when dragging starts from an output port
 * @param onPortDragMove Callback when dragging moves from an output port
 * @param onPortDragEnd Callback when dragging ends from an output port
 * @param onDrag Callback when the node is dragged to a new position
 */
@Composable
fun NodeElement(
    id: String,
    node: NodeData,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onPortClick: (String, PortData) -> Unit,
    onPortDragStart: (String, PortData, Float, Float) -> Unit = { _, _, _, _ -> },
    onPortDragMove: (Float, Float) -> Unit = { _, _ -> },
    onPortDragEnd: (Float, Float) -> Unit = { _, _ -> },
    onDrag: (String, Float, Float) -> Unit,
    controller: NodeEditorController
) {
    // Use the NodeData properties
    val type = node.type
    val x = node.x
    val y = node.y
    val label = node.label
    val inputs = node.inputs
    val outputs = node.outputs
    
    // State for tracking drag operation
    var isDragging by remember { mutableStateOf(false) }
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    var nodeStartX by remember { mutableStateOf(x) }
    var nodeStartY by remember { mutableStateOf(y) }
    
    // Global event handlers for mouse move and mouse up
    val handleGlobalMouseMove = remember {
        { event: Event ->
            if (isDragging && event is MouseEvent) {
                // Calculate delta (no zoom adjustment needed)
                val deltaX = event.clientX.toFloat() - dragStartX
                val deltaY = event.clientY.toFloat() - dragStartY
                onDrag(id, nodeStartX + deltaX, nodeStartY + deltaY)
                event.stopPropagation()
                event.preventDefault()
            }
        }
    }
    
    val handleGlobalMouseUp = remember {
        { event: Event ->
            if (isDragging && event is MouseEvent) {
                isDragging = false
                event.stopPropagation()
                event.preventDefault()
            }
        }
    }
    
    // Add/remove global event listeners when dragging state changes
    DisposableEffect(isDragging) {
        if (isDragging) {
            // Add global event listeners when dragging starts
            document.addEventListener("mousemove", handleGlobalMouseMove)
            document.addEventListener("mouseup", handleGlobalMouseUp)
        }
        
        // Cleanup function to remove event listeners
        onDispose {
            document.removeEventListener("mousemove", handleGlobalMouseMove)
            document.removeEventListener("mouseup", handleGlobalMouseUp)
        }
    }
    
    // Determine node color class based on type
    val nodeColorClass = when(type.toTypeString()) {
        "event" -> NodeEditorStyles.eventNodeColor
        "dialogue" -> NodeEditorStyles.dialogueNodeColor
        "action" -> NodeEditorStyles.actionNodeColor
        "input" -> NodeEditorStyles.inputNodeColor
        else -> NodeEditorStyles.defaultNodeColor
    }
    
    // Effect to register the node element with the controller
    DisposableEffect(id) {
        // Use a small delay to ensure the DOM element is rendered
        val timeoutId = window.setTimeout({
            // Find the node element by its data attribute
            val nodeElement = document.querySelector("[data-node-id='$id']") as? HTMLElement
            if (nodeElement != null) {
                // Register the node element with the controller
                controller.registerNodeElement(id, nodeElement)
            }
        }, 100) // 100ms delay
        
        onDispose {
            // Unregister the node element when the component is disposed
            controller.unregisterNodeElement(id)
            // Clear the timeout
            window.clearTimeout(timeoutId)
        }
    }
    
    Div(
        attrs = {
            classes(NodeEditorStyles.nodeContainer)
            classes(nodeColorClass)
            classes(if (isSelected) NodeEditorStyles.nodeSelected else NodeEditorStyles.nodeUnselected)
            classes(if (isDragging) NodeEditorStyles.nodeDragging else NodeEditorStyles.nodeStatic)
            
            style {
                left(x.px)
                top(y.px)
            }
            
            onClick { 
                if (!isDragging) {
                    onSelect() 
                }
            }
            
            // Add mouse event handler for starting the drag operation
            onMouseDown { event ->
                if (event.button.toInt() == 0) { // Left mouse button
                    isDragging = true
                    dragStartX = event.clientX.toFloat()
                    dragStartY = event.clientY.toFloat()
                    nodeStartX = x
                    nodeStartY = y
                    event.stopPropagation()
                    event.preventDefault()
                }
            }
            
            // Note: We no longer need onMouseMove and onMouseUp handlers on the node element
            // as we now have global event listeners that handle these events at the document level.
            // This allows the drag operation to continue even when the mouse leaves the node element.
            
            // Add node data attributes
            attr("data-node-id", id)
            attr("data-node-type", type.toTypeString())
        }
    ) {
        // Node header
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeHeader)
            }
        ) {
            Text(label)
        }
        
        // Node content with inputs and outputs
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeContent)
            }
        ) {
            // Input ports
            inputs.forEach { port ->
                PortElement(
                    port = port,
                    nodeId = id,
                    onPortClick = onPortClick
                )
            }
            
            // Divider if both inputs and outputs exist
            if (inputs.isNotEmpty() && outputs.isNotEmpty()) {
                Div(
                    attrs = {
                        classes(NodeEditorStyles.nodeDivider)
                    }
                )
            }
            
            // Output ports
            outputs.forEach { port ->
                PortElement(
                    port = port,
                    nodeId = id,
                    onPortClick = onPortClick,
                    onPortDragStart = onPortDragStart,
                    onPortDragMove = onPortDragMove,
                    onPortDragEnd = onPortDragEnd
                )
            }
        }
    }
}
