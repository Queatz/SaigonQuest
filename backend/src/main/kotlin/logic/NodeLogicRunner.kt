package logic

import debug.WebSocketDebug
import models.LogicNode
import models.NodeCategory
import models.NodeId
import models.Object
import models.nodeeditor.NodeConfig
import models.nodeeditor.NodeConnection
import models.nodeeditor.NodeDataModel
import models.nodeeditor.NodeEditorJsonUtil
import models.nodeeditor.PropertyKey

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
        WebSocketDebug.logMessage(
            "NODE_LOGIC_RUNNER_INIT",
            "Initializing for object title: ${objectData.title}, worldId: ${objectData.worldId}",
            context.session
        )
    }

    // We no longer need to store original connections since we're using a simpler approach
    // for button index mapping

    // Parse the node configuration from the object data
    private val nodeConfig: NodeConfig? = try {
        if (objectData.data.isNotBlank()) {
            WebSocketDebug.logMessage(
                "NODE_CONFIG_PARSING",
                "Parsing node config for object title: ${objectData.title}",
                context.session
            )

            // First parse the data using NodeEditorJsonUtil
            val nodeEditorData = NodeEditorJsonUtil.parseNodeEditorData(objectData.data)

            // Convert to NodeConfig format
            val nodes = nodeEditorData.nodes.map { (id, nodeDataJson) ->
                NodeDataModel(
                    id = id,
                    nodeCategory = nodeDataJson.type,
                    nodeId = nodeDataJson.nodeId,
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
                    to = connection.targetId,
                    fromPort = connection.sourcePortId,
                    toPort = connection.targetPortId,
                )
            }

            val config = NodeConfig(nodes, connections)
            WebSocketDebug.logMessage(
                "NODE_CONFIG_PARSED",
                "Successfully parsed node config with ${config.nodes.size} nodes and ${config.connections.size} connections",
                context.session
            )
            config
        } else {
            WebSocketDebug.logMessage(
                "NODE_CONFIG_EMPTY",
                "Object ${objectData.title} has no node configuration data",
                context.session
            )
            null
        }
    } catch (e: Throwable) {
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
        WebSocketDebug.logMessage(
            "PROCESS_NODES_START",
            "Starting node processing for ${startNodeType::class.simpleName} on object: ${objectData.title}",
            context.session
        )

        if (nodeConfig == null) {
            WebSocketDebug.logMessage(
                "PROCESS_NODES_ABORT",
                "No node configuration available for object: ${objectData.title}",
                context.session
            )
            return
        }

        // Find all nodes of the specified type
        val startNodes = nodeConfig.nodes.filter { node ->
            val matches = when (startNodeType) {
                is LogicNode.PlayerInteract -> node.nodeId == NodeId.PlayerInteract
                is LogicNode.VisitorInteract -> node.nodeId == NodeId.VisitorInteract
                else -> false
            }
            if (matches) {
                WebSocketDebug.logMessage(
                    "START_NODE_FOUND",
                    "Found matching start node: ${node.nodeCategory}/${node.nodeId}",
                    context.session
                )
            }
            matches
        }

        WebSocketDebug.logMessage(
            "START_NODES_COUNT",
            "Found ${startNodes.size} starting nodes for ${startNodeType::class.simpleName}",
            context.session
        )

        // Process each starting node
        for (startNode in startNodes) {
            WebSocketDebug.logMessage(
                "PROCESSING_START_NODE",
                "Processing start node: ${startNode.nodeCategory}/${startNode.id}",
                context.session
            )
            processNode(startNode, mutableEventData)
        }

        WebSocketDebug.logMessage(
            "PROCESS_NODES_COMPLETE",
            "Completed node processing for ${startNodeType::class.simpleName}",
            context.session
        )
    }

    /**
     * Process a single node and follow its connections
     *
     * @param node The node to process
     * @param eventData Additional data related to the event (mutable)
     */
    private suspend fun processNode(node: NodeDataModel, eventData: MutableMap<String, Any>) {
        WebSocketDebug.logMessage(
            "PROCESSING_NODE",
            "Processing node: ${node.nodeCategory}/${node.id} with data: ${node.data}",
            context.session
        )

        // Get the node runner for this node type
        val nodeRunner = getNodeRunner(node)
        WebSocketDebug.logMessage(
            "NODE_RUNNER_SELECTED",
            "Selected runner: ${nodeRunner::class.simpleName} for node: ${node.nodeCategory}/${node.id}",
            context.session
        )

        // Execute the node's logic
        WebSocketDebug.logMessage(
            "NODE_EXECUTION_START",
            "Executing node: ${node.nodeCategory}/${node.id}",
            context.session
        )
        val result = nodeRunner.execute(node, eventData)
        WebSocketDebug.logMessage(
            "NODE_EXECUTION_RESULT",
            "Node ${node.nodeCategory}/${node.id} execution result: $result",
            context.session
        )

        // If the node execution was successful, follow its connections
        if (result) {
            // Find connections from this node
            val connections = nodeConfig?.connections?.filter { it.from == node.id } ?: emptyList()
            WebSocketDebug.logMessage(
                "NODE_CONNECTIONS",
                "Found ${connections.size} connections from node: ${node.id}",
                context.session
            )

            // For dialogue and language nodes, follow the connection that matches the button index
            // For HasItem nodes, follow the connection that matches the output property
            if (node.nodeId in listOf(NodeId.Dialogue, NodeId.UserLanguage)) {
                // Get the button index from the event data (default to 0 if not present)
                val buttonIndex = (eventData["buttonIndex"] as? Int) ?: 0
                WebSocketDebug.logMessage(
                    "DIALOGUE_BUTTON_INDEX",
                    "Using button index: $buttonIndex for node: ${node.id}",
                    context.session
                )

                // Simply use the button index to select a connection
                // This ensures that the order of connections matches the order of buttons
                val connection = connections.find { connection -> connection.fromPort.endsWith("_out_button_$buttonIndex") }

                if (connection != null) {
                    WebSocketDebug.logMessage(
                        "FOLLOWING_HAS_ITEM_CONNECTION",
                        "Following connection from ${connection.from} to ${connection.to} for buttonIndex: $buttonIndex",
                        context.session
                    )

                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage(
                            "TARGET_NODE_FOUND",
                            "Found target node: ${targetNode.nodeCategory}/${targetNode.id}",
                            context.session
                        )
                        processNode(targetNode, eventData)
                    } else {
                        WebSocketDebug.logError(
                            "Target node not found for connection: ${connection.from} -> ${connection.to}",
                            null,
                            context.session
                        )
                    }
                } else {
                    WebSocketDebug.logMessage(
                        "NO_CONNECTION_FOR_OUTPUT_PROPERTY",
                        "No connection found for buttonIndex: $buttonIndex on node: ${node.id}",
                        context.session
                    )
                }
            } else if (node.nodeId == NodeId.HasItem || node.nodeId == NodeId.TakeItems) {
                // For HasItem nodes, use the outputProperty to select the correct connection
                val outputProperty = eventData["outputProperty"] as? PropertyKey

                WebSocketDebug.logMessage(
                    "HAS_ITEM_OUTPUT_PROPERTY",
                    "Using output property: $outputProperty for node: ${node.id}",
                    context.session
                )

                // Find the connection that corresponds to the output property
                val connection = if (connections.isNotEmpty()) {
                    // Find the connection that matches the output property based on the sourcePortId in the connection id
                    WebSocketDebug.logMessage(
                        "CHECKING_CONNECTIONS",
                        "Checking ${connections.size} connections for output property: $outputProperty",
                        context.session
                    )

                    val matchingConnection = connections.find { connection ->
                        WebSocketDebug.logMessage(
                            "CHECKING_CONNECTIONS",
                            "Checking connection for output property: $connection with fromPort=${connection.fromPort}",
                            context.session
                        )

                        when (outputProperty) {
                            PropertyKey.YES -> connection.fromPort.endsWith("_out_0")
                            PropertyKey.NO -> connection.fromPort.endsWith("_out_1")
                            PropertyKey.NOT_ENOUGH -> connection.fromPort.endsWith("_out_2")
                            else -> false
                        }
                    }

                    if (matchingConnection != null) {
                        WebSocketDebug.logMessage(
                            "FOUND_MATCHING_CONNECTION",
                            "Found connection matching output property $outputProperty: ${matchingConnection.id}",
                            context.session
                        )
                        matchingConnection
                    } else {
                        WebSocketDebug.logError(
                            "No connection found matching output property $outputProperty for HasItem node: ${node.id}",
                            null,
                            context.session
                        )
                        null
                    }
                } else {
                    WebSocketDebug.logError("No connections found for HasItem node: ${node.id}", null, context.session)
                    null
                }

                if (connection != null) {
                    WebSocketDebug.logMessage(
                        "FOLLOWING_HAS_ITEM_CONNECTION",
                        "Following connection from ${connection.from} to ${connection.to} for output property: $outputProperty",
                        context.session
                    )

                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage(
                            "TARGET_NODE_FOUND",
                            "Found target node: ${targetNode.nodeCategory}/${targetNode.id}",
                            context.session
                        )
                        processNode(targetNode, eventData)
                    } else {
                        WebSocketDebug.logError(
                            "Target node not found for connection: ${connection.from} -> ${connection.to}",
                            null,
                            context.session
                        )
                    }
                } else {
                    WebSocketDebug.logMessage(
                        "NO_CONNECTION_FOR_OUTPUT_PROPERTY",
                        "No connection found for output property: $outputProperty on node: ${node.id}",
                        context.session
                    )
                }
            } else {
                // For other node types, follow all connections
                for (connection in connections) {
                    WebSocketDebug.logMessage(
                        "FOLLOWING_CONNECTION",
                        "Following connection from ${connection.from} to ${connection.to}",
                        context.session
                    )
                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage(
                            "TARGET_NODE_FOUND",
                            "Found target node: ${targetNode.nodeCategory}/${targetNode.id}",
                            context.session
                        )
                        processNode(targetNode, eventData)
                    } else {
                        WebSocketDebug.logError(
                            "Target node not found for connection: ${connection.from} -> ${connection.to}",
                            null,
                            context.session
                        )
                    }
                }
            }
        } else {
            WebSocketDebug.logMessage(
                "NODE_EXECUTION_STOPPED",
                "Node execution returned false, stopping branch at ${node.nodeCategory}/${node.id}",
                context.session
            )
        }
    }

    /**
     * Get the appropriate node runner for a node
     *
     * @param node The node to get a runner for
     * @return The node runner
     */
    private fun getNodeRunner(node: NodeDataModel): NodeRunner {
        WebSocketDebug.logMessage(
            "GET_NODE_RUNNER",
            "Finding runner for node category: ${node.nodeCategory}, id: ${node.nodeId}, node id: ${node.id}",
            context.session
        )

        val runner = when (node.nodeId) {
            NodeId.Teleport -> {
                WebSocketDebug.logMessage(
                    "TELEPORT_RUNNER_SELECTED",
                    "Selected TeleportNodeRunner for node: ${node.id}",
                    context.session
                )
                TeleportNodeRunner(context)
            }

            NodeId.Dialogue -> {
                WebSocketDebug.logMessage(
                    "DIALOGUE_RUNNER_SELECTED",
                    "Selected DialogueNodeRunner for node: ${node.id}",
                    context.session
                )
                DialogueNodeRunner(context)
            }

            NodeId.UserLanguage -> {
                WebSocketDebug.logMessage(
                    "LANGUAGE_RUNNER_SELECTED",
                    "Selected LanguageNodeRunner for node: ${node.id}",
                    context.session
                )
                LanguageNodeRunner(context)
            }

            NodeId.HasItem -> {
                WebSocketDebug.logMessage(
                    "HAS_ITEM_RUNNER_SELECTED",
                    "Selected HasItemNodeRunner for node: ${node.id}",
                    context.session
                )
                HasItemNodeRunner(context)
            }
            
            NodeId.Delay -> {
                WebSocketDebug.logMessage(
                    "DELAY_RUNNER_SELECTED",
                    "Selected DelayNodeRunner for node: ${node.id}",
                    context.session
                )
                DelayNodeRunner(context)
            }

            NodeId.OpenUrl -> {
                WebSocketDebug.logMessage(
                    "OPEN_URL_RUNNER_SELECTED",
                    "Selected OpenUrlNodeRunner for node: ${node.id}",
                    context.session
                )
                OpenUrlNodeRunner(context)
            }
            
            NodeId.ObjectNotification -> {
                WebSocketDebug.logMessage(
                    "OBJECT_NOTIFICATION_RUNNER_SELECTED",
                    "Selected ObjectNotificationNodeRunner for node: ${node.id}",
                    context.session
                )
                ObjectNotificationNodeRunner(context)
            }
            NodeId.ObjectOwnerNotification -> {
                WebSocketDebug.logMessage(
                    "OBJECT_OWNER_NOTIFICATION_RUNNER_SELECTED",
                    "Selected ObjectOwnerNotificationNodeRunner for node: ${node.id}",
                    context.session
                )
                ObjectOwnerNotificationNodeRunner(context)
            }
            NodeId.TakeItems -> {
                WebSocketDebug.logMessage(
                    "TAKE_ITEMS_RUNNER_SELECTED",
                    "Selected TakeItemsNodeRunner for node: ${node.id}",
                    context.session
                )
                TakeItemsNodeRunner(context)
            }
            // Add more node runners as needed
            else -> {
                WebSocketDebug.logMessage(
                    "NO_RUNNER_FOUND",
                    "No specific runner found for node category: ${node.nodeCategory}, id: ${node.nodeId}. Using NoOpNodeRunner",
                    context.session
                )
                NoOpNodeRunner()
            }
        }

        return runner
    }
}
