package main.components.nodeeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import main.utils.getBoundingClientRectSafe
import main.utils.getDocument
import main.utils.getElementFromPoint
import main.utils.getAttributeSafe

/**
 * Controller for the Node Editor that centralizes state management and operations.
 * This class handles all state changes and coordinate transformations for the node editor.
 *
 * @param initialNodes Initial map of nodes
 * @param initialConnections Initial list of connections
 */
class NodeEditorController(
    initialNodes: Map<String, NodeData> = emptyMap(),
    initialConnections: List<Connection> = emptyList()
) {
    // Node state
    var nodes by mutableStateOf(initialNodes)
        private set
    var connections by mutableStateOf(initialConnections)
        private set
    var selectedNode by mutableStateOf<String?>(null)
        private set
    var selectedConnection by mutableStateOf<Connection?>(null)
        private set
    
    // Canvas state
    var zoomLevel by mutableStateOf(1f)
        private set
    var panX by mutableStateOf(0f)
        private set
    var panY by mutableStateOf(0f)
        private set
    var isDraggingCanvas by mutableStateOf(false)
        private set
    var isClickingEmptyArea by mutableStateOf(false)
        private set
    var dragStartX by mutableStateOf(0f)
        private set
    var dragStartY by mutableStateOf(0f)
        private set
    
    // Connection state
    var connectionStartNode by mutableStateOf<String?>(null)
        private set
    var connectionStartPort by mutableStateOf<PortData?>(null)
        private set
    var connectionStartX by mutableStateOf(0f)
        private set
    var connectionStartY by mutableStateOf(0f)
        private set
    var connectionCurrentX by mutableStateOf(0f)
        private set
    var connectionCurrentY by mutableStateOf(0f)
        private set
    var isDraggingConnection by mutableStateOf(false)
        private set
    var potentialTargetPort by mutableStateOf<Pair<String, String>?>(null) // (nodeId, portId)
        private set
    
    // Command history for undo/redo
    private val history = NodeEditorHistory()
    
    /**
     * Converts screen coordinates to canvas coordinates.
     * This is used when handling mouse events to convert the mouse position to canvas coordinates.
     *
     * @param screenX X coordinate in screen space
     * @param screenY Y coordinate in screen space
     * @return Pair of X and Y coordinates in canvas space
     */
    fun screenToCanvasCoordinates(screenX: Float, screenY: Float): Pair<Float, Float> {
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
            
            return Pair(canvasX, canvasY)
        } else {
            // Fallback if we can't get the canvas rect
            return Pair(screenX, screenY)
        }
    }
    
    /**
     * Converts canvas coordinates to screen coordinates.
     * This is used when rendering elements to convert canvas positions to screen positions.
     *
     * @param canvasX X coordinate in canvas space
     * @param canvasY Y coordinate in canvas space
     * @return Pair of X and Y coordinates in screen space
     */
    fun canvasToScreenCoordinates(canvasX: Float, canvasY: Float): Pair<Float, Float> {
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
            
            return Pair(screenX, screenY)
        } else {
            // Fallback if we can't get the canvas rect
            return Pair(canvasX, canvasY)
        }
    }
    
    /**
     * Adds a new node to the canvas.
     *
     * @param type The type of node (event, dialogue, action, input)
     * @param nodeId The ID of the node
     * @param label The label for the node
     * @param x The x position of the node
     * @param y The y position of the node
     * @return The ID of the newly created node
     */
    fun addNode(type: String, nodeId: String, label: String, x: Float, y: Float): String {
        // Create a new node
        val (id, newNode) = createNode(type, nodeId, label, x, y)
        
        // Create and execute the add node command
        val command = AddNodeCommand(nodes, id, newNode)
        val updatedNodes = history.executeCommand(command) as Map<String, NodeData>
        
        // Update the nodes map
        nodes = updatedNodes
        
        // Select the new node
        selectedNode = id
        
        return id
    }
    
    /**
     * Deletes a node and its connections.
     *
     * @param nodeId The ID of the node to delete
     */
    fun deleteNode(nodeId: String) {
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
    
    /**
     * Moves a node to a new position.
     *
     * @param nodeId The ID of the node to move
     * @param newX The new x position
     * @param newY The new y position
     */
    fun moveNode(nodeId: String, newX: Float, newY: Float) {
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
    
    /**
     * Updates the properties of a node.
     *
     * @param nodeId The ID of the node to update
     * @param updatedNode The updated node data
     */
    fun updateNode(nodeId: String, updatedNode: NodeData) {
        // Create and execute the update node command
        val command = UpdateNodeCommand(nodes, nodeId, updatedNode)
        val updatedNodes = history.executeCommand(command) as Map<String, NodeData>
        
        // Update the nodes map
        nodes = updatedNodes
    }
    
    /**
     * Selects a node.
     *
     * @param nodeId The ID of the node to select, or null to deselect
     */
    fun selectNode(nodeId: String?) {
        selectedNode = nodeId
        // Deselect any selected connection when selecting a node
        if (nodeId != null) {
            selectedConnection = null
        }
    }
    
    /**
     * Selects a connection.
     *
     * @param connection The connection to select, or null to deselect
     */
    fun selectConnection(connection: Connection?) {
        selectedConnection = connection
        // Deselect any selected node when selecting a connection
        if (connection != null) {
            selectedNode = null
        }
    }
    
    /**
     * Starts a connection from an output port.
     *
     * @param nodeId The ID of the node containing the output port
     * @param port The output port data
     * @param portX The x position of the port in screen coordinates
     * @param portY The y position of the port in screen coordinates
     */
    fun startConnection(nodeId: String, port: PortData, portX: Float, portY: Float) {
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
    }
    
    /**
     * Updates the current position of the connection being dragged.
     *
     * @param screenX The current x position of the mouse in screen coordinates
     * @param screenY The current y position of the mouse in screen coordinates
     */
    fun updateConnectionPreview(screenX: Float, screenY: Float) {
        // Convert the mouse position from screen coordinates to canvas coordinates
        val (canvasX, canvasY) = screenToCanvasCoordinates(screenX, screenY)
        
        // Update the current connection position with the canvas coordinates
        // This ensures the connection preview aligns properly with the transformed canvas
        connectionCurrentX = canvasX
        connectionCurrentY = canvasY

        // Find if there's a potential target port at the current position
        // Use the same approach as in handlePortDragEnd but just for detection
        val searchRadius = 15 // pixels
        val searchPoints = listOf(
            Pair(screenX, screenY),                           // Center
            Pair(screenX - searchRadius, screenY),            // Left
            Pair(screenX + searchRadius, screenY),            // Right
            Pair(screenX, screenY - searchRadius),            // Top
            Pair(screenX, screenY + searchRadius),            // Bottom
            Pair(screenX - searchRadius, screenY - searchRadius), // Top-left
            Pair(screenX + searchRadius, screenY - searchRadius), // Top-right
            Pair(screenX - searchRadius, screenY + searchRadius), // Bottom-left
            Pair(screenX + searchRadius, screenY + searchRadius)  // Bottom-right
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
                potentialTargetPort = Pair(portNodeId.toString(), portId.toString())
            } else {
                // Not a valid target
                potentialTargetPort = null
            }
        } else {
            // No port element found
            potentialTargetPort = null
        }
    }
    
    /**
     * Completes a connection when the mouse is released over an input port.
     *
     * @param screenX The x position of the mouse in screen coordinates
     * @param screenY The y position of the mouse in screen coordinates
     */
    fun completeConnection(screenX: Float, screenY: Float) {
        // Find if there's an input port at the current position
        // Use a more robust approach to find the port element

        // Try multiple points around the cursor to increase the hit area
        val searchRadius = 25 // pixels (increased for better detection)
        val searchPoints = listOf(
            Pair(screenX, screenY),                           // Center
            Pair(screenX - searchRadius, screenY),            // Left
            Pair(screenX + searchRadius, screenY),            // Right
            Pair(screenX, screenY - searchRadius),            // Top
            Pair(screenX, screenY + searchRadius),            // Bottom
            Pair(screenX - searchRadius, screenY - searchRadius), // Top-left
            Pair(screenX + searchRadius, screenY - searchRadius), // Top-right
            Pair(screenX - searchRadius, screenY + searchRadius), // Bottom-left
            Pair(screenX + searchRadius, screenY + searchRadius)  // Bottom-right
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
                    targetId = portNodeId.toString(),
                    targetPortId = portId.toString()
                )
                
                // Create and execute the add connection command
                val command = AddConnectionCommand(connections, newConnection)
                val updatedConnections = history.executeCommand(command) as List<Connection>
                
                // Update the connections list
                connections = updatedConnections
            }
        }
        
        // Reset the connection state
        cancelConnection()
    }
    
    /**
     * Cancels the current connection being created.
     */
    fun cancelConnection() {
        isDraggingConnection = false
        connectionStartNode = null
        connectionStartPort = null
        potentialTargetPort = null
    }
    
    /**
     * Deletes a connection.
     *
     * @param connection The connection to delete
     */
    fun deleteConnection(connection: Connection) {
        // Create and execute the delete connection command
        val command = DeleteConnectionCommand(connections, connection)
        val updatedConnections = history.executeCommand(command) as List<Connection>
        
        // Update the connections list
        connections = updatedConnections
        
        // Clear the selection if this was the selected connection
        if (connection == selectedConnection) {
            selectedConnection = null
        }
    }
    
    /**
     * Handles zooming of the canvas.
     *
     * @param delta The zoom delta (positive for zoom in, negative for zoom out)
     * @param mouseX The x position of the mouse in screen coordinates
     * @param mouseY The y position of the mouse in screen coordinates
     */
    fun zoom(delta: Float, mouseX: Float, mouseY: Float) {
        // Calculate new zoom level with constraints
        val newZoom = (zoomLevel + delta).coerceIn(0.5f, 2.0f)
        
        // Only update if zoom level changed
        if (newZoom != zoomLevel) {
            // Get the canvas element's bounding rect
            val canvasElement = getDocument().getElementById("nodeEditorCanvas")
            val rect = canvasElement?.getBoundingClientRectSafe()
            
            if (rect != null) {
                // Calculate the mouse position relative to the canvas
                val relativeX = mouseX - rect.left
                val relativeY = mouseY - rect.top
                
                // Convert to canvas coordinates before zooming
                val canvasX = relativeX / zoomLevel - panX
                val canvasY = relativeY / zoomLevel - panY
                
                // Calculate how this position would change after zooming
                val newCanvasX = relativeX / newZoom - panX
                val newCanvasY = relativeY / newZoom - panY
                
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
    
    /**
     * Starts panning the canvas.
     *
     * @param screenX The x position of the mouse in screen coordinates
     * @param screenY The y position of the mouse in screen coordinates
     * @param isEmptyArea Whether the mouse is over an empty area of the canvas
     */
    fun startPanning(screenX: Float, screenY: Float, isEmptyArea: Boolean) {
        isDraggingCanvas = true
        isClickingEmptyArea = isEmptyArea
        dragStartX = screenX
        dragStartY = screenY
    }
    
    /**
     * Updates the pan position while dragging.
     *
     * @param screenX The current x position of the mouse in screen coordinates
     * @param screenY The current y position of the mouse in screen coordinates
     */
    fun updatePan(screenX: Float, screenY: Float) {
        if (isDraggingCanvas) {
            // Calculate the delta movement
            val deltaX = (screenX - dragStartX) / zoomLevel
            val deltaY = (screenY - dragStartY) / zoomLevel
            
            // Update pan position
            panX += deltaX
            panY += deltaY
            
            // Update drag start position
            dragStartX = screenX
            dragStartY = screenY
        }
    }
    
    /**
     * Stops panning the canvas.
     */
    fun stopPanning() {
        isDraggingCanvas = false
        isClickingEmptyArea = false
    }
    
    /**
     * Undoes the last action.
     */
    fun undo() {
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
        }
    }
    
    /**
     * Redoes the last undone action.
     */
    fun redo() {
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
        }
    }
    
    /**
     * Serializes the node editor data to JSON.
     *
     * @return JSON string representation of the node editor data
     */
    fun serializeToJson(): String {
        return serializeNodeData(nodes, connections)
    }
}
