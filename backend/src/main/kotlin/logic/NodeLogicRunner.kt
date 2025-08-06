package logic

import debug.WebSocketDebug
import kotlinx.serialization.json.Json
import models.LogicNode
import models.Object
import models.nodeeditor.ConnectionJson
import models.nodeeditor.NodeEditorJsonUtil

/**
 * Manages the execution of logic nodes for objects
 *
 * This class is responsible for processing node configurations and executing
 * the appropriate actions based on events.
 */
class NodeLogicRunner(
    private val objectData: Object,
    private val context: NodeRunnerContext
) {
    init {
        WebSocketDebug.logMessage("NODE_LOGIC_RUNNER_INIT", "Initializing for object title: ${objectData.title}, worldId: ${objectData.worldId}", context.session)
    }
    
    // We no longer need to store original connections since we're using a simpler approach
    // for button index mapping
    
    // Parse the node configuration from the object data
    private val nodeConfig: NodeConfig? = try {
        if (objectData.data.isNotBlank()) {
            WebSocketDebug.logMessage("NODE_CONFIG_PARSING", "Parsing node config for object title: ${objectData.title}", context.session)
            
            // First parse the data using NodeEditorJsonUtil
            val nodeEditorData = NodeEditorJsonUtil.parseNodeEditorData(objectData.data)
            
            // Convert to NodeConfig format
            val nodes = nodeEditorData.nodes.map { (id, nodeDataJson) ->
                NodeData(
                    id = id,
                    type = "${nodeDataJson.type}_${nodeDataJson.nodeTypeId}",
                    label = nodeDataJson.label,
                    x = nodeDataJson.x,
                    y = nodeDataJson.y,
                    data = mapOf("data" to nodeDataJson.data)
                )
            }
            
            val connections = nodeEditorData.connections.mapIndexed { index, connection ->
                NodeConnection(
                    id = "conn_$index",
                    from = connection.sourceId,
                    to = connection.targetId
                )
            }
            
            val config = NodeConfig(nodes, connections)
            WebSocketDebug.logMessage("NODE_CONFIG_PARSED", "Successfully parsed node config with ${config.nodes.size} nodes and ${config.connections.size} connections", context.session)
            config
        } else {
            WebSocketDebug.logMessage("NODE_CONFIG_EMPTY", "Object ${objectData.title} has no node configuration data", context.session)
            null
        }
    } catch (e: Exception) {
        WebSocketDebug.logError("Failed to parse node config for object: ${objectData.title}", e, context.session)
        null
    }
    
    /**
     * Start processing from a specific node type
     *
     * @param startNodeType The type of node to start processing from
     * @param eventData Additional data related to the event
     */
    suspend fun processNodes(startNodeType: LogicNode, eventData: Map<String, Any>) {
        // Create a mutable copy of the event data
        val mutableEventData = eventData.toMutableMap()
        WebSocketDebug.logMessage("PROCESS_NODES_START", "Starting node processing for ${startNodeType::class.simpleName} on object: ${objectData.title}", context.session)
        
        if (nodeConfig == null) {
            WebSocketDebug.logMessage("PROCESS_NODES_ABORT", "No node configuration available for object: ${objectData.title}", context.session)
            return
        }
        
        // Find all nodes of the specified type
        val startNodes = nodeConfig.nodes.filter { node ->
            // Parse the combined type string (format: "type_id")
            val typeParts = node.type.split("_", limit = 2)
            val nodeType = typeParts.firstOrNull() ?: ""
            val nodeId = typeParts.getOrNull(1) ?: ""
            
            val matches = when (startNodeType) {
                is LogicNode.PlayerInteract -> nodeType == "event" && nodeId == "player_interact"
                is LogicNode.VisitorInteract -> nodeType == "event" && nodeId == "visitor_interact"
                else -> false
            }
            if (matches) {
                WebSocketDebug.logMessage("START_NODE_FOUND", "Found matching start node: $nodeType/$nodeId", context.session)
            }
            matches
        }
        
        WebSocketDebug.logMessage("START_NODES_COUNT", "Found ${startNodes.size} starting nodes for ${startNodeType::class.simpleName}", context.session)
        
        // Process each starting node
        for (startNode in startNodes) {
            WebSocketDebug.logMessage("PROCESSING_START_NODE", "Processing start node: ${startNode.type}/${startNode.id}", context.session)
            processNode(startNode, mutableEventData)
        }
        
        WebSocketDebug.logMessage("PROCESS_NODES_COMPLETE", "Completed node processing for ${startNodeType::class.simpleName}", context.session)
    }
    
    /**
     * Process a single node and follow its connections
     *
     * @param node The node to process
     * @param eventData Additional data related to the event (mutable)
     */
    private suspend fun processNode(node: NodeData, eventData: MutableMap<String, Any>) {
        WebSocketDebug.logMessage("PROCESSING_NODE", "Processing node: ${node.type}/${node.id} with data: ${node.data}", context.session)
        
        // Get the node runner for this node type
        val nodeRunner = getNodeRunner(node)
        WebSocketDebug.logMessage("NODE_RUNNER_SELECTED", "Selected runner: ${nodeRunner::class.simpleName} for node: ${node.type}/${node.id}", context.session)
        
        // Execute the node's logic
        WebSocketDebug.logMessage("NODE_EXECUTION_START", "Executing node: ${node.type}/${node.id}", context.session)
        val result = nodeRunner.execute(node, eventData)
        WebSocketDebug.logMessage("NODE_EXECUTION_RESULT", "Node ${node.type}/${node.id} execution result: $result", context.session)
        
        // If the node execution was successful, follow its connections
        if (result) {
            // Find connections from this node
            val connections = nodeConfig?.connections?.filter { it.from == node.id } ?: emptyList()
            WebSocketDebug.logMessage("NODE_CONNECTIONS", "Found ${connections.size} connections from node: ${node.id}", context.session)
            
            // For dialogue nodes, only follow the connection that matches the button index
            val typeParts = node.type.split("_", limit = 2)
            val nodeType = typeParts.firstOrNull() ?: ""
            val nodeId = typeParts.getOrNull(1) ?: ""
            
            if (nodeType == "dialogue" && nodeId == "dialogue") {
                // Get the button index from the event data (default to 0 if not present)
                val buttonIndex = (eventData["buttonIndex"] as? Int) ?: 0
                WebSocketDebug.logMessage("DIALOGUE_BUTTON_INDEX", "Using button index: $buttonIndex for node: ${node.id}", context.session)
                
                // Simply use the button index to select a connection
                // This ensures that the order of connections matches the order of buttons
                val connection = if (buttonIndex >= 0 && buttonIndex < connections.size) {
                    WebSocketDebug.logMessage("SELECTING_CONNECTION", 
                        "Selecting connection at index $buttonIndex from ${connections.size} connections", 
                        context.session)
                    connections[buttonIndex]
                } else {
                    WebSocketDebug.logMessage("INVALID_BUTTON_INDEX", 
                        "Button index $buttonIndex is out of range (0-${connections.size - 1})", 
                        context.session)
                    null
                }
                
                if (connection != null) {
                    WebSocketDebug.logMessage("FOLLOWING_DIALOGUE_CONNECTION", 
                        "Following connection from ${connection.from} to ${connection.to} for button index: $buttonIndex", 
                        context.session)
                    
                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage("TARGET_NODE_FOUND", "Found target node: ${targetNode.type}/${targetNode.id}", context.session)
                        processNode(targetNode, eventData)
                    } else {
                        WebSocketDebug.logError("Target node not found for connection: ${connection.from} -> ${connection.to}", null, context.session)
                    }
                } else {
                    WebSocketDebug.logMessage("NO_CONNECTION_FOR_BUTTON", 
                        "No connection found for button index: $buttonIndex on node: ${node.id}", 
                        context.session)
                }
            } else {
                // For other node types, follow all connections
                for (connection in connections) {
                    WebSocketDebug.logMessage("FOLLOWING_CONNECTION", "Following connection from ${connection.from} to ${connection.to}", context.session)
                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage("TARGET_NODE_FOUND", "Found target node: ${targetNode.type}/${targetNode.id}", context.session)
                        processNode(targetNode, eventData)
                    } else {
                        WebSocketDebug.logError("Target node not found for connection: ${connection.from} -> ${connection.to}", null, context.session)
                    }
                }
            }
        } else {
            WebSocketDebug.logMessage("NODE_EXECUTION_STOPPED", "Node execution returned false, stopping branch at ${node.type}/${node.id}", context.session)
        }
    }
    
    /**
     * Get the appropriate node runner for a node
     *
     * @param node The node to get a runner for
     * @return The node runner
     */
    private fun getNodeRunner(node: NodeData): NodeRunner {
        WebSocketDebug.logMessage("GET_NODE_RUNNER", "Finding runner for node type: ${node.type}, id: ${node.id}", context.session)
        
        // Parse the combined type string (format: "type_id")
        val typeParts = node.type.split("_", limit = 2)
        val nodeType = typeParts.firstOrNull() ?: ""
        val nodeId = typeParts.getOrNull(1) ?: ""
        
        WebSocketDebug.logMessage("NODE_TYPE_PARSED", "Parsed node type: $nodeType, id: $nodeId", context.session)
        
        val runner = when {
            nodeType == "action" && nodeId == "teleport" -> {
                WebSocketDebug.logMessage("TELEPORT_RUNNER_SELECTED", "Selected TeleportNodeRunner for node: ${node.id}", context.session)
                TeleportNodeRunner(context)
            }
            nodeType == "dialogue" && nodeId == "dialogue" -> {
                WebSocketDebug.logMessage("DIALOGUE_RUNNER_SELECTED", "Selected DialogueNodeRunner for node: ${node.id}", context.session)
                DialogueNodeRunner(context)
            }
            // Add more node runners as needed
            else -> {
                WebSocketDebug.logMessage("NO_RUNNER_FOUND", "No specific runner found for node type: $nodeType, id: $nodeId. Using NoOpNodeRunner", context.session)
                NoOpNodeRunner()
            }
        }
        
        return runner
    }
}
