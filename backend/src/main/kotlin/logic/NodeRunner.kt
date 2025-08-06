package logic

/**
 * Interface for node runners
 *
 * Each node type has its own runner that implements this interface.
 * Node runners are responsible for executing the logic of a specific node type.
 */
interface NodeRunner {
    /**
     * Execute the node's logic
     *
     * @param node The node to execute
     * @param eventData Additional data related to the event (mutable)
     * @return True if the node execution was successful, false otherwise
     */
    suspend fun execute(node: NodeData, eventData: MutableMap<String, Any>): Boolean
}
