package models

import kotlinx.serialization.Serializable

/**
 * Enum representing the category of a logic node
 */
enum class NodeCategory {
    Event,
    Action,
    Dialogue,
    Input,
    Quest
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
        override val description: String = "Generate text from system and user prompts"
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
     * PlayerFar node - triggers when the player moves away from the object
     */
    @Serializable
    data class PlayerFar(
        override val id: NodeId = NodeId.PlayerFar,
        override val label: String = "Player far",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when the player moves away from this object"
    ) : LogicNode

    /**
     * VisitorFar node - triggers when a visitor moves away from the object
     */
    @Serializable
    data class VisitorFar(
        override val id: NodeId = NodeId.VisitorFar,
        override val label: String = "Visitor far",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when a visitor moves away from this object"
    ) : LogicNode

    /**
     * PlayerLeave node - triggers when the player leaves the world
     */
    @Serializable
    data class PlayerLeave(
        override val id: NodeId = NodeId.PlayerLeave,
        override val label: String = "Player leave",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when the player leaves the world"
    ) : LogicNode

    /**
     * VisitorLeave node - triggers when a visitor leaves the world
     */
    @Serializable
    data class VisitorLeave(
        override val id: NodeId = NodeId.VisitorLeave,
        override val label: String = "Visitor leave",
        override val category: NodeCategory = NodeCategory.Event,
        override val description: String = "Triggers when a visitor leaves the world"
    ) : LogicNode

    /**
     * AiChat node - opens a new AI Chat session
     */
    @Serializable
    data class AiChat(
        override val id: NodeId = NodeId.AiChat,
        override val label: String = "AI chat",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Open a new AI chat session with custom system prompt, initial user message, and initial assistant message"
    ) : LogicNode

    /**
     * PlaySound node - plays a sound for everyone in the world
     */
    @Serializable
    data class PlaySound(
        override val id: NodeId = NodeId.PlaySound,
        override val label: String = "Play sound",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Plays a sound"
    ) : LogicNode

    /**
     * PlayerImageInput node - prompts the player for image uploads
     */
    @Serializable
    data class PlayerImageInput(
        override val id: NodeId = NodeId.PlayerImageInput,
        override val label: String = "Player image input",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Prompt player with a title, preview areas and confirm button"
    ) : LogicNode

    /**
     * PlayerAudioInput node - prompts the player for audio uploads
     */
    @Serializable
    data class PlayerAudioInput(
        override val id: NodeId = NodeId.PlayerAudioInput,
        override val label: String = "Player audio input",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Prompt player with a title, preview areas and confirm button"
    ) : LogicNode

    /**
     * JoinList node - formats and joins a comma-separated list into a single string
     */
    @Serializable
    data class JoinList(
        override val id: NodeId = NodeId.JoinList,
        override val label: String = "Join list",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Format and join a comma-separated list into a single string"
    ) : LogicNode

    /**
     * OpenDoc node - opens a WorldDocsDialog with a document pre-opened
     */
    @Serializable
    data class OpenDoc(
        override val id: NodeId = NodeId.OpenDoc,
        override val label: String = "Open doc",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Open a doc"
    ) : LogicNode

    /**
     * QuestState node - reads the global quest state value
     */
    @Serializable
    data class QuestState(
        override val id: NodeId = NodeId.QuestState,
        override val label: String = "Quest state",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Reads the global quest state value"
    ) : LogicNode

    /**
     * PlayerQuestState node - reads the player-local quest state value
     */
    @Serializable
    data class PlayerQuestState(
        override val id: NodeId = NodeId.PlayerQuestState,
        override val label: String = "Player quest state",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Reads the player-local quest state value"
    ) : LogicNode

    /**
     * UpdateQuestState node - updates the global quest state value
     */
    @Serializable
    data class UpdateQuestState(
        override val id: NodeId = NodeId.UpdateQuestState,
        override val label: String = "Update quest state",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Updates the global quest state value"
    ) : LogicNode

    /**
     * UpdatePlayerQuestState node - updates the player-local quest state value
     */
    @Serializable
    data class UpdatePlayerQuestState(
        override val id: NodeId = NodeId.UpdatePlayerQuestState,
        override val label: String = "Update player quest state",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Updates the player-local quest state value"
    ) : LogicNode

    /**
     * CompareValue node - compares two values
     */
    @Serializable
    data class CompareValue(
        override val id: NodeId = NodeId.CompareValue,
        override val label: String = "Compare value",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Compares two values (Equals, Greater than, Less than)"
    ) : LogicNode

    /**
     * Math node - performs arithmetic operations
     */
    @Serializable
    data class Math(
        override val id: NodeId = NodeId.Math,
        override val label: String = "Math",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Performs arithmetic operations (Add, Subtract, Multiply, Divide)"
    ) : LogicNode

    /**
     * AiImage node - generates an image from a prompt
     */
    @Serializable
    data class AiImage(
        override val id: NodeId = NodeId.AiImage,
        override val label: String = "AI Image",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Generate an image from a prompt"
    ) : LogicNode

    /**
     * AiTts node - generates audio from text
     */
    @Serializable
    data class AiTts(
        override val id: NodeId = NodeId.AiTts,
        override val label: String = "AI TTS",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Generate audio from text"
    ) : LogicNode

    /**
     * Minigame node - triggers a minigame on the client
     */
    @Serializable
    data class Minigame(
        override val id: NodeId = NodeId.Minigame,
        override val label: String = "Minigame",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Start a minigame (Flashcards, Matching, etc.)"
    ) : LogicNode

    /**
     * JsonExtract node - extracts fields from a JSON string
     */
    @Serializable
    data class JsonExtract(
        override val id: NodeId = NodeId.JsonExtract,
        override val label: String = "JSON extract",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Extract a value from a JSON string using a key"
    ) : LogicNode

    /**
     * UpdatePlayerQuestStep node - updates the player-local quest progress to a specific step
     */
    @Serializable
    data class UpdatePlayerQuestStep(
        override val id: NodeId = NodeId.UpdatePlayerQuestStep,
        override val label: String = "Update player quest step",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Updates the player's current quest step"
    ) : LogicNode

    /**
     * LocateObject node - moves the player to an object's location with a random offset
     */
    @Serializable
    data class LocateObject(
        override val id: NodeId = NodeId.LocateObject,
        override val label: String = "Locate object",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Moves the player to an object's location"
    ) : LogicNode

    /**
     * StartQuest node - starts a quest for the current player
     */
    @Serializable
    data class StartQuest(
        override val id: NodeId = NodeId.StartQuest,
        override val label: String = "Start quest",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Starts a quest for the current player"
    ) : LogicNode

    /**
     * QuestPlayerStatus node - checks the player's status for a specific quest
     */
    @Serializable
    data class QuestPlayerStatus(
        override val id: NodeId = NodeId.QuestPlayerStatus,
        override val label: String = "Quest player status",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Checks if the player's status for a specific quest (Not started, In progress, Complete)"
    ) : LogicNode

    /**
     * QuestStep node - checks if the player's status for a specific quest step
     */
    @Serializable
    data class QuestStep(
        override val id: NodeId = NodeId.QuestStep,
        override val label: String = "Quest step",
        override val category: NodeCategory = NodeCategory.Quest,
        override val description: String = "Checks if a specific quest step is completed"
    ) : LogicNode

    /**
     * SetAsset node - changes the asset of an object
     */
    @Serializable
    data class SetAsset(
        override val id: NodeId = NodeId.SetAsset,
        override val label: String = "Set asset",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Changes the asset (file and collection) of this object"
    ) : LogicNode

    /**
     * IncreasePlayerSkill node - increases a player's skill level
     */
    @Serializable
    data class IncreasePlayerSkill(
        override val id: NodeId = NodeId.IncreasePlayerSkill,
        override val label: String = "Increase player skill",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Increases a specific skill level for the player"
    ) : LogicNode

    /**
     * CheckPlayerSkill node - checks if a player's skill level meets a condition
     */
    @Serializable
    data class CheckPlayerSkill(
        override val id: NodeId = NodeId.CheckPlayerSkill,
        override val label: String = "Check player skill",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Checks if a player's skill level meets a condition"
    ) : LogicNode

    /**
     * MoveObject node - moves an object to a different 3D position
     */
    @Serializable
    data class MoveObject(
        override val id: NodeId = NodeId.MoveObject,
        override val label: String = "Move object",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Moves this object to a different 3D position"
    ) : LogicNode

    /**
     * TranscribeAudio node - converts an audio file to text
     */
    @Serializable
    data class TranscribeAudio(
        override val id: NodeId = NodeId.TranscribeAudio,
        override val label: String = "Transcribe audio",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "Converts an audio file to text"
    ) : LogicNode

    /**
     * ChangeWorldMusic node - changes the current world's background music
     */
    @Serializable
    data class ChangeWorldMusic(
        override val id: NodeId = NodeId.ChangeWorldMusic,
        override val label: String = "Change world music",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Changes the world background music"
    ) : LogicNode

    /**
     * Plain text node - simple text container
     */
    @Serializable
    data class PlainText(
        override val id: NodeId = NodeId.PlainText,
        override val label: String = "Plain text",
        override val category: NodeCategory = NodeCategory.Input,
        override val description: String = "A simple text node that provides its content as an output"
    ) : LogicNode

    /**
     * DropItem node - takes an item from the world owner's inventory and drops it in the world
     */
    @Serializable
    data class DropItem(
        override val id: NodeId = NodeId.DropItem,
        override val label: String = "Drop item",
        override val category: NodeCategory = NodeCategory.Action,
        override val description: String = "Takes an item from the world owner's inventory and drops it in the world at a random position within a given radius"
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
        LogicNode.PlayerFar(),
        LogicNode.VisitorFar(),
        LogicNode.PlayerLeave(),
        LogicNode.VisitorLeave(),
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
        LogicNode.PlaySound(),
        LogicNode.PlayerImageInput(),
        LogicNode.PlayerAudioInput(),
        LogicNode.JoinList(),
        LogicNode.OpenDoc(),
        LogicNode.QuestState(),
        LogicNode.PlayerQuestState(),
        LogicNode.UpdateQuestState(),
        LogicNode.UpdatePlayerQuestState(),
        LogicNode.CompareValue(),
        LogicNode.Math(),
        LogicNode.AiImage(),
        LogicNode.AiTts(),
        LogicNode.Minigame(),
        LogicNode.JsonExtract(),
        LogicNode.UpdatePlayerQuestStep(),
        LogicNode.LocateObject(),
        LogicNode.StartQuest(),
        LogicNode.QuestPlayerStatus(),
        LogicNode.QuestStep(),
        LogicNode.SetAsset(),
        LogicNode.IncreasePlayerSkill(),
        LogicNode.CheckPlayerSkill(),
        LogicNode.MoveObject(),
        LogicNode.TranscribeAudio(),
        LogicNode.ChangeWorldMusic(),
        LogicNode.PlainText(),
        LogicNode.DropItem(),
    )
}

/**
 * Extension function to get node types by category
 */
fun getNodeTypesByCategory(): Map<NodeCategory, List<LogicNode>> {
    return getAllNodeTypes().groupBy { it.category }
}
