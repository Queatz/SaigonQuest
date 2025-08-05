package main.components.nodeeditor

import models.LogicNode

/**
 * Enum representing the type of port (input or output)
 */
enum class PortType {
    INPUT,
    OUTPUT
}

/**
 * Data class representing a port on a node.
 */
data class PortData(
    val id: String,
    val name: String,
    val type: PortType, // INPUT or OUTPUT
    val dataType: String = "any" // The type of data this port accepts/provides
)

/**
 * A data class to represent a node in the editor.
 */
data class NodeData(
    val type: LogicNode,
    val x: Float,
    val y: Float,
    val label: String,
    val inputs: List<PortData> = emptyList(),
    val outputs: List<PortData> = emptyList(),
    val data: String = "{}" // JSON string containing node-specific data
)

/**
 * A data class to represent a connection between two ports.
 */
data class Connection(
    val sourceId: String,      // ID of the source node
    val sourcePortId: String,  // ID of the source port
    val targetId: String,      // ID of the target node
    val targetPortId: String   // ID of the target port
)
