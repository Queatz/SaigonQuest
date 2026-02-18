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
     * Teleport node - moves the player or visitor to a different world
     */
    @Serializable
    data class Teleport(
        override val id: NodeId = NodeId.Teleport,
        override val label: String = "Teleport",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Move player or visitor to a different world"
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

    /**
     * SetStatus node - sets the object's status text
     */
    @Serializable
    data class SetStatus(
        override val id: NodeId = NodeId.SetStatus,
        override val label: String = "Set status",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Set this object's status text"
    ) : LogicNode

    /**
     * PlayerInput node - prompts the player for a text input
     */
    @Serializable
    data class PlayerInput(
        override val id: NodeId = NodeId.PlayerInput,
        override val label: String = "Player input",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Prompt player with a title, text field and confirm button"
    ) : LogicNode
    
    /**
     * StringTemplate node - formats a template string with inputs
     */
    @Serializable
    data class StringTemplate(
        override val id: NodeId = NodeId.StringTemplate,
        override val label: String = "String template",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Format a template string like 'Hi, {name}!' using inputs"
    ) : LogicNode

    /**
     * PlayerName node - outputs the current player name
     */
    @Serializable
    data class PlayerName(
        override val id: NodeId = NodeId.PlayerName,
        override val label: String = "Player name",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Outputs the current player name"
    ) : LogicNode

    /**
     * Ai node - calls DeepSeek to generate text from prompts
     */
    @Serializable
    data class Ai(
        override val id: NodeId = NodeId.Ai,
        override val label: String = "AI",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Generate text using DeepSeek from system and user prompts"
    ) : LogicNode

    /**
     * DocContent node - outputs the content of a doc if accessible
     */
    @Serializable
    data class DocContent(
        override val id: NodeId = NodeId.DocContent,
        override val label: String = "Doc content",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Outputs the document content"
    ) : LogicNode

    /**
     * CreateDoc node - creates a new document
     */
    @Serializable
    data class CreateDoc(
        override val id: NodeId = NodeId.CreateDoc,
        override val label: String = "Create doc",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Create a new doc"
    ) : LogicNode

    /**
     * IsObjectOwner node - checks if the current player is an owner or co-owner of the world
     */
    @Serializable
    data class IsObjectOwner(
        override val id: NodeId = NodeId.IsObjectOwner,
        override val label: String = "Is object owner",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Checks if the current player is an owner or co-owner of the world"
    ) : LogicNode

    /**
     * VisitorNearby node - triggers when a visitor comes within range
     */
    @Serializable
    data class VisitorNearby(
        override val id: NodeId = NodeId.VisitorNearby,
        override val label: String = "Visitor nearby",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when a visitor comes near this object"
    ) : LogicNode

    /**
     * PlayerNearby node - triggers when a player comes within range
     */
    @Serializable
    data class PlayerNearby(
        override val id: NodeId = NodeId.PlayerNearby,
        override val label: String = "Player nearby",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when the player comes near this object"
    ) : LogicNode

    /**
     * AiChat node - opens a new AI Chat session
     */
    @Serializable
    data class AiChat(
        override val id: NodeId = NodeId.AiChat,
        override val label: String = "AI chat",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Open a new AI chat session with custom system prompt and initial message"
    ) : LogicNode
}

/**
 * Map of node IDs to their logic node instances
 */
val logicNodeById: Map<NodeId, LogicNode> by lazy {
    getAllNodeTypes().associateBy { it.id }
}

/**
 * Extension function to get all available node types
 */
fun getAllNodeTypes(): List<LogicNode> {
    return listOf(
        LogicNode.PlayerInteract(),
        LogicNode.VisitorInteract(),
        LogicNode.PlayerNearby(),
        LogicNode.VisitorNearby(),
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
        LogicNode.SetStatus(),
        LogicNode.PlayerInput(),
        LogicNode.StringTemplate(),
        LogicNode.PlayerName(),
        LogicNode.CreateDoc(),
        LogicNode.DocContent(),
        LogicNode.Ai(),
        LogicNode.IsObjectOwner(),
        LogicNode.AiChat(),
    )
}

/**
 * Extension function to get node types by category
 */
fun getNodeTypesByCategory(): Map<NodeCategory, List<LogicNode>> {
    return getAllNodeTypes().groupBy { it.category }
}
