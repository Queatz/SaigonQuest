package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import main.utils.getBoundingClientRectSafe
import models.nodeeditor.Connection
import models.nodeeditor.PortType
import web.animations.awaitAnimationFrame

/**
 * Handles the rendering and management of connections between nodes.
 * This component is responsible for rendering existing connections and
 * the connection preview when creating a new connection.
 *
 * @param controller The NodeEditorController that manages state and operations
 */
@Composable
fun ConnectionHandler(controller: NodeEditorController) {
    // Render connections between nodes
    controller.connections.forEach { connection ->
        key(connection) {
            RenderConnection(controller, connection)
        }
    }

    if (controller.isDraggingConnection) {
        RenderConnectionPreview(controller)
    }
}

/**
 * Renders a single connection between two nodes.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param connection The connection to render
 */
@Composable
fun RenderConnection(
    controller: NodeEditorController,
    connection: Connection,
) {

    var invalidate by remember { mutableStateOf(0) }
    var invalidateTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(invalidate) {
        delay(50)
        awaitAnimationFrame()
        invalidateTrigger = invalidate
    }

    // Find the source and target nodes and ports
    val sourceNode = controller.nodes[connection.sourceId]
    val targetNode = controller.nodes[connection.targetId]

    // Use derivedStateOf to create a derived state that depends on the nodes and connections
    // This will cause recomposition when the nodes or connections change
    val connectionState by remember(controller.nodes, connection, invalidateTrigger) {
        derivedStateOf {
            if (sourceNode != null && targetNode != null) {
                // Find the source and target ports
                val sourcePort = sourceNode.outputs.find { it.id == connection.sourcePortId }
                val targetPort = targetNode.inputs.find { it.id == connection.targetPortId }

                if (sourcePort != null && targetPort != null) {
                    // Get the port elements using the controller's getPortElement method
                    println("[NODE_CONNECTIONS] Retrieving port elements for connection: ${connection.sourceId}:${connection.sourcePortId} -> ${connection.targetId}:${connection.targetPortId}")
                    val sourcePortElement = controller.getPortElement(
                        nodeId = connection.sourceId,
                        portId = connection.sourcePortId,
                        portType = PortType.OUTPUT
                    )
                    val targetPortElement = controller.getPortElement(
                        nodeId = connection.targetId,
                        portId = connection.targetPortId,
                        portType = PortType.INPUT
                    )
                    println("[NODE_CONNECTIONS] Source port element: ${if (sourcePortElement != null) "Found" else "NULL"}, Target port element: ${if (targetPortElement != null) "Found" else "NULL"}")

                    // Get the port positions
                    val (sourceX, sourceY) = if (sourcePortElement != null) {
                        // Get the center position of the port element
                        val rect = sourcePortElement.getBoundingClientRectSafe()
                        // Convert screen coordinates to canvas coordinates
                        val screenX = (rect.left + rect.width / 2).toFloat()
                        val screenY = (rect.top + rect.height / 2).toFloat()

                        // Convert to canvas coordinates
                        val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                            screenX = screenX,
                            screenY = screenY,
                        )
                        Pair(canvasX, canvasY)
                    } else {
                        // Fallback to the old calculation if we can't get the port element
                        val sourcePortIndex = sourceNode.outputs.indexOf(sourcePort)
                        val sourcePortOffset = 50 + (sourcePortIndex * 30)
                        
                        println("[NODE_CONNECTIONS] Using fallback position for source port: ${connection.sourceId}:${connection.sourcePortId}, index: $sourcePortIndex, offset: $sourcePortOffset")
                        invalidate++
                        Pair(sourceNode.x + 180, sourceNode.y + sourcePortOffset)
                    }

                    val (targetX, targetY) = if (targetPortElement != null) {
                        // Get the center position of the port element
                        val rect = targetPortElement.getBoundingClientRectSafe()
                        // Convert screen coordinates to canvas coordinates
                        val screenX = (rect.left + rect.width / 2).toFloat()
                        val screenY = (rect.top + rect.height / 2).toFloat()

                        // Convert to canvas coordinates
                        val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                            screenX = screenX,
                            screenY = screenY,
                        )
                        Pair(canvasX, canvasY)
                    } else {
                        // Fallback to the old calculation if we can't get the port element
                        val targetPortIndex = targetNode.inputs.indexOf(targetPort)
                        val targetPortOffset = 50 + (targetPortIndex * 30)
                        
                        println("[NODE_CONNECTIONS] Using fallback position for target port: ${connection.targetId}:${connection.targetPortId}, index: $targetPortIndex, offset: $targetPortOffset")
                        invalidate++
                        Pair(targetNode.x, targetNode.y + targetPortOffset)
                    }

                    // Create a unique identifier for this connection
                    val connectionId =
                        "${connection.sourceId}:${connection.sourcePortId}-${connection.targetId}:${connection.targetPortId}"

                    // Check if this connection is selected
                    val isSelected = controller.selectedConnection == connection

                    // Return the connection data
                    ConnectionData(
                        sourceX = sourceX,
                        sourceY = sourceY,
                        targetX = targetX,
                        targetY = targetY,
                        isSelected = isSelected,
                        connectionId = connectionId
                    )
                } else null
            } else null
        }
    }

    // Render the connection if the state is not null
    connectionState?.let { data ->
        BezierConnection(
            sourceX = data.sourceX,
            sourceY = data.sourceY,
            targetX = data.targetX,
            targetY = data.targetY,
            isSelected = data.isSelected,
            onClick = { controller.selectConnection(connection) },
            connectionId = data.connectionId,
        )
    }
}

/**
 * Data class to hold connection rendering data
 */
private data class ConnectionData(
    val sourceX: Float,
    val sourceY: Float,
    val targetX: Float,
    val targetY: Float,
    val isSelected: Boolean,
    val connectionId: String
)

/**
 * Renders a preview of a connection being created.
 *
 * @param controller The NodeEditorController that manages state and operations
 */
@Composable
fun RenderConnectionPreview(controller: NodeEditorController) {
    ConnectionPreview(
        sourceX = controller.connectionStartX,
        sourceY = controller.connectionStartY,
        currentX = controller.connectionCurrentX,
        currentY = controller.connectionCurrentY,
        isValidTarget = controller.potentialTargetPort != null,
    )
}
