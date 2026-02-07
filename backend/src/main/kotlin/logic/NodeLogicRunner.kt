package logic

import debug.WebSocketDebug
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import models.LogicNode
import models.NodeId
import models.Object
import models.nodeeditor.NodeConfig
import models.nodeeditor.NodeConnection
import models.nodeeditor.NodeDataModel
import models.nodeeditor.NodeEditorJsonUtil
import models.nodeeditor.PropertyKey
import models.nodeeditor.PropertyType
import models.nodeeditor.getOutputPropertyDefinitions
import models.nodeeditor.getPropertyDefinitions
import models.nodeeditor.PropertyDirection

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
                is LogicNode.PlayerNearby -> node.nodeId == NodeId.PlayerNearby
                is LogicNode.VisitorNearby -> node.nodeId == NodeId.VisitorNearby
                is LogicNode.PlayerJoined -> node.nodeId == NodeId.PlayerJoined
                is LogicNode.VisitorJoined -> node.nodeId == NodeId.VisitorJoined
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
            val executedNodes = mutableSetOf<String>()
            WebSocketDebug.logMessage(
                "PROCESSING_START_NODE",
                "Processing start node: ${startNode.nodeCategory}/${startNode.id}",
                context.session
            )
            processNode(startNode, mutableEventData, executedNodes)
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
    private suspend fun processNode(node: NodeDataModel, eventData: MutableMap<String, Any>, executedNodes: MutableSet<String>) {
        fun reconstructLogicNode(node: NodeDataModel): LogicNode {
            return when (node.nodeId) {
                NodeId.Teleport -> LogicNode.Teleport()
                NodeId.VisitorInteract -> LogicNode.VisitorInteract()
                NodeId.PlayerInteract -> LogicNode.PlayerInteract()
                NodeId.VisitorNearby -> LogicNode.VisitorNearby()
                NodeId.PlayerNearby -> LogicNode.PlayerNearby()
                NodeId.PlayerJoined -> LogicNode.PlayerJoined()
                NodeId.VisitorJoined -> LogicNode.VisitorJoined()
                NodeId.Ai -> LogicNode.Ai()
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
                NodeId.SetStatus -> LogicNode.SetStatus()
                NodeId.PlayerInput -> LogicNode.PlayerInput()
                NodeId.StringTemplate -> LogicNode.StringTemplate()
                NodeId.PlayerName -> LogicNode.PlayerName()
                NodeId.CreateDoc -> LogicNode.CreateDoc()
                NodeId.DocContent -> LogicNode.DocContent()
                NodeId.IsObjectOwner -> LogicNode.IsObjectOwner()
            }
        }
        fun getOutputPropertyTypes(node: NodeDataModel): List<PropertyType> {
            val logicNode = reconstructLogicNode(node)
            return logicNode.getOutputPropertyDefinitions().map { it.type }
        }
        fun isTriggerConnection(node: NodeDataModel, fromPort: String): Boolean {
            // Dynamic dialogue button outputs are always triggers
            if (fromPort.contains("_out_button_")) return true
            val match = Regex("_out_(\\d+)$").find(fromPort) ?: return false
            val index = match.groupValues[1].toIntOrNull() ?: return false
            val types = getOutputPropertyTypes(node)
            val t = types.getOrNull(index)
            return t == PropertyType.TRIGGER
        }
        fun propagateValue(fromPort: String, toPort: String) {
            val value = eventData[fromPort]
            if (value != null) {
                WebSocketDebug.logMessage(
                    "VALUE_PROPAGATION",
                    "Propagating value from $fromPort to $toPort",
                    context.session
                )
                eventData[toPort] = value
            }
        }
        WebSocketDebug.logMessage(
            "PROCESSING_NODE",
            "Processing node: ${node.nodeCategory}/${node.id} with data: ${node.data}",
            context.session
        )

        // If we are looping back to a node that already ran in this traversal, clear its input values
        // and invalidate upstream pure value outputs so they are re-evaluated (e.g., AI nodes).
        fun clearUpstreamOutputsFor(target: NodeDataModel, visited: MutableSet<String>) {
            if (visited.contains(target.id)) return
            visited.add(target.id)

            val incoming = nodeConfig?.connections?.filter { it.to == target.id } ?: emptyList()
            for (conn in incoming) {
                // Clear the target input port value
                if (eventData.remove(conn.toPort) != null) {
                    WebSocketDebug.logMessage(
                        "CLEAR_INPUT",
                        "Cleared input ${conn.toPort} for target ${target.id}",
                        context.session
                    )
                }

                val srcNode = nodeConfig?.nodes?.find { it.id == conn.from }
                if (srcNode != null) {
                    val srcLogicNode = reconstructLogicNode(srcNode)
                    if (srcLogicNode.getPropertyDefinitions().none { it.type == PropertyType.TRIGGER }) {
                        // Invalidate the specific output from the pure value source
                        if (eventData.remove(conn.fromPort) != null) {
                            WebSocketDebug.logMessage(
                                "INVALIDATE_UPSTREAM_OUTPUT",
                                "Invalidated upstream pure output ${conn.fromPort} from ${srcNode.id}",
                                context.session
                            )
                        }
                        // Special handling for AI node additional alias outputs
                        if (srcNode.nodeId == NodeId.Ai) {
                            val aiTextKey = "${srcNode.id}_text"
                            if (eventData.remove(aiTextKey) != null) {
                                WebSocketDebug.logMessage(
                                    "INVALIDATE_AI_TEXT",
                                    "Invalidated AI text alias $aiTextKey",
                                    context.session
                                )
                            }
                        }
                        // Recurse further upstream
                        clearUpstreamOutputsFor(srcNode, visited)
                    }
                }
            }
        }

        if (executedNodes.contains(node.id)) {
            WebSocketDebug.logMessage(
                "REENTER_NODE_CLEARING",
                "Re-entering node ${node.id}; clearing inputs and upstream pure outputs",
                context.session
            )
            clearUpstreamOutputsFor(node, mutableSetOf())
        }

        // On-demand evaluation: ensure inputs provided by pure value nodes are available before executing this node
        fun isPureValueNode(ln: models.LogicNode): Boolean =
            ln.getPropertyDefinitions().none { it.type == PropertyType.TRIGGER }

        suspend fun ensureInputValuesFor(target: NodeDataModel, resolving: MutableSet<String>) {
            if (resolving.contains(target.id)) return // prevent cycles
            resolving.add(target.id)

            val incoming = nodeConfig?.connections?.filter { it.to == target.id } ?: emptyList()
            for (conn in incoming) {
                // Skip if target already has a value for this input
                if (eventData[conn.toPort] != null) continue

                val srcNode = nodeConfig?.nodes?.find { it.id == conn.from } ?: continue
                val srcLogicNode = reconstructLogicNode(srcNode)

                // 1) If the source already produced a value (regardless of being pure/non‑pure), propagate it
                eventData[conn.fromPort]?.let { existing ->
                    WebSocketDebug.logMessage(
                        "VALUE_PROPAGATION",
                        "Propagating existing value from ${conn.fromPort} to ${conn.toPort}",
                        context.session
                    )
                    eventData[conn.toPort] = existing
                    continue
                }

                // 2) If the source is a pure value node, we can execute it on demand
                if (isPureValueNode(srcLogicNode)) {
                    // Recursively ensure inputs for the source node before executing it
                    ensureInputValuesFor(srcNode, resolving)

                    // Execute the source value node on demand
                    val srcRunner = getNodeRunner(srcNode)
                    WebSocketDebug.logMessage(
                        "ON_DEMAND_EXEC",
                        "Executing value node ${srcNode.nodeId} for target ${target.id}",
                        context.session
                    )
                    val ok = srcRunner.execute(srcNode, eventData)
                    if (ok) {
                        val v = eventData[conn.fromPort]
                        if (v != null) {
                            WebSocketDebug.logMessage(
                                "ON_DEMAND_PROPAGATE",
                                "Propagating value from ${conn.fromPort} to ${conn.toPort}",
                                context.session
                            )
                            eventData[conn.toPort] = v
                        }
                    }
                } else {
                    // 3) Non‑pure source without a ready value: cannot auto‑execute; leave unset
                    WebSocketDebug.logMessage(
                        "INPUT_VALUE_MISSING",
                        "Input ${conn.toPort} expects value from ${conn.fromPort}, but source is non‑pure and has no value yet",
                        context.session
                    )
                }
            }

            resolving.remove(target.id)
        }

        // Fill inputs from connected pure value sources
        val resolving = mutableSetOf<String>()
        ensureInputValuesFor(node, resolving)

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
            // Mark this node as executed in this traversal so re-entry can clear inputs
            executedNodes.add(node.id)
            // Find connections from this node
            val connections = nodeConfig?.connections?.filter { it.from == node.id } ?: emptyList()
            WebSocketDebug.logMessage(
                "NODE_CONNECTIONS",
                "Found ${connections.size} connections from node: ${node.id}",
                context.session
            )

            // For dialogue and language nodes, follow the connection that matches the button index
            // For HasItem nodes, follow the connection that matches the output property
            if (node.nodeId in listOf(NodeId.Dialogue, NodeId.UserLanguage, NodeId.UserSex)) {
                // Get the button index from the event data (default to 0 if not present)
                val buttonIndex = (eventData["buttonIndex"] as? Int) ?: 0
                WebSocketDebug.logMessage(
                    "DIALOGUE_BUTTON_INDEX",
                    "Using button index: $buttonIndex for node: ${node.id}",
                    context.session
                )

                // Use the button index to select a connection
                // Prefer dynamic dialogue outputs, but fall back to static outputs
                val connection = connections.find { c -> c.fromPort.endsWith("_out_button_$buttonIndex") }
                    ?: connections.find { c -> c.fromPort.endsWith("_out_$buttonIndex") }

                if (connection != null) {
                    WebSocketDebug.logMessage(
                        "FOLLOWING_HAS_ITEM_CONNECTION",
                        "Following connection from ${connection.from} to ${connection.to} for buttonIndex: $buttonIndex",
                        context.session
                    )

                    // Always propagate any value carried on this connection
                    propagateValue(connection.fromPort, connection.toPort)

                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage(
                            "TARGET_NODE_FOUND",
                            "Found target node: ${targetNode.nodeCategory}/${targetNode.id}",
                            context.session
                        )
                        // Only traverse if this is a trigger connection
                        if (isTriggerConnection(node, connection.fromPort)) {
                            processNode(targetNode, eventData, executedNodes)
                        }
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
            } else if (node.nodeId == NodeId.HasItem || node.nodeId == NodeId.TakeItems || node.nodeId == NodeId.GiveItems || node.nodeId == NodeId.UserAge || node.nodeId == NodeId.IsObjectOwner) {
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

                    // Propagate any value carried on this connection
                    propagateValue(connection.fromPort, connection.toPort)

                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to }
                    if (targetNode != null) {
                        WebSocketDebug.logMessage(
                            "TARGET_NODE_FOUND",
                            "Found target node: ${targetNode.nodeCategory}/${targetNode.id}",
                            context.session
                        )
                        if (isTriggerConnection(node, connection.fromPort)) {
                            processNode(targetNode, eventData, executedNodes)
                        }
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
                // For other node types, do a two-pass approach:
                // 1) Propagate ALL values across connections first
                // 2) Then traverse ONLY trigger connections
                // This ensures targets receive value inputs before they are processed via a trigger.
                // Pass 1: propagate values
                for (connection in connections) {
                    propagateValue(connection.fromPort, connection.toPort)
                }
                // Pass 2: traverse triggers concurrently
                coroutineScope {
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
                            if (isTriggerConnection(node, connection.fromPort)) {
                                // Launch each trigger-connected branch concurrently with isolated state
                                val branchEventData = eventData.toMutableMap()
                                val branchExecuted = executedNodes.toMutableSet()
                                launch {
                                    processNode(targetNode, branchEventData, branchExecuted)
                                }
                            } else {
                                WebSocketDebug.logMessage(
                                    "SKIP_NON_TRIGGER_CONNECTION",
                                    "Skipping non-trigger connection fromPort=${connection.fromPort} toPort=${connection.toPort} for node=${node.id}",
                                    context.session
                                )
                            }
                        } else {
                            WebSocketDebug.logError(
                                "Target node not found for connection: ${connection.from} -> ${connection.to}",
                                null,
                                context.session
                            )
                        }
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

            NodeId.UserSex -> {
                WebSocketDebug.logMessage(
                    "SEX_RUNNER_SELECTED",
                    "Selected SexNodeRunner for node: ${node.id}",
                    context.session
                )
                SexNodeRunner(context)
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
            NodeId.GiveItems -> {
                WebSocketDebug.logMessage(
                    "GIVE_ITEMS_RUNNER_SELECTED",
                    "Selected GiveItemsNodeRunner for node: ${node.id}",
                    context.session
                )
                GiveItemsNodeRunner(context)
            }
            NodeId.UserAge -> {
                WebSocketDebug.logMessage(
                    "AGE_RUNNER_SELECTED",
                    "Selected AgeNodeRunner for node: ${node.id}",
                    context.session
                )
                AgeNodeRunner(context)
            }
            NodeId.SendChat -> {
                WebSocketDebug.logMessage(
                    "SEND_CHAT_RUNNER_SELECTED",
                    "Selected SendChatNodeRunner for node: ${node.id}",
                    context.session
                )
                SendChatNodeRunner(context)
            }
            NodeId.SetStatus -> {
                WebSocketDebug.logMessage(
                    "SET_STATUS_RUNNER_SELECTED",
                    "Selected SetStatusNodeRunner for node: ${node.id}",
                    context.session
                )
                SetStatusNodeRunner(context)
            }
            NodeId.PlayerInput -> {
                WebSocketDebug.logMessage(
                    "PLAYER_INPUT_RUNNER_SELECTED",
                    "Selected PlayerInputNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerInputNodeRunner(context)
            }
            NodeId.StringTemplate -> {
                WebSocketDebug.logMessage(
                    "STRING_TEMPLATE_RUNNER_SELECTED",
                    "Selected StringTemplateNodeRunner for node: ${node.id}",
                    context.session
                )
                StringTemplateNodeRunner(context)
            }
            NodeId.PlayerName -> {
                WebSocketDebug.logMessage(
                    "PLAYER_NAME_RUNNER_SELECTED",
                    "Selected PlayerNameNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerNameNodeRunner(context)
            }
            NodeId.Ai -> {
                WebSocketDebug.logMessage(
                    "AI_RUNNER_SELECTED",
                    "Selected AiNodeRunner for node: ${node.id}",
                    context.session
                )
                AiNodeRunner(context)
            }
            NodeId.DocContent -> {
                WebSocketDebug.logMessage(
                    "DOC_CONTENT_RUNNER_SELECTED",
                    "Selected DocContentNodeRunner for node: ${node.id}",
                    context.session
                )
                DocContentNodeRunner(context)
            }
            NodeId.CreateDoc -> {
                WebSocketDebug.logMessage(
                    "CREATE_DOC_RUNNER_SELECTED",
                    "Selected CreateDocNodeRunner for node: ${node.id}",
                    context.session
                )
                CreateDocNodeRunner(context)
            }
            NodeId.IsObjectOwner -> {
                WebSocketDebug.logMessage(
                    "IS_OBJECT_OWNER_RUNNER_SELECTED",
                    "Selected IsObjectOwnerNodeRunner for node: ${node.id}",
                    context.session
                )
                IsObjectOwnerNodeRunner(context)
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
