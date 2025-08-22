package main.api

import kotlinx.coroutines.launch
import models.ChatMessage
import models.WebSocketAction
import kotlin.time.Clock


/**
 * Extension functions for WebSocketApi related to chat operations
 */

/**
 * Send a message to the WebSocket server with a ChatMessage
 * 
 * @return true if the message was sent successfully, false otherwise
 */
private fun WebSocketApi.sendChatMessageData(action: WebSocketAction, data: ChatMessage): Boolean {
    if (!isConnected()) return false
    
    try {
        console.log("Preparing to send chat message with action: $action")
        val serializedData = ApiClient.json.encodeToString(data)
        console.log("Serialized chat message data: $serializedData")
        
        val message = WebSocketMessage.fromEnum(action, serializedData)
        console.log("Created WebSocketMessage: action=${message.action}, data length=${message.data.length}")
        
        val success = sendMessage(message)
        
        if (success) {
            console.log("Chat message sent successfully: ${data.message}")
        } else {
            console.error("Failed to send chat message")
        }
        
        return success
    } catch (e: Throwable) {
        console.error("Error sending chat message data", e)
        return false
    }
}

/**
 * Send a chat message to all users in the same world
 * 
 * This sends a chat message to all users in the same world.
 * The server will broadcast this to all clients including the sender.
 * 
 * @param worldId The ID of the world where the message is being sent
 * @param messageText The content of the message
 * @param callback A callback function that will be called with the result (true if successful, false otherwise)
 */

fun WebSocketApi.sendChatMessage(worldId: String, messageText: String, callback: (Boolean) -> Unit) {
    // Validate input parameters
    if (worldId.isEmpty()) {
        console.error("Cannot send chat message: worldId is empty")
        callback(false)
        return
    }

    if (messageText.isEmpty()) {
        console.error("Cannot send chat message: message is empty")
        callback(false)
        return
    }

    // Check connection status
    if (!isConnected()) {
        console.warn("Cannot send chat message: WebSocket not connected")
        callback(false)
        return
    }

    this.apiScope.launch {
        try {
            // Get current user
            val user = try {
                UserApi.getCurrentUser()
            } catch (e: Throwable) {
                console.error("Error getting current user", e)
                callback(false)
                return@launch
            }

            // Validate user data
            if (user.key.isEmpty()) {
                console.error("Cannot send chat message: User not authenticated")
                callback(false)
                return@launch
            }

            // Create chat message
            val chatMessage = ChatMessage(
                worldId = worldId,
                userId = user.key,
                username = user.username,
                message = messageText,
                timestamp = Clock.System.now()
            )

            // Send message
            console.log("Sending chat message: $messageText")
            val success = sendChatMessageData(WebSocketAction.
            SEND_CHAT_MESSAGE, chatMessage)

            if (!success) {
                console.error("Failed to send chat message")
            }

            // Call the callback with the result
            callback(success)
        } catch (e: Throwable) {
            console.error("Error creating chat message", e)
            callback(false)
        }
    }
}

/**
 * Update a chat message
 * 
 * This updates a chat message and broadcasts the update to all users in the same world.
 * 
 * @param message The chat message to update
 * @param newText The new text for the message
 * @param callback A callback function that will be called with the result (true if successful, false otherwise)
 */
fun WebSocketApi.updateChatMessage(message: ChatMessage, newText: String, callback: (Boolean) -> Unit) {
    // Validate input parameters
    if (message.key.isEmpty()) {
        console.error("Cannot update chat message: message key is empty")
        callback(false)
        return
    }

    if (newText.isEmpty()) {
        console.error("Cannot update chat message: new text is empty")
        callback(false)
        return
    }

    // Check connection status
    if (!isConnected()) {
        console.warn("Cannot update chat message: WebSocket not connected")
        callback(false)
        return
    }

    this.apiScope.launch {
        try {
            // Create updated message
            val updatedMessage = message.copy(
                message = newText,
                edited = true
            )

            // Send update
            console.log("Updating chat message: ${message.key} with text: $newText")
            val success = sendChatMessageData(WebSocketAction.UPDATE_CHAT_MESSAGE, updatedMessage)

            if (!success) {
                console.error("Failed to update chat message")
            }

            // Call the callback with the result
            callback(success)
        } catch (e: Throwable) {
            console.error("Error updating chat message", e)
            callback(false)
        }
    }
}

/**
 * Delete a chat message
 * 
 * This marks a chat message as deleted and broadcasts the update to all users in the same world.
 * 
 * @param message The chat message to delete
 * @param callback A callback function that will be called with the result (true if successful, false otherwise)
 */
fun WebSocketApi.deleteChatMessage(message: ChatMessage, callback: (Boolean) -> Unit) {
    // Validate input parameters
    if (message.key.isEmpty()) {
        console.error("Cannot delete chat message: message key is empty")
        callback(false)
        return
    }

    // Check connection status
    if (!isConnected()) {
        console.warn("Cannot delete chat message: WebSocket not connected")
        callback(false)
        return
    }

    this.apiScope.launch {
        try {
            // Create deleted message
            val deletedMessage = message.copy(
                message = "",
                deleted = true,
                edited = false
            )

            // Send delete
            console.log("Deleting chat message: ${message.key}")
            val success = sendChatMessageData(WebSocketAction.DELETE_CHAT_MESSAGE, deletedMessage)

            if (!success) {
                console.error("Failed to delete chat message")
            }

            // Call the callback with the result
            callback(success)
        } catch (e: Throwable) {
            console.error("Error deleting chat message", e)
            callback(false)
        }
    }
}
