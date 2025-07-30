package main.components.nodeeditor

/**
 * Interface for all node editor commands.
 * Commands represent operations that can be undone and redone.
 */
interface NodeEditorCommand {
    /**
     * Execute the command.
     * 
     * @return The result of the command execution
     */
    fun execute(): Any?
    
    /**
     * Undo the command.
     * 
     * @return The result of the command undoing
     */
    fun undo(): Any?
    
    /**
     * Get a description of the command for debugging.
     * 
     * @return A string describing the command
     */
    fun getDescription(): String
}

/**
 * Command for adding a node.
 * 
 * @param nodes The current nodes map
 * @param nodeId The ID of the node to add
 * @param node The node data to add
 */
class AddNodeCommand(
    private val nodes: Map<String, NodeData>,
    private val nodeId: String,
    private val node: NodeData
) : NodeEditorCommand {
    override fun execute(): Map<String, NodeData> {
        return nodes + (nodeId to node)
    }
    
    override fun undo(): Map<String, NodeData> {
        val updatedNodes = nodes.toMutableMap()
        updatedNodes.remove(nodeId)
        return updatedNodes
    }
    
    override fun getDescription(): String {
        return "Add node: $nodeId (${node.type})"
    }
}

/**
 * Command for deleting a node.
 * 
 * @param nodes The current nodes map
 * @param connections The current connections list
 * @param nodeId The ID of the node to delete
 */
class DeleteNodeCommand(
    private val nodes: Map<String, NodeData>,
    private val connections: List<Connection>,
    private val nodeId: String
) : NodeEditorCommand {
    private val nodeToDelete = nodes[nodeId]
    private val connectionsToDelete = connections.filter { 
        it.sourceId == nodeId || it.targetId == nodeId 
    }
    
    override fun execute(): Pair<Map<String, NodeData>, List<Connection>> {
        val updatedNodes = nodes.toMutableMap()
        updatedNodes.remove(nodeId)
        
        val updatedConnections = connections.filter { 
            it.sourceId != nodeId && it.targetId != nodeId 
        }
        
        return Pair(updatedNodes, updatedConnections)
    }
    
    override fun undo(): Pair<Map<String, NodeData>, List<Connection>> {
        val updatedNodes = if (nodeToDelete != null) {
            nodes + (nodeId to nodeToDelete)
        } else {
            nodes
        }
        
        val updatedConnections = connections + connectionsToDelete
        
        return Pair(updatedNodes, updatedConnections)
    }
    
    override fun getDescription(): String {
        return "Delete node: $nodeId"
    }
}

/**
 * Command for updating a node.
 * 
 * @param nodes The current nodes map
 * @param nodeId The ID of the node to update
 * @param newNode The new node data
 */
class UpdateNodeCommand(
    private val nodes: Map<String, NodeData>,
    private val nodeId: String,
    private val newNode: NodeData
) : NodeEditorCommand {
    private val oldNode = nodes[nodeId]
    
    override fun execute(): Map<String, NodeData> {
        return nodes + (nodeId to newNode)
    }
    
    override fun undo(): Map<String, NodeData> {
        return if (oldNode != null) {
            nodes + (nodeId to oldNode)
        } else {
            nodes
        }
    }
    
    override fun getDescription(): String {
        return "Update node: $nodeId"
    }
}

/**
 * Command for adding a connection.
 * 
 * @param connections The current connections list
 * @param connection The connection to add
 */
class AddConnectionCommand(
    private val connections: List<Connection>,
    private val connection: Connection
) : NodeEditorCommand {
    override fun execute(): List<Connection> {
        return connections + connection
    }
    
    override fun undo(): List<Connection> {
        return connections.filter { it != connection }
    }
    
    override fun getDescription(): String {
        return "Add connection: ${connection.sourceId}:${connection.sourcePortId} -> ${connection.targetId}:${connection.targetPortId}"
    }
}

/**
 * Command for deleting a connection.
 * 
 * @param connections The current connections list
 * @param connection The connection to delete
 */
class DeleteConnectionCommand(
    private val connections: List<Connection>,
    private val connection: Connection
) : NodeEditorCommand {
    override fun execute(): List<Connection> {
        return connections.filter { it != connection }
    }
    
    override fun undo(): List<Connection> {
        return connections + connection
    }
    
    override fun getDescription(): String {
        return "Delete connection: ${connection.sourceId}:${connection.sourcePortId} -> ${connection.targetId}:${connection.targetPortId}"
    }
}

/**
 * Command for moving a node.
 * 
 * @param nodes The current nodes map
 * @param nodeId The ID of the node to move
 * @param newX The new X coordinate
 * @param newY The new Y coordinate
 */
class MoveNodeCommand(
    private val nodes: Map<String, NodeData>,
    private val nodeId: String,
    private val newX: Float,
    private val newY: Float
) : NodeEditorCommand {
    private val oldNode = nodes[nodeId]
    private val oldX = oldNode?.x ?: 0f
    private val oldY = oldNode?.y ?: 0f
    
    override fun execute(): Map<String, NodeData> {
        val node = nodes[nodeId] ?: return nodes
        val updatedNode = node.copy(x = newX, y = newY)
        return nodes + (nodeId to updatedNode)
    }
    
    override fun undo(): Map<String, NodeData> {
        val node = nodes[nodeId] ?: return nodes
        val updatedNode = node.copy(x = oldX, y = oldY)
        return nodes + (nodeId to updatedNode)
    }
    
    override fun getDescription(): String {
        return "Move node: $nodeId to ($newX, $newY)"
    }
}

/**
 * History manager for tracking and managing undo/redo operations.
 */
class NodeEditorHistory {
    private val undoStack = mutableListOf<NodeEditorCommand>()
    private val redoStack = mutableListOf<NodeEditorCommand>()
    
    /**
     * Execute a command and add it to the history.
     * 
     * @param command The command to execute
     * @return The result of the command execution
     */
    fun executeCommand(command: NodeEditorCommand): Any? {
        val result = command.execute()
        undoStack.add(command)
        redoStack.clear() // Clear redo stack when a new command is executed
        return result
    }
    
    /**
     * Undo the last command.
     * 
     * @return The result of the command undoing, or null if there's nothing to undo
     */
    fun undo(): Any? {
        if (undoStack.isEmpty()) return null
        
        val command = undoStack.removeAt(undoStack.size - 1)
        val result = command.undo()
        redoStack.add(command)
        return result
    }
    
    /**
     * Redo the last undone command.
     * 
     * @return The result of the command execution, or null if there's nothing to redo
     */
    fun redo(): Any? {
        if (redoStack.isEmpty()) return null
        
        val command = redoStack.removeAt(redoStack.size - 1)
        val result = command.execute()
        undoStack.add(command)
        return result
    }
    
    /**
     * Check if there are commands that can be undone.
     * 
     * @return True if there are commands that can be undone, false otherwise
     */
    fun canUndo(): Boolean {
        return undoStack.isNotEmpty()
    }
    
    /**
     * Check if there are commands that can be redone.
     * 
     * @return True if there are commands that can be redone, false otherwise
     */
    fun canRedo(): Boolean {
        return redoStack.isNotEmpty()
    }
    
    /**
     * Get the description of the last command that can be undone.
     * 
     * @return The description of the last command, or null if there's nothing to undo
     */
    fun getUndoDescription(): String? {
        return if (undoStack.isNotEmpty()) {
            undoStack.last().getDescription()
        } else {
            null
        }
    }
    
    /**
     * Get the description of the last command that can be redone.
     * 
     * @return The description of the last command, or null if there's nothing to redo
     */
    fun getRedoDescription(): String? {
        return if (redoStack.isNotEmpty()) {
            redoStack.last().getDescription()
        } else {
            null
        }
    }
    
    /**
     * Clear the history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
