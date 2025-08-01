package main.components.nodeeditor

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div

/**
 * Handles node operations such as rendering, creation, deletion, and updates.
 * This component is responsible for rendering nodes and handling node-related operations.
 *
 * @param controller The NodeEditorController that manages state and operations
 */
@Composable
fun NodeOperations(controller: NodeEditorController) {
    // Render existing nodes
    controller.nodes.forEach { (id, node) ->
        RenderNode(controller, id, node)
    }
}

/**
 * Renders a single node.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param id The ID of the node
 * @param node The node data
 */
@Composable
fun RenderNode(controller: NodeEditorController, id: String, node: NodeData) {
    NodeElement(
        id = id,
        node = node,
        isSelected = id == controller.selectedNode,
        onSelect = { controller.selectNode(id) },
        onEdit = { /* Open node edit dialog */ },
        onPortClick = { nodeId, port -> 
            // Handle port click (for future implementation)
        },
        onPortDragStart = { nodeId, port, portX, portY ->
            controller.startConnection(nodeId, port, portX, portY)
        },
        onPortDragMove = { x, y ->
            controller.updateConnectionPreview(x, y)
        },
        onPortDragEnd = { x, y ->
            controller.completeConnection(x, y)
        },
        onDrag = { nodeId, x, y ->
            controller.moveNode(nodeId, x, y)
        },
        controller = controller
    )
}

/**
 * Creates a new node at the specified position.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param type The type of node (event, dialogue, action, input)
 * @param nodeId The ID of the node
 * @param label The label for the node
 * @param x The x position of the node
 * @param y The y position of the node
 * @return The ID of the newly created node
 */
fun createNodeOperation(
    controller: NodeEditorController,
    type: String,
    nodeId: String,
    label: String,
    x: Float,
    y: Float
): String {
    return controller.addNode(type, nodeId, label, x, y)
}

/**
 * Deletes a node and its connections.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param nodeId The ID of the node to delete
 */
fun deleteNodeOperation(controller: NodeEditorController, nodeId: String) {
    controller.deleteNode(nodeId)
}

/**
 * Updates a node's properties.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param nodeId The ID of the node to update
 * @param updatedNode The updated node data
 */
fun updateNodeOperation(controller: NodeEditorController, nodeId: String, updatedNode: NodeData) {
    controller.updateNode(nodeId, updatedNode)
}

/**
 * Moves a node to a new position.
 *
 * @param controller The NodeEditorController that manages state and operations
 * @param nodeId The ID of the node to move
 * @param newX The new x position
 * @param newY The new y position
 */
fun moveNodeOperation(controller: NodeEditorController, nodeId: String, newX: Float, newY: Float) {
    controller.moveNode(nodeId, newX, newY)
}
