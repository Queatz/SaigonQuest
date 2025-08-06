package main.components.nodeeditor

import kotlin.js.Date
import models.LogicNode
import models.toTypeString

/**
 * Parse node data from JSON string.
 * 
 * @param jsonData The JSON string containing node data
 * @return A map of node IDs to NodeData objects
 */
fun parseNodes(jsonData: String): Map<String, NodeData> {
    try {
        // Use the NodeEditorJsonUtil to parse the JSON string
        val nodeEditorData = NodeEditorJsonUtil.parseNodeEditorData(jsonData)
        
        // Convert the NodeDataJson objects to NodeData objects
        return nodeEditorData.nodes.mapValues { (_, nodeDataJson) ->
            NodeEditorJsonUtil.convertToNodeData(nodeDataJson)
        }
    } catch (e: Exception) {
        console.error("Error parsing nodes", e)
        return emptyMap()
    }
}

/**
 * Parse connection data from JSON string.
 * 
 * @param jsonData The JSON string containing connection data
 * @return A list of Connection objects
 */
fun parseConnections(jsonData: String): List<Connection> {
    try {
        // Use the NodeEditorJsonUtil to parse the JSON string
        val nodeEditorData = NodeEditorJsonUtil.parseNodeEditorData(jsonData)
        
        // Convert the ConnectionJson objects to Connection objects
        return nodeEditorData.connections.map { connectionJson ->
            NodeEditorJsonUtil.convertToConnection(connectionJson)
        }
    } catch (e: Exception) {
        console.error("Error parsing connections", e)
        return emptyList()
    }
}

/**
 * Serialize node editor data to JSON string.
 * 
 * @param nodes Map of node IDs to NodeData objects
 * @param connections List of Connection objects
 * @return A JSON string representing the node editor data
 */
fun serializeNodeData(nodes: Map<String, NodeData>, connections: List<Connection>): String {
    // Convert the NodeData objects to NodeDataJson objects
    val nodeDataJsonMap = nodes.mapValues { (_, nodeData) ->
        NodeEditorJsonUtil.convertToNodeDataJson(nodeData)
    }
    
    // Convert the Connection objects to ConnectionJson objects
    val connectionJsonList = connections.map { connection ->
        NodeEditorJsonUtil.convertToConnectionJson(connection)
    }
    
    // Create a NodeEditorDataJson object
    val nodeEditorData = NodeEditorDataJson(
        nodes = nodeDataJsonMap,
        connections = connectionJsonList
    )
    
    // Use the NodeEditorJsonUtil to serialize the data
    return NodeEditorJsonUtil.serializeNodeEditorData(nodeEditorData)
}

/**
 * Creates ports for a node based on its LogicNode type.
 * 
 * @param logicNode The LogicNode type
 * @param nodeId The ID of the node
 * @return A pair of input and output port lists
 */
fun createPortsForNodeType(logicNode: LogicNode, nodeId: String): Pair<List<PortData>, List<PortData>> {
    val inputs = mutableListOf<PortData>()
    val outputs = mutableListOf<PortData>()
    
    // Get property definitions from the LogicNode
    val inputProperties = logicNode.getInputPropertyDefinitions()
    val outputProperties = logicNode.getOutputPropertyDefinitions()
    
    // Create input ports
    for (property in inputProperties) {
        inputs.add(PortData(
            id = "${nodeId}_in_${property.key.name.lowercase()}",
            name = property.name,
            type = PortType.INPUT,
            dataType = property.type.toPortDataType()
        ))
    }
    
    // Create output ports
    for (property in outputProperties) {
        outputs.add(PortData(
            id = "${nodeId}_out_${property.key.name.lowercase()}",
            name = property.name,
            type = PortType.OUTPUT,
            dataType = property.type.toPortDataType()
        ))
    }
    
    return Pair(inputs, outputs)
}

/**
 * Creates a new node with the given parameters.
 * 
 * @param type The type of node (as a string like "event", "dialogue", "action")
 * @param nodeId The ID of the node (or empty to generate one)
 * @param label The label for the node
 * @param x The x position of the node
 * @param y The y position of the node
 * @return The new NodeData object
 */
fun createNode(type: String, nodeId: String, label: String, x: Float, y: Float): Pair<String, NodeData> {
    // Always generate a unique ID by appending a timestamp
    // This ensures we can add multiple nodes of the same type
    val id = if (nodeId.isNotEmpty()) "${nodeId}_${Date.now().toLong()}" else "node_${Date.now().toLong()}"
    
    // Convert string type to LogicNode
    val logicNodeType = models.getLogicNodeFromTypeAndId(type, nodeId)
    
    // Create ports based on node type
    val (inputs, outputs) = createPortsForNodeType(logicNodeType, id)
    
    // Create a new node
    val newNode = NodeData(
        type = logicNodeType,
        x = x,
        y = y,
        label = label,
        inputs = inputs,
        outputs = outputs
    )
    
    return Pair(id, newNode)
}
