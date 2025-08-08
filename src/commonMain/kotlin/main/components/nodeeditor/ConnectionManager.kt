package main.components.nodeeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import models.nodeeditor.Connection
import models.nodeeditor.PortData
import models.nodeeditor.PortType
import org.w3c.dom.HTMLElement

/**
 * Manages connection operations for the Node Editor.
 * This class encapsulates the logic for creating, updating, and completing connections.
 */
class ConnectionManager(
    private val historyManager: NodeEditorHistory,
    private val controller: NodeEditorController
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
     */
    fun startConnection(
        nodeId: String, 
        port: PortData, 
        portX: Float, 
        portY: Float,
    ) {
        connectionStartNode = nodeId
        connectionStartPort = port

        // Convert the port position from screen coordinates to canvas coordinates
        val (canvasX, canvasY) = controller.screenToCanvasCoordinates(portX, portY)
        
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
    fun updateConnectionPreview(
        screenX: Float, 
        screenY: Float,
    ) {
        // Convert the mouse position from screen coordinates to canvas coordinates
        val (canvasX, canvasY) = controller.screenToCanvasCoordinates(screenX, screenY)
        
        // Update the current connection position with the canvas coordinates
        // This ensures the connection preview aligns properly with the transformed canvas
        connectionCurrentX = canvasX
        connectionCurrentY = canvasY

        // Find if there's a potential target port at the current position
        // Use the controller's findPortElementAtPosition method
        val portResult = controller.findPortElementAtPosition(
            screenX = screenX,
            screenY = screenY,
            portType = PortType.INPUT,
            excludeNodeId = connectionStartNode
        )
        
        // Update potential target port if found
        potentialTargetPort = if (portResult != null) {
            val (nodeId, portId, _) = portResult
            Pair(nodeId, portId)
        } else {
            null
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
        // If we already have a potential target port, use that
        val targetPort = potentialTargetPort
        if (targetPort != null && connectionStartNode != null && connectionStartPort != null) {
            // Create a new connection
            val newConnection = Connection(
                sourceId = connectionStartNode!!,
                sourcePortId = connectionStartPort!!.id,
                targetId = targetPort.first,
                targetPortId = targetPort.second
            )
            
            // Create and execute the add connection command
            val command = AddConnectionCommand(connections, newConnection)
            val updatedConnections = historyManager.executeCommand(command) as List<Connection>
            
            // Reset the connection state
            cancelConnection()
            
            // Return the updated connections
            return updatedConnections
        }
        
        // If we don't have a potential target port, try to find one
        val portResult = controller.findPortElementAtPosition(
            screenX = screenX,
            screenY = screenY,
            portType = PortType.INPUT,
            excludeNodeId = connectionStartNode
        )
        
        if (portResult != null && connectionStartNode != null && connectionStartPort != null) {
            val (nodeId, portId, _) = portResult
            
            // Create a new connection
            val newConnection = Connection(
                sourceId = connectionStartNode!!,
                sourcePortId = connectionStartPort!!.id,
                targetId = nodeId,
                targetPortId = portId
            )
            
            // Create and execute the add connection command
            val command = AddConnectionCommand(connections, newConnection)
            val updatedConnections = historyManager.executeCommand(command) as List<Connection>
            
            // Reset the connection state
            cancelConnection()
            
            // Return the updated connections
            return updatedConnections
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
