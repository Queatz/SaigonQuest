package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import main.utils.getBoundingClientRectSafe
import org.w3c.dom.HTMLElement
import web.timers.Timeout
import web.timers.clearTimeout
import web.timers.setTimeout

/**
 * Controller for the Node Editor that centralizes state management and operations.
 * This class handles all state changes and coordinate transformations for the node editor.
 *
 * @param initialNodes Initial map of nodes
 * @param initialConnections Initial list of connections
 * @param canvasElement Reference to the canvas element (optional, can be set later)
 * @param wrapperElement Reference to the wrapper element (optional, can be set later)
 */
class NodeEditorController(
    initialNodes: Map<String, NodeData> = emptyMap(),
    initialConnections: List<Connection> = emptyList(),
    canvasElement: HTMLElement? = null,
    wrapperElement: HTMLElement? = null,
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
    
    // Element references
    var canvasElement: HTMLElement? = canvasElement
        private set
    var wrapperElement: HTMLElement? = wrapperElement
        private set
    private val nodeElements = mutableStateMapOf<String, HTMLElement>()

    private var updateCanvasSizeTimeout: Timeout? = null

    // Connection state properties delegated to ConnectionManager
    val connectionStartNode get() = connectionManager.connectionStartNode
    val connectionStartPort get() = connectionManager.connectionStartPort
    val connectionStartX get() = connectionManager.connectionStartX
    val connectionStartY get() = connectionManager.connectionStartY
    val connectionCurrentX get() = connectionManager.connectionCurrentX
    val connectionCurrentY get() = connectionManager.connectionCurrentY
    val isDraggingConnection get() = connectionManager.isDraggingConnection
    val potentialTargetPort get() = connectionManager.potentialTargetPort
    
    // Command history for undo/redo
    private val history = NodeEditorHistory()
    
    // Connection manager for handling connection operations
    private val connectionManager = ConnectionManager(history)
    
    /**
     * Sets the canvas element reference.
     *
     * @param element The canvas element
     */
    fun setCanvasElement(element: HTMLElement) {
        canvasElement = element
        updateCanvasSize()
    }
    
    /**
     * Sets the wrapper element reference.
     *
     * @param element The wrapper element
     */
    fun setWrapperElement(element: HTMLElement) {
        wrapperElement = element
        updateCanvasSize()
    }
    
    /**
     * Registers a node element reference.
     *
     * @param nodeId The ID of the node
     * @param element The node element
     */
    fun registerNodeElement(nodeId: String, element: HTMLElement) {
        nodeElements[nodeId] = element
        updateCanvasSize()
    }
    
    /**
     * Unregisters a node element reference.
     *
     * @param nodeId The ID of the node
     */
    fun unregisterNodeElement(nodeId: String) {
        nodeElements.remove(nodeId)
    }
    
    /**
     * Converts screen coordinates to canvas coordinates.
     * This is used when handling mouse events to convert the mouse position to canvas coordinates.
     * It's also used to convert DOM element positions (which are in screen space) to canvas coordinates.
     *
     * @param screenX X coordinate in screen space
     * @param screenY Y coordinate in screen space
     * @param isElementPosition Whether the coordinates are from a DOM element's getBoundingClientRect()
     * @return Pair of X and Y coordinates in canvas space
     */
    fun screenToCanvasCoordinates(screenX: Float, screenY: Float, isElementPosition: Boolean = false): Pair<Float, Float> {
        // Use the stored canvas element reference
        val rect = canvasElement?.getBoundingClientRectSafe()
    
        if (rect != null) {
            // Calculate the position relative to the canvas
            val relativeX = screenX - rect.left.toFloat()
            val relativeY = screenY - rect.top.toFloat()
        
            // Simple conversion without zoom or pan
            return Pair(relativeX, relativeY)
        } else {
            // Fallback if we can't get the canvas rect
            return Pair(screenX, screenY)
        }
    }
    
    /**
     * Adds a new node to the canvas.
     *
     * @param type The type of node as a string (event, dialogue, action, input)
     * @param nodeId The ID of the node type (e.g., teleport, player_interact)
     * @param label The label for the node
     * @param x The x position of the node
     * @param y The y position of the node
     * @return The ID of the newly created node
     */
    fun addNode(type: String, nodeId: String, label: String, x: Float, y: Float): String {
        // Create a new node (createNode handles conversion from String to LogicNode)
        val (id, newNode) = createNode(type, nodeId, label, x, y)
        
        // Create and execute the add node command
        val command = AddNodeCommand(nodes, id, newNode)
        val updatedNodes = history.executeCommand(command) as Map<String, NodeData>
        
        // Update the nodes map
        nodes = updatedNodes
        
        // Select the new node
        selectedNode = id

        updateCanvasSize()

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
        
        // Update canvas size after deleting the node
        updateCanvasSize()
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
                
                // Update canvas size to accommodate the moved node
                updateCanvasSize()
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
        
        // If the position changed, update canvas size
        val oldNode = nodes[nodeId]
        if (oldNode != null && (oldNode.x != updatedNode.x || oldNode.y != updatedNode.y)) {
            updateCanvasSize()
        }
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
        // Use fixed values for zoom and pan (no zoom, no pan)
        connectionManager.startConnection(nodeId, port, portX, portY, 1f, 0f, 0f)
    }
    
    /**
     * Updates the current position of the connection being dragged.
     *
     * @param screenX The current x position of the mouse in screen coordinates
     * @param screenY The current y position of the mouse in screen coordinates
     */
    fun updateConnectionPreview(screenX: Float, screenY: Float) {
        // Use fixed values for zoom and pan (no zoom, no pan)
        connectionManager.updateConnectionPreview(screenX, screenY, 1f, 0f, 0f)
    }
    
    /**
     * Completes a connection when the mouse is released over an input port.
     *
     * @param screenX The x position of the mouse in screen coordinates
     * @param screenY The y position of the mouse in screen coordinates
     */
    fun completeConnection(screenX: Float, screenY: Float) {
        val updatedConnections = connectionManager.completeConnection(screenX, screenY, connections)
        if (updatedConnections != null) {
            connections = updatedConnections
        }
    }
    
    /**
     * Cancels the current connection being created.
     */
    fun cancelConnection() {
        connectionManager.cancelConnection()
    }
    
    /**
     * Deletes a connection.
     *
     * @param connection The connection to delete
     */
    fun deleteConnection(connection: Connection) {
        connections = connectionManager.deleteConnection(connections, connection)
        
        // Clear the selection if this was the selected connection
        if (connection == selectedConnection) {
            selectedConnection = null
        }
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
    
    /**
     * Calculates the required canvas size based on node positions.
     * This ensures the canvas is large enough to accommodate all nodes with some padding.
     * Uses the available space (parent size) as initial values and actual node heights.
     * 
     * @return Pair of width and height for the canvas
     */
    private fun calculateRequiredCanvasSize(): Pair<Float, Float> {
        // Use wrapper element dimensions as initial values if available
        var maxX = canvasElement?.getBoundingClientRectSafe()?.width?.toFloat() ?: 800f
        var maxY = canvasElement?.getBoundingClientRectSafe()?.height?.toFloat() ?: 600f
        
        // If there are no nodes, return the parent size
        if (nodes.isEmpty()) {
            return Pair(maxX, maxY)
        }
        
        // Find the rightmost and bottommost positions of all nodes
        // Add node width and padding
        for ((nodeId, node) in nodes) {
            // Get the actual node element if available
            val nodeElement = nodeElements[nodeId]
            
            // Get the actual width and height of the node element, or use defaults
            val nodeWidth = nodeElement?.getBoundingClientRectSafe()?.width?.toFloat() ?: 180f
            val nodeHeight = nodeElement?.getBoundingClientRectSafe()?.height?.toFloat() ?: 150f
            
            // Calculate the right and bottom edges with padding
            val nodeRight = node.x + nodeWidth + 16f // Node width + padding
            val nodeBottom = node.y + nodeHeight + 16f // Actual node height + padding
            
            if (nodeRight > maxX) {
                maxX = nodeRight
            }
            
            if (nodeBottom > maxY) {
                maxY = nodeBottom
            }
        }
        
        return Pair(maxX, maxY)
    }
    
    /**
     * Updates the canvas size in the DOM based on the required size.
     * This ensures the canvas is large enough to accommodate all nodes.
     */
    private fun updateCanvasSize() {
        updateCanvasSizeTimeout?.let {
            clearTimeout(it)
        }
        calc()
        // Add a small delay before updating canvas size to allow the DOM to render the node
        updateCanvasSizeTimeout = setTimeout({
            calc()
            updateCanvasSizeTimeout = null
        }, 10) // 10ms delay as requested
    }

    private fun calc() {
        val (width, height) = calculateRequiredCanvasSize()

        // Update the canvas size using the stored reference
        canvasElement?.style?.width = "${width}px"
        canvasElement?.style?.height = "${height}px"
    }
}
