package models.nodeeditor

import kotlinx.serialization.Serializable
import models.LogicNode
import models.NodeCategory
import models.NodeId

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
@Serializable
data class PortData(
    val id: String,
    val name: String,
    val type: PortType, // INPUT or OUTPUT
    val dataType: PropertyType? = null // The type of data this port accepts/provides
)

/**
 * A data class to represent a node in the editor.
 */
@Serializable
data class NodeData(
    val type: LogicNode,
    val x: Float = 0f,
    val y: Float = 0f,
    val label: String,
    val inputs: List<PortData> = emptyList(),
    val outputs: List<PortData> = emptyList(),
    val data: String = "{}" // JSON string containing node-specific data
)

/**
 * A data class to represent a connection between two ports.
 */
@Serializable
data class Connection(
    val sourceId: String,      // ID of the source node
    val sourcePortId: String,  // ID of the source port
    val targetId: String,      // ID of the target node
    val targetPortId: String   // ID of the target port
)

/**
 * Connection between two nodes
 *
 * This class represents a connection between two nodes in the logic system.
 */
@Serializable
data class NodeConnection(
    val id: String,
    val from: String,
    val to: String,
    val fromPort: String,
    val toPort: String
)

/**
 * Configuration for a node-based logic system
 *
 * This class represents the configuration of a node-based logic system,
 * including the nodes and connections between them.
 */
@Serializable
data class NodeConfig(
    val nodes: List<NodeDataModel> = emptyList(),
    val connections: List<NodeConnection> = emptyList()
)

/**
 * Data for a single node in the logic system
 *
 * This class represents a single node in the logic system, including its
 * type, position, and data.
 */
@Serializable
data class NodeDataModel(
    val id: String,
    val nodeCategory: NodeCategory,
    val nodeId: NodeId,
    val label: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val data: Map<String, String> = emptyMap()
)
