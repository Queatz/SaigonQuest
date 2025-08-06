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
