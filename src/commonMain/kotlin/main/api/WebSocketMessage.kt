package main.api

import kotlinx.serialization.Serializable
import models.WebSocketAction

/**
 * WebSocket message model
 *
 * This class defines the expected format for all WebSocket messages.
 * - action: The type of action being performed (must be one of the predefined WebSocketAction enum values)
 * - data: A JSON string containing the serialized data for the action
 *
 * Valid actions are defined in the WebSocketAction enum:
 * - ADD_OBJECT: Adds an object to the world
 * - REMOVE_OBJECT: Removes an object from the world
 * - UPDATE_PLAYER_POSITION: Updates a player's position
 * - PLAYER_INTERACT: Sends a player interaction with an object
 * - TRIGGER_OBJECT_EVENT: Triggers an event on an object
 * - SEND_CHAT_MESSAGE: Sends a chat message to all users in the same world
 * - RECEIVE_CHAT_MESSAGE: Receives a chat message from another user
 */
@Serializable
data class WebSocketMessage(
    val action: WebSocketAction? = null,
    val data: String = ""
) {
    /**
     * Check if this message is valid (has required fields)
     */
    fun isValid(): Boolean {
        return action != null && data.isNotEmpty()
    }

    companion object {
        /**
         * Create a WebSocketMessage from a WebSocketAction enum
         */
        fun fromEnum(action: WebSocketAction, data: String): WebSocketMessage {
            console.log("WebSocketMessage.fromEnum: Creating message with action=$action")
            if (data.isEmpty()) {
                console.warn("WebSocketMessage.fromEnum: Warning - data is empty for action=$action")
            }

            // Explicitly create a new instance with non-null action
            val message = WebSocketMessage(action = action, data = data)
            console.log("WebSocketMessage.fromEnum: Created message with action=${message.action}, data length=${message.data.length}")

            // Verify the message is valid
            if (!message.isValid()) {
                console.error("WebSocketMessage.fromEnum: Created an invalid message! action=${message.action}, data length=${message.data.length}")
            }

            return message
        }
    }
}
