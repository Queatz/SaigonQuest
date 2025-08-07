package models

import kotlinx.serialization.Serializable
import main.components.world.PlayerActivity

/**
 * Player typing data model
 * 
 * This model is sent when a player starts or stops typing.
 */
@Serializable
data class PlayerTypingData(
    val userId: String,
    val isTyping: Boolean
)

/**
 * Player position data model
 */
@Serializable
data class PlayerPositionData(
    val position: Position,
    val isPlayer: Boolean,
    val userId: String
)

/**
 * Player interaction data model
 */
@Serializable
data class PlayerInteractData(
    val objectId: String,
    val isPlayer: Boolean,
    val userId: String
)

/**
 * Trigger event data model
 */
@Serializable
data class TriggerEventData(
    val objectId: String,
    val eventType: String,
    val userId: String
)

/**
 * User join data model
 * 
 * This model is sent when a user joins a world.
 */
@Serializable
data class UserJoinData(
    val userId: String,
    val username: String,
    val isPlayer: Boolean,
    val position: Position,
    val worldId: String
)

/**
 * User leave data model
 * 
 * This model is sent when a user leaves a world.
 */
@Serializable
data class UserLeaveData(
    val userId: String,
    val worldId: String
)

/**
 * Update player data model
 * 
 * This model is sent when a visitor becomes a player or player details are updated.
 */
@Serializable
data class UpdatePlayerData(
    val userId: String,
    val name: String,
    val isPlayer: Boolean,
    val activity: PlayerActivity? = null,
    val status: String? = null
)

/**
 * Dialogue data model
 * 
 * This model is sent when a dialogue node is executed to show a dialogue to the player.
 */
@Serializable
data class DialogueData(
    val dialogueId: String,
    val userId: String,
    val text: String,
    val nodeId: String,
    val buttons: List<String> = listOf("OK") // Default to a single "OK" button
)

/**
 * Dialogue confirmation model
 * 
 * This model is sent when a player clicks a button on a dialogue.
 */
@Serializable
data class DialogueConfirmation(
    val dialogueId: String,
    val userId: String,
    val buttonIndex: Int = 0 // Index of the button that was clicked (0 is the default/first button)
)
