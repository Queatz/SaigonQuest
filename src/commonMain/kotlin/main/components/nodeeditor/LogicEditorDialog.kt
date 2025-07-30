package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.theme.NodeEditorStyles
import main.theme.Styles
import models.Object
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.DOMRect
import web.cssom.AtRules.Companion.property
import main.utils.getDocument
import main.utils.getElementFromPoint
import main.utils.getAttributeSafe
import main.utils.getBoundingClientRectSafe
import main.components.nodeeditor.PropertyPanel
import main.components.nodeeditor.NodeEditorHistory
import main.components.nodeeditor.AddNodeCommand
import main.components.nodeeditor.DeleteNodeCommand
import main.components.nodeeditor.UpdateNodeCommand
import main.components.nodeeditor.AddConnectionCommand
import main.components.nodeeditor.DeleteConnectionCommand
import main.components.nodeeditor.MoveNodeCommand

/**
 * A dialog for editing object logic.
 * 
 * @param objectModel The object being edited, or null if creating a new object
 * @param worldId The ID of the world this object belongs to
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when an object is saved (created or updated)
 */
@Composable
fun LogicEditorDialog(
    objectModel: Object? = null,
    worldId: String,
    onClose: () -> Unit,
    onSave: (Object) -> Unit
) {
    // State for form fields
    var title by remember { mutableStateOf(objectModel?.title ?: "") }
    var categories by remember { 
        mutableStateOf(
            objectModel?.categories?.joinToString(", ") ?: ""
        )
    }
    var data by remember { mutableStateOf(objectModel?.data ?: "{}") }
    
    // State for node editor
    var selectedNode by remember { mutableStateOf<String?>(null) }
    var nodes by remember { mutableStateOf(parseNodes(objectModel?.data ?: "{}")) }
    var connections by remember { mutableStateOf(parseConnections(objectModel?.data ?: "{}")) }
    
    // State for canvas zoom and pan
    var zoomLevel by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var isDraggingCanvas by remember { mutableStateOf(false) }
    var isClickingEmptyArea by remember { mutableStateOf(false) } // Track if we're clicking on an empty area
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    
    // Command history for undo/redo
    val history = remember { NodeEditorHistory() }
    
    // State for fullscreen mode
    var isFullscreen by remember { mutableStateOf(false) }
    
    // State for connection creation
    var connectionStartNode by remember { mutableStateOf<String?>(null) }
    var connectionStartPort by remember { mutableStateOf<PortData?>(null) }
    var connectionStartX by remember { mutableStateOf(0f) }
    var connectionStartY by remember { mutableStateOf(0f) }
    var connectionCurrentX by remember { mutableStateOf(0f) }
    var connectionCurrentY by remember { mutableStateOf(0f) }
    var isDraggingConnection by remember { mutableStateOf(false) }
    var potentialTargetPort by remember { mutableStateOf<Pair<String, String>?>(null) } // (nodeId, portId)
    
    // State for connection selection
    var selectedConnection by remember { mutableStateOf<Connection?>(null) }

    // Function to handle node dragging
    val handleNodeDrag = { nodeId: String, newX: Float, newY: Float ->
        // Get the current node
        val currentNode = nodes[nodeId]
        if (currentNode != null) {
            // Ensure the node stays within the canvas bounds (prevent dragging off-screen)
            val boundedX = newX.coerceAtLeast(0f)
            val boundedY = newY.coerceAtLeast(0f)
            
            // Only create a command if the position actually changed
            if (boundedX != currentNode.x || boundedY != currentNode.y) {
                // Create and execute the move node command
                val command = MoveNodeCommand(nodes, nodeId, boundedX, boundedY)
                val updatedNodes = history.executeCommand(command) as Map<String, NodeData>
                
                // Update the nodes map
                nodes = updatedNodes
            }
        }
    }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = objectModel != null
    val dialogTitle = if (isEditing) "Edit Object Logic" else "Create Object"
    
    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Function to toggle fullscreen mode
    fun toggleFullscreen() {
        try {
            val doc = document
            
            if (doc.fullscreenElement == null) {
                // Enter fullscreen mode
                document.documentElement?.requestFullscreen()
                // State will be updated by the fullscreenchange event listener
            } else {
                // Exit fullscreen mode
                doc.exitFullscreen()
                // State will be updated by the fullscreenchange event listener
            }
        } catch (e: dynamic) {
            console.error("Fullscreen API error: ", e)
        }
    }
    
    // Update fullscreen state when fullscreen changes
    DisposableEffect(Unit) {
        val fullscreenChangeListener: (dynamic) -> Unit = {
            isFullscreen = document.fullscreenElement != null
        }
        
        document.addEventListener("fullscreenchange", fullscreenChangeListener)
        
        onDispose {
            document.removeEventListener("fullscreenchange", fullscreenChangeListener)
        }
    }
    
    // Function to handle form submission
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""
        
        // Convert categories string to list
        val categoryList = categories.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // Serialize node editor data to JSON
        val nodeData = serializeNodeData(nodes, connections)
        
        val newObject = if (isEditing) {
            // Update existing object
            objectModel!!.copy(
                title = title,
                categories = categoryList,
                data = nodeData
            )
        } else {
            // Create new object
            Object(
                title = title,
                categories = categoryList,
                data = nodeData,
                worldId = worldId,
                position = models.Position(0f, 0f, 0f),
                rotation = Rotation(0f, 0f, 0f),
                scale = Scale(1f, 1f, 1f)
            )
        }
        
        coroutineScope.launch {
            try {
                val savedObject = if (isEditing) {
                    ObjectApi.updateObject(objectModel!!.key, newObject)
                } else {
                    ObjectApi.createObject(newObject)
                }
                
                isSubmitting = false
                onSave(savedObject)
                onClose()
            } catch (error: Exception) {
                isSubmitting = false
                errorMessage = error.message ?: "An error occurred"
            }
        }
    }
    
    // Main dialog container
    Div(
        attrs = {
            classes(Styles.overlayBase)
            // Add click outside to close
            onClick { onClose() }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                // Apply fullscreen or regular dialog content style based on fullscreen state
                classes(if (isFullscreen) NodeEditorStyles.dialogContentFullscreen else NodeEditorStyles.dialogContent)
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            Div(
                attrs = {
                    classes(NodeEditorStyles.dialogHeader)
                }
            ) {
                H3(
                    attrs = {
                        classes(NodeEditorStyles.dialogTitle)
                    }
                ) {
                    Text(dialogTitle)
                }
                
                // Container for buttons to group them together
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            alignItems(AlignItems.Center)
                        }
                    }
                ) {
                    // Fullscreen button
                    Button(
                        attrs = {
                            classes(NodeEditorStyles.closeButton)
                            onClick { toggleFullscreen() }
                        }
                    ) {
                        Text(if (isFullscreen) "⤓" else "⤢")
                    }
                        
                    Button(
                        attrs = {
                            classes(NodeEditorStyles.closeButton)
                            onClick { onClose() }
                        }
                    ) {
                        Text("×")
                    }
                }
            }
            
            // Main content area (flex row with left panel, node editor, and property panel)
            Div(
                attrs = {
                    classes(NodeEditorStyles.mainContentArea)
                }
            ) {
                // Left panel (Events, Dialogue, Actions)
                Div(
                    attrs = {
                        classes(NodeEditorStyles.sidebarContainer)
                    }
                ) {
                    // Basic object info section removed as per requirements
                    
                    // Events section
                    H4(
                        attrs = {
                            classes(NodeEditorStyles.sectionHeader)
                        }
                    ) {
                        Text("Events")
                    }
                    
                    // List of draggable events
                    DraggableItem("Visitor nearby", "event", "visitor_nearby")
                    DraggableItem("Visitor interact", "event", "visitor_interact")
                    DraggableItem("Player nearby", "event", "player_nearby")
                    DraggableItem("Player interact", "event", "player_interact")
                    DraggableItem("Local time", "event", "local_time")
                    
                    // Dialogue section
                    H4(
                        attrs = {
                            classes(NodeEditorStyles.sectionHeader)
                        }
                    ) {
                        Text("Dialogue")
                    }
                    
                    // List of draggable dialogue options
                    DraggableItem("Present text", "dialogue", "present_text")
                    DraggableItem("Show buttons", "dialogue", "show_buttons")
                    
                    // Actions section
                    H4(
                        attrs = {
                            classes(NodeEditorStyles.sectionHeader)
                        }
                    ) {
                        Text("Actions")
                    }
                    
                    // List of draggable actions
                    DraggableItem("Teleport player", "action", "teleport")
                    DraggableItem("Play sound", "action", "play_sound")
                    DraggableItem("Show effect", "action", "show_effect")
                    
                    // Inputs section
                    H4(
                        attrs = {
                            classes(NodeEditorStyles.sectionHeader)
                        }
                    ) {
                        Text("Inputs")
                    }
                    
                    // List of draggable inputs
                    DraggableItem("Player info", "input", "player_info")
                    DraggableItem("Visitor info", "input", "visitor_info")
                    DraggableItem("World info", "input", "world_info")
                    DraggableItem("Object info", "input", "object_info")
                }
                
                // Function to add a new node
                val addNode = { type: String, nodeId: String, label: String, x: Float, y: Float ->
                    // Create a new node
                    val (id, newNode) = createNode(type, nodeId, label, x, y)
                    
                    // Create and execute the add node command
                    val command = AddNodeCommand(nodes, id, newNode)
                    val updatedNodes = history.executeCommand(command) as Map<String, NodeData>
                    
                    // Update the nodes map
                    nodes = updatedNodes
                    
                    // Select the new node
                    selectedNode = id
                }
                
                // Function to delete a node and its connections
                val deleteNode = { nodeId: String ->
                    if (nodeId == selectedNode) {
                        selectedNode = null
                    }
                    
                    // Create and execute the delete node command
                    val command = DeleteNodeCommand(nodes, connections, nodeId)
                    val result = history.executeCommand(command) as Pair<Map<String, NodeData>, List<Connection>>
                    
                    // Update the nodes and connections
                    nodes = result.first
                    connections = result.second
                }
                
                // Helper function to convert screen coordinates to canvas coordinates
                val screenToCanvasCoordinates = { screenX: Float, screenY: Float ->
                    // Get the canvas element's bounding rect
                    val canvasElement = getDocument().getElementById("nodeEditorCanvas")
                    val rect = canvasElement?.getBoundingClientRectSafe()
                    
                    if (rect != null) {
                        // Calculate the position relative to the canvas
                        val relativeX = screenX - rect.left.toFloat()
                        val relativeY = screenY - rect.top.toFloat()
                        
                        // Apply inverse of the zoom and pan transformations
                        val canvasX = relativeX / zoomLevel - panX
                        val canvasY = relativeY / zoomLevel - panY
                        
                        Pair(canvasX, canvasY)
                    } else {
                        // Fallback if we can't get the canvas rect
                        Pair(screenX, screenY)
                    }
                }
                
                // Helper function to convert canvas coordinates to screen coordinates
                val canvasToScreenCoordinates = { canvasX: Float, canvasY: Float ->
                    // Get the canvas element's bounding rect
                    val canvasElement = getDocument().getElementById("nodeEditorCanvas")
                    val rect = canvasElement?.getBoundingClientRectSafe()
                    
                    if (rect != null) {
                        // Apply zoom and pan transformations
                        val relativeX = (canvasX + panX) * zoomLevel
                        val relativeY = (canvasY + panY) * zoomLevel
                        
                        // Calculate the position relative to the screen
                        val screenX = relativeX + rect.left.toFloat()
                        val screenY = relativeY + rect.top.toFloat()
                        
                        Pair(screenX, screenY)
                    } else {
                        // Fallback if we can't get the canvas rect
                        Pair(canvasX, canvasY)
                    }
                }
                
                // Function to handle port drag start
                val handlePortDragStart = { nodeId: String, port: PortData, portX: Float, portY: Float ->
                    connectionStartNode = nodeId
                    connectionStartPort = port
    
                    // Convert the port position from screen coordinates to canvas coordinates
                    val (canvasX, canvasY) = screenToCanvasCoordinates(portX, portY)
                    
                    // Store both the original port position and the current mouse position
                    // in canvas coordinates to ensure proper alignment with the transformed canvas
                    connectionStartX = canvasX
                    connectionStartY = canvasY
                    connectionCurrentX = canvasX
                    connectionCurrentY = canvasY
                    isDraggingConnection = true
    
                    // Connection drag started from an output port
                }
                
                // Function to handle port drag move
                val handlePortDragMove = { x: Float, y: Float ->
                    // Convert the mouse position from screen coordinates to canvas coordinates
                    val (canvasX, canvasY) = screenToCanvasCoordinates(x, y)
                    
                    // Update the current connection position with the canvas coordinates
                    // This ensures the connection preview aligns properly with the transformed canvas
                    connectionCurrentX = canvasX
                    connectionCurrentY = canvasY
    
                    // Find if there's a potential target port at the current position
                    // Use the same approach as in handlePortDragEnd but just for detection
                    val searchRadius = 15 // pixels
                    val searchPoints = listOf(
                        Pair(x, y),                           // Center
                        Pair(x - searchRadius, y),            // Left
                        Pair(x + searchRadius, y),            // Right
                        Pair(x, y - searchRadius),            // Top
                        Pair(x, y + searchRadius),            // Bottom
                        Pair(x - searchRadius, y - searchRadius), // Top-left
                        Pair(x + searchRadius, y - searchRadius), // Top-right
                        Pair(x - searchRadius, y + searchRadius), // Bottom-left
                        Pair(x + searchRadius, y + searchRadius)  // Bottom-right
                    )
                    
                    var portElement: org.w3c.dom.Element? = null
                    
                    // Try each point until we find a port element
                    for ((pointX, pointY) in searchPoints) {
                        // Get the element at this point
                        val element = getDocument().getElementFromPoint(pointX, pointY)
                        if (element == null) continue
                        
                        // Check if the element is a port
                        var currentElement = element
                        var attempts = 0
                        
                        // Try to find a port element by checking the current element and its parents
                        while (currentElement != null && attempts < 5) {
                            val portType = currentElement.getAttributeSafe("data-port-type")
                            if (portType != null) {
                                // Found a port element
                                portElement = currentElement
                                break
                            }
                            
                            // Check if any of the children are port elements
                            val children = currentElement.children
                            var foundInChildren = false
                            for (i in 0 until children.length) {
                                val child = children.item(i)
                                if (child != null && child is org.w3c.dom.Element) {
                                    val childPortType = child.getAttributeSafe("data-port-type")
                                    if (childPortType != null) {
                                        portElement = child
                                        foundInChildren = true
                                        break
                                    }
                                }
                            }
                            
                            if (foundInChildren) {
                                break
                            }
                            
                            // Try the parent element
                            currentElement = currentElement.parentElement
                            attempts++
                        }
                        
                        // If we found a port element, stop searching
                        if (portElement != null) {
                            break
                        }
                    }
                    
                    // Check if we found a valid target port
                    if (portElement != null) {
                        val portType = portElement.getAttributeSafe("data-port-type")
                        val portId = portElement.getAttributeSafe("data-port-id")
                        val portNodeId = portElement.getAttributeSafe("data-port-node")
                        
                        // If it's an input port and not from the same node as the source
                        if (portType == PortType.INPUT.name.lowercase() && 
                            portNodeId != null && 
                            portId != null && 
                            portNodeId != connectionStartNode) {
                            // Update potential target port
                            potentialTargetPort = Pair(portNodeId, portId)
                        } else {
                            // Not a valid target
                            potentialTargetPort = null
                        }
                    } else {
                        // No port element found
                        potentialTargetPort = null
                    }
                }
                
                // Function to handle port drag end
                val handlePortDragEnd = { x: Float, y: Float ->
                    // Convert the mouse position from screen coordinates to canvas coordinates
                    // Note: For port detection, we still use screen coordinates since we're using DOM methods
                    // that work with screen coordinates (getElementFromPoint)
    
                    // Find if there's an input port at the current position
                    // Use a more robust approach to find the port element
    
                    // Try multiple points around the cursor to increase the hit area
                    val searchRadius = 25 // pixels (increased for better detection)
                    val searchPoints = listOf(
                        Pair(x, y),                           // Center
                        Pair(x - searchRadius, y),            // Left
                        Pair(x + searchRadius, y),            // Right
                        Pair(x, y - searchRadius),            // Top
                        Pair(x, y + searchRadius),            // Bottom
                        Pair(x - searchRadius, y - searchRadius), // Top-left
                        Pair(x + searchRadius, y - searchRadius), // Top-right
                        Pair(x - searchRadius, y + searchRadius), // Bottom-left
                        Pair(x + searchRadius, y + searchRadius)  // Bottom-right
                    )
                    
                    var portElement: org.w3c.dom.Element? = null
                    
                    // Try each point until we find a port element
                    for ((pointX, pointY) in searchPoints) {
                        // Get the element at this point
                        val element = getDocument().getElementFromPoint(pointX, pointY)
                        if (element == null) continue
                        
                        // Check if the element is a port
                        var currentElement = element
                        var attempts = 0
                        
                        // Try to find a port element by checking the current element and its parents
                        while (currentElement != null && attempts < 5) {
                            val portType = currentElement.getAttributeSafe("data-port-type")
                            if (portType != null) {
                                // Found a port element
                                portElement = currentElement
                                break
                            }
                            
                            // Check if any of the children are port elements
                            val children = currentElement.children
                            var foundInChildren = false
                            for (i in 0 until children.length) {
                                val child = children.item(i)
                                if (child != null && child is org.w3c.dom.Element) {
                                    val childPortType = child.getAttributeSafe("data-port-type")
                                    if (childPortType != null) {
                                        portElement = child
                                        foundInChildren = true
                                        break
                                    }
                                }
                            }
                            
                            if (foundInChildren) {
                                break
                            }
                            
                            // Try the parent element
                            currentElement = currentElement.parentElement
                            attempts++
                        }
                        
                        // If we found a port element, stop searching
                        if (portElement != null) {
                            break
                        }
                    }
                    
                    // Check if we found a port element
                    if (portElement != null) {
                        val portType = portElement.getAttributeSafe("data-port-type")
                        val portId = portElement.getAttributeSafe("data-port-id")
                        val portNodeId = portElement.getAttributeSafe("data-port-node")
                        
                        // If it's an input port and not from the same node as the source
                        if (portType == PortType.INPUT.name.lowercase() && portNodeId != null && portId != null && portNodeId != connectionStartNode) {
                            // Found a valid target port for connection
                            
                            // Create a new connection
                            val newConnection = Connection(
                                sourceId = connectionStartNode!!,
                                sourcePortId = connectionStartPort!!.id,
                                targetId = portNodeId,
                                targetPortId = portId
                            )
                            
                            // Create and execute the add connection command
                            val command = AddConnectionCommand(connections, newConnection)
                            val updatedConnections = history.executeCommand(command) as List<Connection>
                            
                            // Update the connections list
                            connections = updatedConnections
                            
                        } else {
                            // Not a valid target port (either not an input port or from the same node)
                        }
                    } else {
                        // No port element found at the current position
                    }
                    
                    // Reset the connection state
                    isDraggingConnection = false
                    connectionStartNode = null
                    connectionStartPort = null
                }
                
                // Function to handle port clicks for creating connections
                val handlePortClick = { nodeId: String, port: PortData ->
                    if (connectionStartNode == null || connectionStartPort == null) {
                        // Start a new connection if it's an output port
                        if (port.type == PortType.OUTPUT) {
                            connectionStartNode = nodeId
                            connectionStartPort = port
                        }
                    } else {
                        // Complete the connection if it's an input port and not the same node
                        if (port.type == PortType.INPUT && nodeId != connectionStartNode) {
                            // Create a new connection
                            val newConnection = Connection(
                                sourceId = connectionStartNode!!,
                                sourcePortId = connectionStartPort!!.id,
                                targetId = nodeId,
                                targetPortId = port.id
                            )
                            
                            // Add the connection to the list
                            connections = connections + newConnection
                            
                            // Reset the connection state
                            connectionStartNode = null
                            connectionStartPort = null
                        } else {
                            // Cancel the connection if clicking on an output port again
                            connectionStartNode = null
                            connectionStartPort = null
                        }
                    }
                }
                
                // Node editor canvas wrapper
                Div(
                    attrs = {
                        classes(NodeEditorStyles.nodeEditorCanvasWrapper)
                        
                        // Add keyboard event listener for delete key
                        attr("tabindex", "0") // Make div focusable to receive keyboard events
                        
                        onKeyDown { event ->
                            // Check if delete key is pressed
                            if (event.key == "Delete" || event.key == "Backspace") {
                                // If a node is selected, delete it
                                if (selectedNode != null) {
                                    deleteNode(selectedNode!!)
                                    event.preventDefault()
                                }
                                // If a connection is selected, delete it
                                else if (selectedConnection != null) {
                                    // Create and execute the delete connection command
                                    val command = DeleteConnectionCommand(connections, selectedConnection!!)
                                    val updatedConnections = history.executeCommand(command) as List<Connection>
                                    
                                    // Update the connections list
                                    connections = updatedConnections
                                    
                                    // Clear the selection
                                    selectedConnection = null
                                    event.preventDefault()
                                }
                            }
                            // Check for undo (Ctrl+Z)
                            else if (event.key.lowercase() == "z" && event.ctrlKey && !event.shiftKey) {
                                if (history.canUndo()) {
                                    val result = history.undo()
                                    
                                    // Update state based on the result type
                                    when (result) {
                                        is Map<*, *> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            nodes = result as Map<String, NodeData>
                                        }
                                        is List<*> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            connections = result as List<Connection>
                                        }
                                        is Pair<*, *> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            val pair = result as Pair<Map<String, NodeData>, List<Connection>>
                                            nodes = pair.first
                                            connections = pair.second
                                        }
                                    }
                                    
                                    event.preventDefault()
                                }
                            }
                            // Check for redo (Ctrl+Y or Ctrl+Shift+Z)
                            else if ((event.key.lowercase() == "y" && event.ctrlKey) || 
                                     (event.key.lowercase() == "z" && event.ctrlKey && event.shiftKey)) {
                                if (history.canRedo()) {
                                    val result = history.redo()
                                    
                                    // Update state based on the result type
                                    when (result) {
                                        is Map<*, *> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            nodes = result as Map<String, NodeData>
                                        }
                                        is List<*> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            connections = result as List<Connection>
                                        }
                                        is Pair<*, *> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            val pair = result as Pair<Map<String, NodeData>, List<Connection>>
                                            nodes = pair.first
                                            connections = pair.second
                                        }
                                    }
                                    
                                    event.preventDefault()
                                }
                            }
                        }
                        
                        // Add mouse wheel event for zooming
                        onWheel { event ->
                            // Prevent default behavior (page scrolling)
                            event.preventDefault()
                            
                            // Calculate zoom delta based on wheel movement
                            val delta = if (event.deltaY < 0) 0.1f else -0.1f
                            
                            // Update zoom level with constraints
                            val newZoom = (zoomLevel + delta).coerceIn(0.5f, 2.0f)
                            
                            // Only update if zoom level changed
                            if (newZoom != zoomLevel) {
                                // Get the canvas element's bounding rect
                                val canvasElement = getDocument().getElementById("nodeEditorCanvas")
                                val rect = canvasElement?.getBoundingClientRectSafe()
                                
                                if (rect != null) {
                                    // Calculate the mouse position relative to the canvas
                                    val mouseX = event.clientX - rect.left
                                    val mouseY = event.clientY - rect.top
                                    
                                    // Convert to canvas coordinates before zooming
                                    val canvasX = mouseX / zoomLevel - panX
                                    val canvasY = mouseY / zoomLevel - panY
                                    
                                    // Calculate how this position would change after zooming
                                    val newCanvasX = mouseX / newZoom - panX
                                    val newCanvasY = mouseY / newZoom - panY
                                    
                                    // Adjust pan to keep the point under the cursor in the same position
                                    panX += (newCanvasX - canvasX).toFloat()
                                    panY += (newCanvasY - canvasY).toFloat()
                                    
                                    // Update zoom level
                                    zoomLevel = newZoom
                                } else {
                                    // Fallback if we can't get the canvas rect
                                    zoomLevel = newZoom
                                }
                            }
                        }
                        
                        // Add drop event handlers
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
                                    
                                    // Calculate position relative to the canvas, accounting for zoom and pan
                                    val rect = getDocument().getElementById("nodeEditorCanvas")?.getBoundingClientRectSafe()
                                    val x = ((event.clientX - (rect?.left ?: 0.0)).toFloat() / zoomLevel) - panX
                                    val y = ((event.clientY - (rect?.top ?: 0.0)).toFloat() / zoomLevel) - panY
                                    
                                    // Add the node
                                    addNode(type, id, label, x, y)
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
                                property("transform", "scale($zoomLevel) translate(${panX}px, ${panY}px)")
                            }
                            
                            // Global mouse event handlers for connection dragging
                            // These handlers ensure that the connection drag operation continues even when
                            // the mouse leaves the output port element. This allows users to create connections
                            // by dragging from an output port to an input port without having to keep the mouse
                            // over the output port during the entire drag operation.
                            
                            // Track mouse movement globally for connection dragging
                            onMouseMove { event ->
                                if (isDraggingConnection) {
                                    handlePortDragMove(event.clientX.toFloat(), event.clientY.toFloat())
                                    event.stopPropagation()
                                    event.preventDefault()
                                }
                            }
                            
                            // Handle mouse up globally for connection creation
                            onMouseUp { event ->
                                if (isDraggingConnection) {
                                    // Call handlePortDragEnd which will handle the connection creation
                                    // and reset the connection state internally
                                    handlePortDragEnd(event.clientX.toFloat(), event.clientY.toFloat())
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
                                    isDraggingCanvas = true
                                    isClickingEmptyArea = isEmptyArea
                                    dragStartX = event.clientX.toFloat()
                                    dragStartY = event.clientY.toFloat()
                                    event.preventDefault()
                                    
                                    // If it's the left mouse button on an empty area, stop propagation
                                    // to prevent other handlers from processing this event
                                    if (event.button.toInt() == 0 && isEmptyArea) {
                                        event.stopPropagation()
                                    }
                                }
                            }
                            
                            onMouseMove { event ->
                                if (isDraggingCanvas) {
                                    // Calculate the delta movement
                                    val deltaX = (event.clientX.toFloat() - dragStartX) / zoomLevel
                                    val deltaY = (event.clientY.toFloat() - dragStartY) / zoomLevel
                                    
                                    // Update pan position
                                    panX += deltaX
                                    panY += deltaY
                                    
                                    // Update drag start position
                                    dragStartX = event.clientX.toFloat()
                                    dragStartY = event.clientY.toFloat()
                                    
                                    event.preventDefault()
                                }
                            }
                            
                            onMouseUp { event ->
                                if (isDraggingCanvas) {
                                    isDraggingCanvas = false
                                    isClickingEmptyArea = false
                                    event.preventDefault()
                                }
                            }
                            
                            onMouseLeave { event ->
                                if (isDraggingCanvas) {
                                    isDraggingCanvas = false
                                }
                            }
                        }
                    ) {
                        // Canvas background - only show when empty
                        if (nodes.isEmpty()) {
                            Div(
                                attrs = {
                                    classes(NodeEditorStyles.emptyCanvasMessage)
                                }
                            ) {
                                Text("Drag items from the left panel to create nodes")
                            }
                        }
                        
                        // Render existing nodes
                        nodes.forEach { (id, node) ->
                            NodeElement(
                                id = id,
                                node = node,
                                isSelected = id == selectedNode,
                                onSelect = { selectedNode = id },
                                onEdit = { /* Open node edit dialog */ },
                                onPortClick = handlePortClick,
                                onPortDragStart = handlePortDragStart,
                                onPortDragMove = handlePortDragMove,
                                onPortDragEnd = handlePortDragEnd,
                                onDrag = handleNodeDrag,
                                zoomLevel = zoomLevel
                            )
                            
                        }
                        
                        // Render connections between nodes
                        connections.forEach { connection ->
                            // Find the source and target nodes and ports
                            val sourceNode = nodes[connection.sourceId]
                            val targetNode = nodes[connection.targetId]
                            
                            if (sourceNode != null && targetNode != null) {
                                // Find the source and target ports
                                val sourcePort = sourceNode.outputs.find { it.id == connection.sourcePortId }
                                val targetPort = targetNode.inputs.find { it.id == connection.targetPortId }
                                
                                if (sourcePort != null && targetPort != null) {
                                    // Get the port elements by their data attributes
                                    val sourcePortElement = getDocument().querySelector("[data-port-node='${connection.sourceId}'][data-port-id='${connection.sourcePortId}']")
                                    val targetPortElement = getDocument().querySelector("[data-port-node='${connection.targetId}'][data-port-id='${connection.targetPortId}']")
                                    
                                    // Get the port positions
                                    val (sourceX, sourceY) = if (sourcePortElement != null) {
                                        // Get the center position of the port element
                                        val rect = sourcePortElement.getBoundingClientRectSafe()
                                        if (rect != null) {
                                            // Convert screen coordinates to canvas coordinates
                                            val screenX = (rect.left + rect.width / 2).toFloat()
                                            val screenY = (rect.top + rect.height / 2).toFloat()
                                            val (canvasX, canvasY) = screenToCanvasCoordinates(screenX, screenY)
                                            Pair(canvasX, canvasY)
                                        } else {
                                            // Fallback to the old calculation if we can't get the port element
                                            val sourcePortIndex = sourceNode.outputs.indexOf(sourcePort)
                                            val sourcePortOffset = 50 + (sourcePortIndex * 30)
                                            Pair(sourceNode.x + 180, sourceNode.y + sourcePortOffset)
                                        }
                                    } else {
                                        // Fallback to the old calculation if we can't get the port element
                                        val sourcePortIndex = sourceNode.outputs.indexOf(sourcePort)
                                        val sourcePortOffset = 50 + (sourcePortIndex * 30)
                                        Pair(sourceNode.x + 180, sourceNode.y + sourcePortOffset)
                                    }
                                    
                                    val (targetX, targetY) = if (targetPortElement != null) {
                                        // Get the center position of the port element
                                        val rect = targetPortElement.getBoundingClientRectSafe()
                                        if (rect != null) {
                                            // Convert screen coordinates to canvas coordinates
                                            val screenX = (rect.left + rect.width / 2).toFloat()
                                            val screenY = (rect.top + rect.height / 2).toFloat()
                                            val (canvasX, canvasY) = screenToCanvasCoordinates(screenX, screenY)
                                            Pair(canvasX, canvasY)
                                        } else {
                                            // Fallback to the old calculation if we can't get the port element
                                            val targetPortIndex = targetNode.inputs.indexOf(targetPort)
                                            val targetPortOffset = 50 + (targetPortIndex * 30)
                                            Pair(targetNode.x, targetNode.y + targetPortOffset)
                                        }
                                    } else {
                                        // Fallback to the old calculation if we can't get the port element
                                        val targetPortIndex = targetNode.inputs.indexOf(targetPort)
                                        val targetPortOffset = 50 + (targetPortIndex * 30)
                                        Pair(targetNode.x, targetNode.y + targetPortOffset)
                                    }
                                    
                                    // Create a unique identifier for this connection
                                    val connectionId = "${connection.sourceId}:${connection.sourcePortId}-${connection.targetId}:${connection.targetPortId}"
                                    
                                    // Check if this connection is selected
                                    val isSelected = selectedConnection == connection
                                    
                                    // Render the connection using a bezier curve
                                    BezierConnection(
                                        sourceX = sourceX,
                                        sourceY = sourceY,
                                        targetX = targetX,
                                        targetY = targetY,
                                        isSelected = isSelected,
                                        onClick = { selectedConnection = connection },
                                        connectionId = connectionId
                                    )
                                }
                            }
                        }
                        
                        // Connection preview is now handled by ConnectionHandler component
                        // This prevents duplicate rendering of the preview when dragging connections
                    }
                }
                
                // Property panel (shown when a node is selected)
                if (selectedNode != null) {
                    val node = nodes[selectedNode]
                    if (node != null) {
                        // Function to update node properties
                        val handleNodeUpdate = { nodeId: String, updatedNode: NodeData ->
                            // Update the node in the nodes map
                            nodes = nodes + (nodeId to updatedNode)
                        }
                        
                        // Render the property panel
                        PropertyPanel(
                            nodeId = selectedNode!!,
                            node = node,
                            onNodeUpdate = handleNodeUpdate
                        )
                    }
                }
            }
            
            // Footer with action buttons
            Div(
                attrs = {
                    classes(NodeEditorStyles.dialogFooter)
                }
            ) {
                // Error message
                if (errorMessage.isNotEmpty()) {
                    Span(
                        attrs = {
                            classes(NodeEditorStyles.errorMessage)
                        }
                    ) {
                        Text(errorMessage)
                    }
                }
                
                // Cancel button
                Button(
                    attrs = {
                        classes(NodeEditorStyles.cancelButton)
                        onClick { onClose() }
                    }
                ) {
                    Text("Cancel")
                }
                
                // Save button
                Button(
                    attrs = {
                        classes(NodeEditorStyles.saveButton)
                        classes(if (isSubmitting) NodeEditorStyles.saveButtonDisabled else NodeEditorStyles.saveButtonEnabled)
                        // Only allow clicking if not submitting
                        onClick { if (!isSubmitting) handleSubmit() }
                    }
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save")
                }
            }
        }
    }
}
