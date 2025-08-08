package models

import kotlinx.serialization.Serializable

/**
 * Enum representing the category of a logic node
 */
enum class NodeCategory {
    Event,
    Action,
    Dialogue,
    Input
}

/**
 * Sealed interface representing a logic node type
 */
@Serializable
sealed interface LogicNode {
    /**
     * The unique identifier for this node type
     */
    val id: NodeId
    
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
        override val id: NodeId = NodeId.Teleport,
        override val label: String = "Teleport player",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Move player to a different location"
    ) : LogicNode
    
    /**
     * VisitorInteract node - triggers when a visitor interacts with this object
     */
    @Serializable
    data class VisitorInteract(
        override val id: NodeId = NodeId.VisitorInteract,
        override val label: String = "Visitor interact",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when a visitor interacts with this object"
    ) : LogicNode
    
    /**
     * PlayerInteract node - triggers when the player interacts with this object
     */
    @Serializable
    data class PlayerInteract(
        override val id: NodeId = NodeId.PlayerInteract,
        override val label: String = "Player interact",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when the player interacts with this object"
    ) : LogicNode
    
    /**
     * Dialogue node - displays text message to the player
     */
    @Serializable
    data class Dialogue(
        override val id: NodeId = NodeId.Dialogue,
        override val label: String = "Present text",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Display text message to the player"
    ) : LogicNode
    
    /**
     * UserLanguage node - directs logic based on the user's language
     */
    @Serializable
    data class UserLanguage(
        override val id: NodeId = NodeId.UserLanguage,
        override val label: String = "User language",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Directs logic based on the user's language"
    ) : LogicNode
    
    /**
     * HasItem node - checks if player has a specific item
     */
    @Serializable
    data class HasItem(
        override val id: NodeId = NodeId.HasItem,
        override val label: String = "Has item",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Checks if player has a specific item"
    ) : LogicNode
    
    /**
     * OpenUrl node - opens a URL in a new browser window
     */
    @Serializable
    data class OpenUrl(
        override val id: NodeId = NodeId.OpenUrl,
        override val label: String = "Open URL",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Opens a URL in a new browser window"
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
        LogicNode.UserLanguage(),
        LogicNode.HasItem(),
        LogicNode.OpenUrl()
    )
}

/**
 * Extension function to get node types by category
 */
fun getNodeTypesByCategory(): Map<NodeCategory, List<LogicNode>> {
    return getAllNodeTypes().groupBy { it.category }
}
