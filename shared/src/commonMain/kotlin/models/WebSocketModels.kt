

package models

import kotlinx.serialization.Serializable
import main.components.world.PlayerActivity

import kotlin.time.Instant

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
    val buttons: List<String> = listOf("OK"), // Default to a single "OK" button
    val imageUrl: String? = null, // Optional image URL to display above text
    val objectTitle: String? = null, // Optional title of the object that initiated the dialogue
    val title: String? = null, // Optional custom title for the dialog
    val requiresInput: Boolean = false, // If true, show a text input and return text on confirm
    val placeholder: String? = null, // Optional placeholder for the input
    val confirmLabel: String? = null, // Optional confirm button label override
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
    val buttonIndex: Int = 0, // Index of the button that was clicked (0 is the default/first button)
    val inputText: String? = null // Optional text provided by the user for input dialogues
)

/**
 * Take-items prompt sent to a user.
 */
@Serializable
data class TakeItemsPrompt(
    val requestId: String,
    val userId: String,
    val nodeId: String,
    val items: List<TakeItemEntry>,
    val isReceiving: Boolean = false,
)

@Serializable
data class TakeItemEntry(
    val itemId: String,
    val quantity: Int,
    val title: String,
    val file: String,
    val assetIndex: Int
)

/**
 * Client confirmation for a take-items prompt.
 */
@Serializable
data class TakeItemsConfirmation(
    val requestId: String,
    val userId: String,
    val accepted: Boolean
)

/**
 * Inventory change notification.
 */
@Serializable
data class InventoryChanged(
    val userId: String
)

/**
 * Friend status change notification.
 * Sent over the global WebSocket to a user's friends.
 */
@Serializable
data class FriendStatusChanged(
    val userId: String,
    val isOnline: Boolean,
    val worldId: String? = null,
    val worldName: String? = null,
    val position: Position? = null,
)

/**
 * Open URL data model
 * 
 * This model is sent when an OpenUrl node is executed to prompt the user to open a URL.
 */
@Serializable
data class OpenUrlData(
    val userId: String,
    val url: String
)

/**
 * Notification data model
 * 
 * This model is sent when a notification is created to inform the user.
 */
@Serializable
data class NotificationData(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val isRead: Boolean,
    val type: NotificationType,
    val worldId: String,
    val objectId: String,
    val otherUserId: String,
    val timestamp: Instant
)

/**
 * Pause event data model
 *
 * Sent to all other active sessions for the same user when a new
 * session is established. The client should navigate to "/paused"
 * and allow resuming back to the provided world.
 */
@Serializable
data class PauseData(
    val userId: String,
    val worldUrl: String,
    val worldName: String
)

// Friend request lifecycle payload for WebSocket events
@Serializable
data class FriendRequestData(
    val id: String,
    val requesterId: String,
    val requesterUsername: String,
    val addresseeId: String,
    val status: FriendRequestStatus,
    val createdAt: Instant,
    val decidedAt: Instant? = null,
)
