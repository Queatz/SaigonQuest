package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.utils.getBoundingClientRectSafe
import main.utils.getDocument
import web.animations.awaitAnimationFrame
import kotlin.random.Random

/**
 * Handles the rendering and management of connections between nodes.
 * This component is responsible for rendering existing connections and
 * the connection preview when creating a new connection.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param isInConnectionLayer Whether this handler is rendering in the separate connection layer
 */
@Composable
fun ConnectionHandler(controller: NodeEditorController) {
    // Render connections between nodes
    controller.connections.forEach { connection ->
        RenderConnection(controller, connection)
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
    // Find the source and target nodes and ports
    val sourceNode = controller.nodes[connection.sourceId]
    val targetNode = controller.nodes[connection.targetId]

    var r by remember { mutableStateOf(0) }

    // todo: hack due to recomposition being 1 frame behind but I don't know why
    LaunchedEffect(controller.nodes) {
        awaitAnimationFrame()
        r = Random.nextInt()
    }

    key(r) {
        if (sourceNode != null && targetNode != null) {
            // Find the source and target ports
            val sourcePort = sourceNode.outputs.find { it.id == connection.sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == connection.targetPortId }

            if (sourcePort != null && targetPort != null) {
                // Get the port elements by their data attributes
                val sourcePortElement =
                    getDocument().querySelector("[data-port-node='${connection.sourceId}'][data-port-id='${connection.sourcePortId}']")
                val targetPortElement =
                    getDocument().querySelector("[data-port-node='${connection.targetId}'][data-port-id='${connection.targetPortId}']")

                // Get the port positions
                val (sourceX, sourceY) = if (sourcePortElement != null) {
                    // Get the center position of the port element
                    val rect = sourcePortElement.getBoundingClientRectSafe()
                    // Convert screen coordinates to canvas coordinates
                    val screenX = (rect.left + rect.width / 2).toFloat()
                    val screenY = (rect.top + rect.height / 2).toFloat()

                    // For the canvas, use the normal coordinate conversion
                    // Use isElementPosition=true since these coordinates come from getBoundingClientRect()
                    val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                        screenX,
                        screenY,
                        isElementPosition = true
                    )
                    Pair(canvasX, canvasY)
                } else {
                    // Fallback to the old calculation if we can't get the port element
                    val sourcePortIndex = sourceNode.outputs.indexOf(sourcePort)
                    val sourcePortOffset = 50 + (sourcePortIndex * 30)

                    Pair(sourceNode.x + 180, sourceNode.y + sourcePortOffset)
                }

                val (targetX, targetY) = if (targetPortElement != null) {
                    // Get the center position of the port element
                    val rect = targetPortElement.getBoundingClientRectSafe()
                    // Convert screen coordinates to canvas coordinates
                    val screenX = (rect.left + rect.width / 2).toFloat()
                    val screenY = (rect.top + rect.height / 2).toFloat()

                    // Use isElementPosition=true since these coordinates come from getBoundingClientRect()
                    val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                        screenX,
                        screenY,
                        isElementPosition = true
                    )
                    Pair(canvasX, canvasY)
                } else {
                    // Fallback to the old calculation if we can't get the port element
                    val targetPortIndex = targetNode.inputs.indexOf(targetPort)
                    val targetPortOffset = 50 + (targetPortIndex * 30)

                    Pair(targetNode.x, targetNode.y + targetPortOffset)
                }

                // Create a unique identifier for this connection
                val connectionId =
                    "${connection.sourceId}:${connection.sourcePortId}-${connection.targetId}:${connection.targetPortId}"

                // Check if this connection is selected
                val isSelected = controller.selectedConnection == connection

                // Render the connection using a bezier curve
                BezierConnection(
                    sourceX = sourceX,
                    sourceY = sourceY,
                    targetX = targetX,
                    targetY = targetY,
                    isSelected = isSelected,
                    onClick = { controller.selectConnection(connection) },
                    connectionId = connectionId,
                )
            }
        }
    }
}

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
