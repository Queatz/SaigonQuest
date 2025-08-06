package logic

import kotlinx.serialization.Serializable

/**
 * Configuration for a node-based logic system
 *
 * This class represents the configuration of a node-based logic system,
 * including the nodes and connections between them.
 */
@Serializable
data class NodeConfig(
    val nodes: List<NodeData> = emptyList(),
    val connections: List<NodeConnection> = emptyList()
)

/**
 * Data for a single node in the logic system
 *
 * This class represents a single node in the logic system, including its
 * type, position, and data.
 */
@Serializable
data class NodeData(
    val id: String,
    val type: String,
    val label: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val data: Map<String, String> = emptyMap()
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
    val to: String
)
