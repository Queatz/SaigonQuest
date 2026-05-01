package logic

import debug.WebSocketDebug
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import models.LogicNode
import models.logicNodeById
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
        context.logicRunner = this
        WebSocketDebug.logMessage(
            "NODE_LOGIC_RUNNER_INIT",
            "Initializing for object title: ${objectData.title}, worldId: ${objectData.worldId}",
            context.session
        )
    }

    // Parse the node configuration from the object data
    internal val nodeConfig: NodeConfig? = try {
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

    fun isPortConnected(nodeId: String, portId: String): Boolean {
        return nodeConfig?.connections?.any { it.from == nodeId && it.fromPort == portId } ?: false
    }

    /**
     * Enum to determine how a node selects its next branches
     */
    private enum class BranchingMode {
        INDEX,      // Branch based on buttonIndex or isWordClick
        PROPERTY,   // Branch based on outputProperty (YES, NO, etc.)
        ALL         // Follow all trigger connections
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
                is LogicNode.PlayerFar -> node.nodeId == NodeId.PlayerFar
                is LogicNode.VisitorFar -> node.nodeId == NodeId.VisitorFar
                is LogicNode.PlayerLeave -> node.nodeId == NodeId.PlayerLeave
                is LogicNode.VisitorLeave -> node.nodeId == NodeId.VisitorLeave
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
                    val srcLogicNode = logicNodeById[srcNode.nodeId]!!
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
                val srcLogicNode = logicNodeById[srcNode.nodeId]!!

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
        val result = executeNode(node, eventData)

        // If the node execution was successful, follow its connections
        if (result) {
            // Mark this node as executed in this traversal so re-entry can clear inputs
            executedNodes.add(node.id)
            
            // Get branching signals and THEN clear them from eventData so they don't leak to next nodes
            val outputProperty = eventData.remove("outputProperty") as? PropertyKey
            val buttonIndex = eventData.remove("buttonIndex") as? Int
            val isWordClick = eventData.remove("isWordClick") as? Boolean ?: false

            // Find connections from this node
            val connections = nodeConfig?.connections?.filter { it.from == node.id } ?: emptyList()
            WebSocketDebug.logMessage(
                "NODE_CONNECTIONS",
                "Found ${connections.size} connections from node: ${node.id}",
                context.session
            )

            // 1) Pass 1: Propagate ALL non-trigger values across connections first.
            // This ensures target nodes receive their input values before being triggered.
            propagateNonTriggerValues(node, connections, eventData)

            // 2) Pass 2: Identify matching trigger connections and follow them
            handleBranching(
                node = node,
                connections = connections,
                eventData = eventData,
                executedNodes = executedNodes,
                outputProperty = outputProperty,
                buttonIndex = buttonIndex,
                isWordClick = isWordClick
            )
        } else {
            WebSocketDebug.logMessage(
                "NODE_EXECUTION_STOPPED",
                "Node execution returned false, stopping branch at ${node.nodeCategory}/${node.id}",
                context.session
            )
        }
    }

    /**
     * Executes the logic runner for a node
     */
    private suspend fun executeNode(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val nodeRunner = NodeRunnerFactory.getNodeRunner(node, context)
        WebSocketDebug.logMessage(
            "NODE_RUNNER_SELECTED",
            "Selected runner: ${nodeRunner::class.simpleName} for node: ${node.nodeCategory}/${node.id}",
            context.session
        )

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
        return result
    }

    /**
     * Propagates all non-trigger values from a node to its connected target ports
     */
    private fun propagateNonTriggerValues(
        node: NodeDataModel,
        connections: List<NodeConnection>,
        eventData: MutableMap<String, Any>
    ) {
        for (connection in connections) {
            if (!isTriggerConnection(node, connection.fromPort)) {
                propagateValue(connection.fromPort, connection.toPort, eventData)
            }
        }
    }

    /**
     * Handles the branching logic after a node has executed successfully
     */
    private suspend fun handleBranching(
        node: NodeDataModel,
        connections: List<NodeConnection>,
        eventData: MutableMap<String, Any>,
        executedNodes: MutableSet<String>,
        outputProperty: PropertyKey?,
        buttonIndex: Int?,
        isWordClick: Boolean
    ) {
        // 1) Determine the branching mode for this node
        val mode = when {
            // Nodes that explicitly set an outputProperty want property-based branching
            outputProperty != null -> BranchingMode.PROPERTY
            
            // Dialogue and user-info nodes are inherently index-based selectors
            node.nodeId in listOf(NodeId.Dialogue, NodeId.UserLanguage, NodeId.UserSex) -> BranchingMode.INDEX
            
            // If buttonIndex is present for some other reason, treat as index-based
            buttonIndex != null || isWordClick -> BranchingMode.INDEX
            
            // Default: follow all trigger connections
            else -> BranchingMode.ALL
        }

        // 2) Identify matching trigger connections based on the mode
        val matchingConnections = when (mode) {
            BranchingMode.INDEX -> {
                val idx = if (isWordClick) -1 else (buttonIndex ?: 0)
                WebSocketDebug.logMessage("BRANCHING_INDEX", "Using index: $idx (isWordClick: $isWordClick)", context.session)
                
                if (isWordClick && node.nodeId == NodeId.Dialogue) {
                    connections.filter { it.fromPort.endsWith("_out_word_clicked") }
                } else {
                    // Prefer dynamic dialogue outputs (_out_button_N), fall back to indexed outputs (_out_N)
                    val dynamic = connections.filter { c -> c.fromPort.endsWith("_out_button_$idx") }
                    if (dynamic.isNotEmpty()) dynamic else connections.filter { c -> c.fromPort.endsWith("_out_$idx") }
                }
            }
            
            BranchingMode.PROPERTY -> {
                WebSocketDebug.logMessage("BRANCHING_PROPERTY", "Using property: $outputProperty", context.session)
                
                val expectedIndex = getOutputPropertyIndex(node, outputProperty!!)
                
                connections.filter { connection ->
                    if (!isTriggerConnection(node, connection.fromPort)) return@filter false
                    
                    if (expectedIndex != -1) {
                        connection.fromPort.endsWith("_out_$expectedIndex")
                    } else {
                        // Fallback for hardcoded common keys
                        when (outputProperty) {
                            PropertyKey.YES, PropertyKey.NEXT -> connection.fromPort.endsWith("_out_0")
                            PropertyKey.NO, PropertyKey.NO_IMAGES, PropertyKey.NO_AUDIO -> connection.fromPort.endsWith("_out_1")
                            PropertyKey.NOT_ENOUGH -> connection.fromPort.endsWith("_out_2")
                            else -> false
                        }
                    }
                }
            }
            
            BranchingMode.ALL -> {
                connections.filter { isTriggerConnection(node, it.fromPort) }
            }
        }

        // 3) Follow the selected trigger branches concurrently
        if (matchingConnections.isNotEmpty()) {
            coroutineScope {
                for (connection in matchingConnections) {
                    val targetNode = nodeConfig?.nodes?.find { it.id == connection.to } ?: continue
                    
                    WebSocketDebug.logMessage(
                        "FOLLOWING_CONNECTION",
                        "Following connection from ${connection.from} to ${connection.to} (Mode: $mode)",
                        context.session
                    )
                    
                    launch {
                        val branchEventData = eventData.toMutableMap()
                        val branchExecuted = executedNodes.toMutableSet()
                        // Propagate any value carried on this trigger connection itself
                        propagateValue(connection.fromPort, connection.toPort, branchEventData)
                        processNode(targetNode, branchEventData, branchExecuted)
                    }
                }
            }
        } else if (mode != BranchingMode.ALL) {
            WebSocketDebug.logMessage(
                "NO_MATCHING_BRANCH",
                "No branch found for mode $mode on node: ${node.id}",
                context.session
            )
        }
    }

    /**
     * Triggers a specific output branch of a node.
     * Useful for asynchronous events like word clicks in dialogues.
     */
    suspend fun triggerBranch(nodeId: String, fromPortId: String, additionalEventData: Map<String, Any>) {
        val node = nodeConfig?.nodes?.find { it.id == nodeId } ?: return
        val connections = nodeConfig.connections.filter { it.from == nodeId && it.fromPort == fromPortId }

        WebSocketDebug.logMessage(
            "TRIGGER_BRANCH",
            "Triggering branch for node $nodeId from port $fromPortId with ${connections.size} connections",
            context.session
        )

        coroutineScope {
            for (connection in connections) {
                val targetNode = nodeConfig.nodes.find { it.id == connection.to } ?: continue
                val branchEventData = additionalEventData.toMutableMap()
                
                // Propagate values for the triggered connection
                propagateValue(connection.fromPort, connection.toPort, branchEventData)

                launch {
                    processNode(targetNode, branchEventData, mutableSetOf())
                }
            }
        }
    }


    private fun getOutputPropertyTypes(node: NodeDataModel): List<PropertyType> {
        val logicNode = logicNodeById[node.nodeId]!!
        return logicNode.getOutputPropertyDefinitions().map { it.type }
    }

    private fun getOutputPropertyIndex(node: NodeDataModel, key: PropertyKey): Int {
        val logicNode = logicNodeById[node.nodeId] ?: return -1
        return logicNode.getOutputPropertyDefinitions().indexOfFirst { it.key == key }
    }

    private fun isTriggerConnection(node: NodeDataModel, fromPort: String): Boolean {
        // Dynamic dialogue button outputs are always triggers
        if (fromPort.contains("_out_button_")) return true
        // Named triggers (like word_clicked)
        if (fromPort.endsWith("_out_word_clicked")) return true

        val match = Regex("_out_(\\d+)$").find(fromPort)
        if (match != null) {
            val index = match.groupValues[1].toIntOrNull() ?: return false
            val types = getOutputPropertyTypes(node)
            val t = types.getOrNull(index)
            return t == PropertyType.TRIGGER
        }
        return false
    }

    private fun propagateValue(fromPort: String, toPort: String, eventData: MutableMap<String, Any>) {
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

    /**
     * Get the appropriate node runner for a node
     *
     * @param node The node to get a runner for
     * @return The node runner
     */
    private fun getNodeRunner(node: NodeDataModel): NodeRunner {
        // Implementation moved to NodeRunnerFactory
        return NodeRunnerFactory.getNodeRunner(node, context)
    }
}
