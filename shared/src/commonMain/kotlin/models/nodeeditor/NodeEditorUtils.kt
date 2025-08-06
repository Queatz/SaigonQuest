package models.nodeeditor

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
        // Silent error handling in shared module
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
        // Silent error handling in shared module
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
