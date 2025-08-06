package main.components.nodeeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import main.utils.getBoundingClientRectSafe
import main.utils.getDocument
import main.utils.getElementFromPoint
import main.utils.getAttributeSafe

/**
 * Manages connection operations for the Node Editor.
 * This class encapsulates the logic for creating, updating, and completing connections.
 */
class ConnectionManager(
    private val historyManager: NodeEditorHistory
) {
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
    
    /**
     * Starts a connection from an output port.
     *
     * @param nodeId The ID of the node containing the output port
     * @param port The output port data
     * @param portX The x position of the port in screen coordinates
     * @param portY The y position of the port in screen coordinates
     * @param zoomLevel Current zoom level of the canvas (not used)
     * @param panX Current X pan offset of the canvas (not used)
     * @param panY Current Y pan offset of the canvas (not used)
     */
    fun startConnection(
        nodeId: String, 
        port: PortData, 
        portX: Float, 
        portY: Float,
        zoomLevel: Float,
        panX: Float,
        panY: Float
    ) {
        connectionStartNode = nodeId
        connectionStartPort = port

        // Convert the port position from screen coordinates to canvas coordinates
        // Get the canvas element by its data attribute
        val canvasElement = getDocument().querySelector("[data-canvas='node-editor-canvas']")
        val rect = canvasElement?.getBoundingClientRectSafe()
    
        val canvasX: Float
        val canvasY: Float
        
        if (rect != null) {
            // Calculate the position relative to the canvas
            canvasX = portX - rect.left.toFloat()
            canvasY = portY - rect.top.toFloat()
        } else {
            // Fallback if we can't get the canvas rect
            canvasX = portX
            canvasY = portY
        }
        
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
     * @param zoomLevel Current zoom level of the canvas (not used)
     * @param panX Current X pan offset of the canvas (not used)
     * @param panY Current Y pan offset of the canvas (not used)
     */
    fun updateConnectionPreview(
        screenX: Float, 
        screenY: Float,
        zoomLevel: Float,
        panX: Float,
        panY: Float
    ) {
        // Convert the mouse position from screen coordinates to canvas coordinates
        // Get the canvas element by its data attribute
        val canvasElement = getDocument().querySelector("[data-canvas='node-editor-canvas']")
        val rect = canvasElement?.getBoundingClientRectSafe()
    
        val canvasX: Float
        val canvasY: Float
        
        if (rect != null) {
            // Calculate the position relative to the canvas
            canvasX = screenX - rect.left.toFloat()
            canvasY = screenY - rect.top.toFloat()
        } else {
            // Fallback if we can't get the canvas rect
            canvasX = screenX
            canvasY = screenY
        }
        
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
                    if (child != null) {
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
     * @param connections Current list of connections
     * @return Updated list of connections if a new connection was created, or null if no connection was created
     */
    fun completeConnection(
        screenX: Float, 
        screenY: Float,
        connections: List<Connection>
    ): List<Connection>? {
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
                    if (child != null) {
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
                val updatedConnections = historyManager.executeCommand(command) as List<Connection>
                
                // Reset the connection state
                cancelConnection()
                
                // Return the updated connections
                return updatedConnections
            }
        }
        
        // Reset the connection state
        cancelConnection()
        
        // No connection was created
        return null
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
     * @param connections Current list of connections
     * @param connection The connection to delete
     * @return Updated list of connections
     */
    fun deleteConnection(connections: List<Connection>, connection: Connection): List<Connection> {
        // Create and execute the delete connection command
        val command = DeleteConnectionCommand(connections, connection)
        return historyManager.executeCommand(command) as List<Connection>
    }
}
