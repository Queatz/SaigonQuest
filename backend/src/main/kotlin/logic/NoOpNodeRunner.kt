package logic

/**
 * No-op node runner for unsupported node types
 *
 * This node runner does nothing and is used for unsupported node types.
 * It always returns true to allow the node execution to continue.
 */
class NoOpNodeRunner : NodeRunner {
    override suspend fun execute(node: NodeData, eventData: Map<String, Any>): Boolean {
        // Do nothing
        return true
    }
}
