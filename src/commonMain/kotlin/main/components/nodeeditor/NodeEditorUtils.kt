package main.components.nodeeditor

import models.LogicNode
import models.NodeCategory
import models.NodeId
import models.nodeeditor.Connection
import models.nodeeditor.NodeData
import models.nodeeditor.NodeEditorDataJson
import models.nodeeditor.NodeEditorJsonUtil
import models.nodeeditor.NodeEditorJsonUtil.convertToNodeData
import models.nodeeditor.PortData
import models.nodeeditor.PortType
import models.nodeeditor.PropertyKey
import models.nodeeditor.PropertyType
import models.nodeeditor.getInputPropertyDefinitions
import models.nodeeditor.getOutputPropertyDefinitions
import kotlin.js.Date

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
            convertToNodeData(nodeDataJson)
        }
    } catch (e: Throwable) {
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
    } catch (e: Throwable) {
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
 * @param nodeData Optional node data string for extracting property values
 * @return A pair of input and output port lists
 */
fun createPortsForNodeType(logicNode: LogicNode, nodeId: String, nodeData: String = "{}"): Pair<List<PortData>, List<PortData>> {
    val inputs = mutableListOf<PortData>()
    val outputs = mutableListOf<PortData>()
    
    // Get property definitions from the LogicNode
    val inputProperties = logicNode.getInputPropertyDefinitions()
    val outputProperties = logicNode.getOutputPropertyDefinitions()
    
    // Create input ports
    for (property in inputProperties) {
        inputs.add(
            PortData(
                id = "${nodeId}_in_${property.key.name.lowercase()}",
                name = property.name,
                type = PortType.INPUT,
                dataType = property.type
            )
        )
    }
    
    // For dialogue nodes, create dynamic outputs based on buttons
    if (logicNode is LogicNode.Dialogue) {
        // Parse the node data to extract buttons
        val parsedNodeData = NodeDataParser.parseNodeData(logicNode, nodeData)
        val buttonsValue = NodeDataParser.getPropertyValue(parsedNodeData, PropertyKey.BUTTONS)
        
        // If buttons are defined, create an output port for each button
        if (buttonsValue.isNotEmpty()) {
            val buttons = buttonsValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            // Create a port for each button
            buttons.forEachIndexed { index, buttonText ->
                outputs.add(
                    PortData(
                        id = "${nodeId}_out_button_$index",
                        name = buttonText,
                        type = PortType.OUTPUT,
                        dataType = PropertyType.TRIGGER
                    )
                )
            }
        } else {
            // If no buttons are defined, create a default "OK" button output
            outputs.add(
                PortData(
                    id = "${nodeId}_out_button_0",
                    name = "OK",
                    type = PortType.OUTPUT,
                    dataType = PropertyType.TRIGGER
                )
            )
        }
    } else {
        // For other node types, create output ports as usual
        outputProperties.forEachIndexed { index, property ->
            outputs.add(
                PortData(
                    id = "${nodeId}_out_$index",
                    name = property.name,
                    type = PortType.OUTPUT,
                    dataType = property.type
                )
            )
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
 * @param data Optional node data string for extracting property values
 * @return The new NodeData object
 */
fun createNode(
    type: NodeCategory,
    nodeId: NodeId,
    label: String,
    x: Float,
    y: Float,
    data: String = "{}"
): Pair<String, NodeData> {
    // Always generate a unique ID by appending a timestamp
    // This ensures we can add multiple nodes of the same type
    val id = "${nodeId}_${Date.now().toLong()}"

    // Create the appropriate LogicNode based on nodeId
    val logicNodeType = when (nodeId) {
        NodeId.Teleport -> LogicNode.Teleport()
        NodeId.VisitorInteract -> LogicNode.VisitorInteract()
        NodeId.PlayerInteract -> LogicNode.PlayerInteract()
        NodeId.Dialogue -> LogicNode.Dialogue()
        NodeId.UserLanguage -> LogicNode.UserLanguage()
        NodeId.UserSex -> LogicNode.UserSex()
        NodeId.UserAge -> LogicNode.UserAge()
        NodeId.HasItem -> LogicNode.HasItem()
        NodeId.OpenUrl -> LogicNode.OpenUrl()
        NodeId.ObjectNotification -> LogicNode.ObjectNotification()
        NodeId.ObjectOwnerNotification -> LogicNode.ObjectOwnerNotification()
        NodeId.Delay -> LogicNode.Delay()
        NodeId.TakeItems -> LogicNode.TakeItems()
        NodeId.GiveItems -> LogicNode.GiveItems()
        NodeId.SendChat -> LogicNode.SendChat()
    }
    
    // Create ports based on node type and data
    val (inputs, outputs) = createPortsForNodeType(logicNodeType, id, data)
    
    // Create a new node
    val newNode = NodeData(
        type = logicNodeType,
        x = x,
        y = y,
        label = label,
        inputs = inputs,
        outputs = outputs,
        data = data
    )
    
    return Pair(id, newNode)
}

/**
 * Updates the outputs of a node based on its data.
 * This is particularly useful for dialogue nodes, where the outputs depend on the buttons.
 * 
 * @param node The node to update
 * @param nodeId The ID of the node
 * @param data The node data string
 * @return The updated node
 */
fun updateNodeOutputs(node: NodeData, nodeId: String, data: String): NodeData {
    // For dialogue nodes, recreate outputs based on buttons
    if (node.type.id == NodeId.Dialogue) {
        // Use createPortsForNodeType to generate new inputs and outputs
        val (_, outputs) = createPortsForNodeType(node.type, nodeId, data)
        
        // Return updated node with new outputs
        return node.copy(
            outputs = outputs,
            data = data
        )
    }
    
    // For other node types, just update the data
    return node.copy(data = data)
}
