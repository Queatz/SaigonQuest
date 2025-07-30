package main.components.nodeeditor

import kotlin.js.json
import kotlin.js.Date
import kotlin.js.JSON

/**
 * Parse node data from JSON string.
 * 
 * @param jsonData The JSON string containing node data
 * @return A map of node IDs to NodeData objects
 */
fun parseNodes(jsonData: String): Map<String, NodeData> {
    try {
        val data = JSON.parse<dynamic>(jsonData)
        val result = mutableMapOf<String, NodeData>()
        
        if (data.nodes != undefined) {
            val nodes = data.nodes
            for (key in js("Object").keys(nodes)) {
                val node = nodes[key]
                val nodeId = key as String
                
                // Parse inputs
                val inputs = mutableListOf<PortData>()
                if (node.inputs != undefined) {
                    val nodeInputs = node.inputs
                    for (i in 0 until (nodeInputs.length as? Int ?: 0)) {
                        val input = nodeInputs[i]
                        inputs.add(PortData(
                            id = input.id as? String ?: "${nodeId}_in_$i",
                            name = input.name as? String ?: "Input $i",
                            type = PortType.INPUT,
                            dataType = input.dataType as? String ?: "any"
                        ))
                    }
                }
                
                // Parse outputs
                val outputs = mutableListOf<PortData>()
                if (node.outputs != undefined) {
                    val nodeOutputs = node.outputs
                    for (i in 0 until (nodeOutputs.length as? Int ?: 0)) {
                        val output = nodeOutputs[i]
                        outputs.add(PortData(
                            id = output.id as? String ?: "${nodeId}_out_$i",
                            name = output.name as? String ?: "Output $i",
                            type = PortType.OUTPUT,
                            dataType = output.dataType as? String ?: "any"
                        ))
                    }
                }
                
                // Create the node with inputs and outputs
                result[nodeId] = NodeData(
                    type = node.type as? String ?: "",
                    x = (node.x as? Number)?.toFloat() ?: 0f,
                    y = (node.y as? Number)?.toFloat() ?: 0f,
                    label = node.label as? String ?: "Node",
                    inputs = inputs,
                    outputs = outputs
                )
            }
        }
        
        return result
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
        val data = JSON.parse<dynamic>(jsonData)
        val result = mutableListOf<Connection>()
        
        if (data.connections != undefined) {
            val connections = data.connections
            for (i in 0 until connections.length) {
                val conn = connections[i]
                
                // Extract connection data
                val sourceId = conn.sourceId as? String ?: conn.source?.id as? String ?: ""
                val sourcePortId = conn.sourcePortId as? String ?: conn.source?.portId as? String ?: ""
                val targetId = conn.targetId as? String ?: conn.target?.id as? String ?: ""
                val targetPortId = conn.targetPortId as? String ?: conn.target?.portId as? String ?: ""
                
                // Only add valid connections
                if (sourceId.isNotEmpty() && sourcePortId.isNotEmpty() && 
                    targetId.isNotEmpty() && targetPortId.isNotEmpty()) {
                    result.add(Connection(
                        sourceId = sourceId,
                        sourcePortId = sourcePortId,
                        targetId = targetId,
                        targetPortId = targetPortId
                    ))
                }
            }
        }
        
        return result
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
    val nodesObj = js("{}")
    
    for ((id, node) in nodes) {
        // Convert NodeData back to a JS object
        val nodeObj = js("{}")
        nodeObj["type"] = node.type
        nodeObj["x"] = node.x
        nodeObj["y"] = node.y
        nodeObj["label"] = node.label
        
        // Serialize input ports
        val inputsArray = js("[]")
        for (input in node.inputs) {
            val inputObj = js("{}")
            inputObj["id"] = input.id
            inputObj["name"] = input.name
            inputObj["dataType"] = input.dataType
            inputsArray.push(inputObj)
        }
        nodeObj["inputs"] = inputsArray
        
        // Serialize output ports
        val outputsArray = js("[]")
        for (output in node.outputs) {
            val outputObj = js("{}")
            outputObj["id"] = output.id
            outputObj["name"] = output.name
            outputObj["dataType"] = output.dataType
            outputsArray.push(outputObj)
        }
        nodeObj["outputs"] = outputsArray
        
        nodesObj[id] = nodeObj
    }
    
    // Serialize connections
    val connectionsArray = js("[]")
    for (connection in connections) {
        val connectionObj = js("{}")
        connectionObj["sourceId"] = connection.sourceId
        connectionObj["sourcePortId"] = connection.sourcePortId
        connectionObj["targetId"] = connection.targetId
        connectionObj["targetPortId"] = connection.targetPortId
        connectionsArray.push(connectionObj)
    }
    
    val data = json(
        "nodes" to nodesObj,
        "connections" to connectionsArray
    )
    
    return JSON.stringify(data)
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
 * @param type The type of node (event, dialogue, action)
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
    
    // Create a new node
    val newNode = NodeData(
        type = type,
        x = x,
        y = y,
        label = label,
        inputs = inputs,
        outputs = outputs
    )
    
    return Pair(id, newNode)
}
