package models

import kotlinx.serialization.Serializable

/**
 * Enum representing the category of a logic node
 */
@Serializable
enum class NodeCategory {
    EVENT,
    ACTION,
    DIALOGUE,
    INPUT
}

/**
 * Sealed interface representing a logic node type
 */
@Serializable
sealed interface LogicNode {
    /**
     * The unique identifier for this node type
     */
    val id: String
    
    /**
     * The display label for this node type
     */
    val label: String
    
    /**
     * The category this node belongs to
     */
    val category: NodeCategory
    
    /**
     * A short description of what this node does
     */
    val description: String
    
    /**
     * Teleport node - moves player to a different location
     */
    @Serializable
    data class Teleport(
        override val id: String = "teleport",
        override val label: String = "Teleport player",
        override val category: NodeCategory = NodeCategory.ACTION,
        override val description: String = "Move player to a different location"
    ) : LogicNode
    
    /**
     * VisitorInteract node - triggers when a visitor interacts with this object
     */
    @Serializable
    data class VisitorInteract(
        override val id: String = "visitor_interact",
        override val label: String = "Visitor interact",
        override val category: NodeCategory = NodeCategory.EVENT,
        override val description: String = "Triggers when a visitor interacts with this object"
    ) : LogicNode
    
    /**
     * PlayerInteract node - triggers when the player interacts with this object
     */
    @Serializable
    data class PlayerInteract(
        override val id: String = "player_interact",
        override val label: String = "Player interact",
        override val category: NodeCategory = NodeCategory.EVENT,
        override val description: String = "Triggers when the player interacts with this object"
    ) : LogicNode
    
    /**
     * Dialogue node - displays text message to the player
     */
    @Serializable
    data class Dialogue(
        override val id: String = "dialogue",
        override val label: String = "Present text",
        override val category: NodeCategory = NodeCategory.ACTION,
        override val description: String = "Display text message to the player"
    ) : LogicNode
    
    /**
     * UserLanguage node - directs logic based on the user's language
     */
    @Serializable
    data class UserLanguage(
        override val id: String = "user_language",
        override val label: String = "User language",
        override val category: NodeCategory = NodeCategory.INPUT,
        override val description: String = "Directs logic based on the user's language"
    ) : LogicNode
}

/**
 * Extension function to get all available node types
 */
fun getAllNodeTypes(): List<LogicNode> {
    return listOf(
        LogicNode.PlayerInteract(),
        LogicNode.VisitorInteract(),
        LogicNode.Teleport(),
        LogicNode.Dialogue(),
        LogicNode.UserLanguage()
    )
}

/**
 * Extension function to get node types by category
 */
fun getNodeTypesByCategory(): Map<NodeCategory, List<LogicNode>> {
    return getAllNodeTypes().groupBy { it.category }
}

/**
 * Extension function to convert LogicNode to String representation
 */
fun LogicNode.toTypeString(): String {
    return when (this) {
        is LogicNode.Teleport -> "action"
        is LogicNode.VisitorInteract -> "event"
        is LogicNode.PlayerInteract -> "event"
        is LogicNode.Dialogue -> "dialogue"
        is LogicNode.UserLanguage -> "input"
    }
}

/**
 * Function to convert String type and id to LogicNode
 */
fun getLogicNodeFromTypeAndId(type: String, id: String): LogicNode {
    return when {
        type == "action" && id == "teleport" -> LogicNode.Teleport()
        type == "event" && id == "visitor_interact" -> LogicNode.VisitorInteract()
        type == "event" && id == "player_interact" -> LogicNode.PlayerInteract()
        type == "dialogue" && id == "dialogue" || id == "present_text" -> LogicNode.Dialogue()
        type == "input" && id == "user_language" -> LogicNode.UserLanguage()
        // Default to Teleport if no match is found
        else -> LogicNode.Teleport()
    }
}
