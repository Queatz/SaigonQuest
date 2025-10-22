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
     * PlayerJoined node - triggers when a player joins the world
     */
    @Serializable
    data class PlayerJoined(
        override val id: NodeId = NodeId.PlayerJoined,
        override val label: String = "Player joined",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when a player joins the world"
    ) : LogicNode

    /**
     * VisitorJoined node - triggers when a visitor joins the world
     */
    @Serializable
    data class VisitorJoined(
        override val id: NodeId = NodeId.VisitorJoined,
        override val label: String = "Visitor joined",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when a visitor joins the world"
    ) : LogicNode
    
    /**
     * Dialogue node - displays text message to the player
     */
    @Serializable
    data class Dialogue(
        override val id: NodeId = NodeId.Dialogue,
        override val label: String = "Dialogue",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Display text and buttons to the player"
    ) : LogicNode
    
    /**
     * UserLanguage node - directs logic based on the user's language
     */
    @Serializable
    data class UserLanguage(
        override val id: NodeId = NodeId.UserLanguage,
        override val label: String = "User language",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Directs logic based on the user's preferred language"
    ) : LogicNode
    
    /**
     * UserSex node - directs logic based on the user's sex
     */
    @Serializable
    data class UserSex(
        override val id: NodeId = NodeId.UserSex,
        override val label: String = "User sex",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Directs logic based on the user's sex"
    ) : LogicNode
    
    /**
     * UserAge node - directs logic based on the user's age
     */
    @Serializable
    data class UserAge(
        override val id: NodeId = NodeId.UserAge,
        override val label: String = "User age",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Directs logic based on min/max age"
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
    
    /**
     * ObjectNotification node - sends a notification to the current user
     */
    @Serializable
    data class ObjectNotification(
        override val id: NodeId = NodeId.ObjectNotification,
        override val label: String = "Send notification",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Sends a notification to the current user"
    ) : LogicNode

    /**
     * ObjectOwnerNotification node - sends a notification to the object's owner
     */
    @Serializable
    data class ObjectOwnerNotification(
        override val id: NodeId = NodeId.ObjectOwnerNotification,
        override val label: String = "Notify object owner",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Sends a notification to the object's owner"
    ) : LogicNode

    /**
     * Delay node - waits for a specified time before continuing
     */
    @Serializable
    data class Delay(
        override val id: NodeId = NodeId.Delay,
        override val label: String = "Delay",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Wait for N seconds, then continue"
    ) : LogicNode

    /**
     * TakeItems node - prompts the user to transfer items to the world owner
     */
    @Serializable
    data class TakeItems(
        override val id: NodeId = NodeId.TakeItems,
        override val label: String = "Take items",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Prompt user to transfer selected items to the world owner"
    ) : LogicNode

    /**
     * GiveItems node - prompts the user to receive items from the world owner
     */
    @Serializable
    data class GiveItems(
        override val id: NodeId = NodeId.GiveItems,
        override val label: String = "Give items",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Prompt user to receive selected items from the world owner"
    ) : LogicNode

    /**
     * SendChat node - sends a chat message to the world's chat as this object
     */
    @Serializable
    data class SendChat(
        override val id: NodeId = NodeId.SendChat,
        override val label: String = "Send chat",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Send a chat message to the world as this object"
    ) : LogicNode
}

/**
 * Extension function to get all available node types
 */
fun getAllNodeTypes(): List<LogicNode> {
    return listOf(
        LogicNode.PlayerInteract(),
        LogicNode.VisitorInteract(),
        LogicNode.PlayerJoined(),
        LogicNode.VisitorJoined(),
        LogicNode.Teleport(),
        LogicNode.Dialogue(),
        LogicNode.UserLanguage(),
        LogicNode.UserSex(),
        LogicNode.UserAge(),
        LogicNode.HasItem(),
        LogicNode.OpenUrl(),
        LogicNode.ObjectNotification(),
        LogicNode.ObjectOwnerNotification(),
        LogicNode.Delay(),
        LogicNode.TakeItems(),
        LogicNode.GiveItems(),
        LogicNode.SendChat(),
    )
}

/**
 * Extension function to get node types by category
 */
fun getNodeTypesByCategory(): Map<NodeCategory, List<LogicNode>> {
    return getAllNodeTypes().groupBy { it.category }
}
