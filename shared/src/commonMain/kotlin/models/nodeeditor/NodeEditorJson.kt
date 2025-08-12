package models.nodeeditor

import kotlinx.serialization.Serializable
import models.LogicNode
import models.NodeCategory
import models.NodeId

/**
 * Serializable data classes for node editor JSON structure
 */

/**
 * Serializable port data for JSON
 */
@Serializable
data class PortDataJson(
    val id: String,
    val name: String,
    val dataType: PropertyType? = null
)

/**
 * Serializable node data for JSON
 */
@Serializable
data class NodeDataJson(
    val type: NodeCategory,
    val nodeId: NodeId,
    val x: Float,
    val y: Float,
    val label: String,
    val inputs: List<PortDataJson> = emptyList(),
    val outputs: List<PortDataJson> = emptyList(),
    val data: String = "{}" // JSON string containing node-specific data
)

/**
 * Serializable connection data for JSON
 */
@Serializable
data class ConnectionJson(
    val sourceId: String,
    val sourcePortId: String,
    val targetId: String,
    val targetPortId: String
)

/**
 * Serializable node editor data for JSON
 */
@Serializable
data class NodeEditorDataJson(
    val nodes: Map<String, NodeDataJson> = emptyMap(),
    val connections: List<ConnectionJson> = emptyList()
)

/**
 * JSON utility object for node editor
 */
object NodeEditorJsonUtil {
    // Configure JSON parser with lenient mode to handle potential issues
    private val json = utils.json
    
    /**
     * Parse node editor data from JSON string
     */
    fun parseNodeEditorData(jsonData: String): NodeEditorDataJson {
        return try {
            // Handle empty or invalid JSON
            if (jsonData.isBlank() || jsonData == "{}") {
                NodeEditorDataJson()
            } else {
                json.decodeFromString<NodeEditorDataJson>(jsonData)
            }
        } catch (e: Throwable) {
            // Return empty data on error
            NodeEditorDataJson()
        }
    }
    /**
     * Convert between domain model and JSON model
     */
    fun convertToNodeData(nodeDataJson: NodeDataJson): NodeData {
        // Create the appropriate LogicNode based on nodeId
        val logicNode = when (nodeDataJson.nodeId) {
            NodeId.Teleport -> LogicNode.Teleport()
            NodeId.VisitorInteract -> LogicNode.VisitorInteract()
            NodeId.PlayerInteract -> LogicNode.PlayerInteract()
            NodeId.Dialogue -> LogicNode.Dialogue()
            NodeId.UserLanguage -> LogicNode.UserLanguage()
            NodeId.HasItem -> LogicNode.HasItem()
            NodeId.OpenUrl -> LogicNode.OpenUrl()
            NodeId.ObjectNotification -> LogicNode.ObjectNotification()
        }

        return NodeData(
            type = logicNode,
            x = nodeDataJson.x,
            y = nodeDataJson.y,
            label = nodeDataJson.label,
            inputs = nodeDataJson.inputs.map {
                PortData(
                    id = it.id,
                    name = it.name,
                    type = PortType.INPUT,
                    dataType = it.dataType
                )
            },
            outputs = nodeDataJson.outputs.map {
                PortData(
                    id = it.id,
                    name = it.name,
                    type = PortType.OUTPUT,
                    dataType = it.dataType
                )
            },
            data = nodeDataJson.data
        )
    }

    fun convertToNodeDataJson(nodeData: NodeData): NodeDataJson {
        return NodeDataJson(
            type = nodeData.type.category,
            nodeId = nodeData.type.id,
            x = nodeData.x,
            y = nodeData.y,
            label = nodeData.label,
            inputs = nodeData.inputs.map {
                PortDataJson(
                    id = it.id,
                    name = it.name,
                    dataType = it.dataType
                )
            },
            outputs = nodeData.outputs.map {
                PortDataJson(
                    id = it.id,
                    name = it.name,
                    dataType = it.dataType
                )
            },
            data = nodeData.data
        )
    }

    fun convertToConnection(connectionJson: ConnectionJson): Connection {
        return Connection(
            sourceId = connectionJson.sourceId,
            sourcePortId = connectionJson.sourcePortId,
            targetId = connectionJson.targetId,
            targetPortId = connectionJson.targetPortId
        )
    }

    fun convertToConnectionJson(connection: Connection): ConnectionJson {
        return ConnectionJson(
            sourceId = connection.sourceId,
            sourcePortId = connection.sourcePortId,
            targetId = connection.targetId,
            targetPortId = connection.targetPortId
        )
    }

    /**
     * Serialize node editor data to JSON string
     */
    fun serializeNodeEditorData(data: NodeEditorDataJson): String {
        return json.encodeToString(NodeEditorDataJson.serializer(), data)
    }
}
