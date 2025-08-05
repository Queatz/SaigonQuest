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
 * Creates ports for a node based on its type.
 * 
 * @param type The type of node (event, dialogue, action)
 * @param nodeId The ID of the node
 * @return A pair of input and output port lists
 */
fun createPortsForNodeType(type: String, nodeId: String): Pair<List<PortData>, List<PortData>> {
    val inputs = mutableListOf<PortData>()
    val outputs = mutableListOf<PortData>()
    
    when (type) {
        // Events have no inputs, only outputs
        "event" -> {
            outputs.add(PortData(
                id = "${nodeId}_out_trigger",
                name = "Trigger",
                type = PortType.OUTPUT,
                dataType = "trigger"
            ))
        }
        
        // Dialogue nodes have inputs and outputs
        "dialogue" -> {
            inputs.add(PortData(
                id = "${nodeId}_in_trigger",
                name = "Trigger",
                type = PortType.INPUT,
                dataType = "trigger"
            ))
            
            outputs.add(PortData(
                id = "${nodeId}_out_next",
                name = "Next",
                type = PortType.OUTPUT,
                dataType = "trigger"
            ))
        }
        
        // Actions have inputs and may have outputs
        "action" -> {
            inputs.add(PortData(
                id = "${nodeId}_in_trigger",
                name = "Trigger",
                type = PortType.INPUT,
                dataType = "trigger"
            ))
            
            // Some actions might have additional outputs
            if (nodeId == "teleport" || nodeId == "play_sound" || nodeId == "show_effect") {
                outputs.add(PortData(
                    id = "${nodeId}_out_complete",
                    name = "Complete",
                    type = PortType.OUTPUT,
                    dataType = "trigger"
                ))
            }
        }
        
        // Inputs have no inputs, only outputs
        "input" -> {
            // Different output ports based on the input type
            when (nodeId) {
                "player_info" -> {
                    outputs.add(PortData(
                        id = "${nodeId}_out_name",
                        name = "Name",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                    outputs.add(PortData(
                        id = "${nodeId}_out_id",
                        name = "ID",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                }
                "visitor_info" -> {
                    outputs.add(PortData(
                        id = "${nodeId}_out_name",
                        name = "Name",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                    outputs.add(PortData(
                        id = "${nodeId}_out_id",
                        name = "ID",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                }
                "world_info" -> {
                    outputs.add(PortData(
                        id = "${nodeId}_out_name",
                        name = "Name",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                    outputs.add(PortData(
                        id = "${nodeId}_out_id",
                        name = "ID",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                }
                "object_info" -> {
                    outputs.add(PortData(
                        id = "${nodeId}_out_name",
                        name = "Name",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                    outputs.add(PortData(
                        id = "${nodeId}_out_id",
                        name = "ID",
                        type = PortType.OUTPUT,
                        dataType = "string"
                    ))
                    outputs.add(PortData(
                        id = "${nodeId}_out_position",
                        name = "Position",
                        type = PortType.OUTPUT,
                        dataType = "vector3"
                    ))
                }
            }
        }
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
    
    // Create ports based on node type
    val (inputs, outputs) = createPortsForNodeType(type, id)
    
    // Convert string type to LogicNode
    val logicNodeType = models.getLogicNodeFromTypeAndId(type, nodeId)
    
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
