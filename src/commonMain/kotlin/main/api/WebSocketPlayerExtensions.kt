package main.api

import kotlinx.coroutines.launch
import main.components.world.PlayerActivity
import models.PlayerInteractData
import models.PlayerPositionData
import models.PlayerTypingData
import models.Position
import models.UpdatePlayerData
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import utils.json

/**
 * Extension functions for WebSocketApi related to player operations
 */

/**
 * Update player position
 * 
 * This sends the player's current position to all other clients in the world.
 * The server will broadcast this to all other clients except the sender.
 * 
 * @param position The player's current position
 * @param isPlayer True if this is a player (authenticated user), false if it's a visitor
 * @return true if the position was updated successfully, false otherwise
 */
suspend fun WebSocketApi.updatePlayerPosition(position: Position, isPlayer: Boolean): Boolean {
    try {
        if (!isConnected()) {
            console.warn("[MOVEMENT] Cannot update player position: WebSocket not connected")
            return false
        }
        
        val user = UserApi.getCurrentUser()
        val positionData = PlayerPositionData(
            position = position,
            isPlayer = isPlayer,
            userId = user.key
        )
        
        val message = WebSocketMessage.fromEnum(WebSocketAction.UPDATE_PLAYER_POSITION, ApiClient.json.encodeToString(positionData))
        return sendMessage(message)
    } catch (e: Throwable) {
        console.error("[MOVEMENT] Error updating player position", e)
        return false
    }
}

/**
 * Send a player interaction with an object
 * 
 * This is called when a player clicks or taps on an object.
 * The server will trigger the "Player interact" or "Visitor interact" event on the object.
 * 
 * @param objectId The ID of the object being interacted with
 * @param isPlayer True if this is a player (authenticated user), false if it's a visitor
 * @return true if the interaction was sent successfully, false otherwise
 */
fun WebSocketApi.playerInteract(objectId: String, isPlayer: Boolean): Boolean {
    var success = false

    apiScope.launch {
        try {
            val user = UserApi.getCurrentUser()
            val interactData = PlayerInteractData(
                objectId = objectId,
                isPlayer = isPlayer,
                userId = user.key
            )
            
            val message = WebSocketMessage.fromEnum(WebSocketAction.USER_INTERACT, json.encodeToString(interactData))
            success = sendMessage(message)
        } catch (e: Throwable) {
            console.error("Error sending player interaction", e)
        }
    }
    
    return success
}

/**
 * Update player details
 * 
 * This sends the player's updated details to all other clients in the world.
 * Used when a visitor becomes a player or when player details are updated.
 * 
 * @param name The player's name
 * @param isPlayer True if this is a player (authenticated user), false if it's a visitor
 * @param activity The player's current activity (null means standing)
 * @param status The player's status message (null means no status)
 * @return true if the update was sent successfully, false otherwise
 */
suspend fun WebSocketApi.updatePlayer(
    name: String,
    isPlayer: Boolean,
    activity: PlayerActivity? = null,
    status: String? = null
): Boolean {
    try {
        if (!isConnected()) {
            console.warn("Cannot update player details: WebSocket not connected")
            return false
        }

        val user = UserApi.getCurrentUser()
        val updateData = UpdatePlayerData(
            userId = user.key,
            name = name,
            isPlayer = isPlayer,
            activity = activity,
            status = status
        )

        val message = WebSocketMessage.fromEnum(WebSocketAction.UPDATE_PLAYER, ApiClient.json.encodeToString(updateData))
        val success = sendMessage(message)

        if (success) {
            console.log("Player update sent successfully: name=$name, isPlayer=$isPlayer, activity=${activity?.name ?: "Standing"}, status=${status ?: "None"}")
        } else {
            console.error("Failed to send player update")
        }

        return success
    } catch (e: Throwable) {
        console.error("Error updating player details", e)
        return false
    }
}

/**
 * Update player status
 * 
 * This sends the player's updated status to all other clients in the world.
 * 
 * @param status The player's status message
 * @param isPlayer Whether the user is a player (true) or visitor (false). Defaults to true.
 * @return true if the update was sent successfully, false otherwise
 */
suspend fun WebSocketApi.updatePlayerStatus(status: String, isPlayer: Boolean = true): Boolean {
    try {
        if (!isConnected()) {
            console.warn("Cannot update player status: WebSocket not connected")
            return false
        }

        val user = UserApi.getCurrentUser()
        val updateData = UpdatePlayerData(
            userId = user.key,
            name = user.username,
            isPlayer = isPlayer,
            status = status
        )

        val message = WebSocketMessage.fromEnum(WebSocketAction.UPDATE_PLAYER, ApiClient.json.encodeToString(updateData))
        val success = sendMessage(message)

        if (success) {
            console.log("Player status update sent successfully: status=$status, isPlayer=$isPlayer")
        } else {
            console.error("Failed to send player status update")
        }

        return success
    } catch (e: Throwable) {
        console.error("Error updating player status", e)
        return false
    }
}

/**
 * Sends a typing status update
 *
 * This sends a typing status update to all users in the same world.
 * The server will broadcast this to all clients including the sender.
 *
 * @param worldId The ID of the world
 * @param isTyping Whether the player is typing or not
 * @return True if the message was sent successfully, false otherwise
 */
suspend fun WebSocketApi.sendTypingStatus(worldId: String, isTyping: Boolean): Boolean {
    console.log("[TYPING] sendTypingStatus called with worldId=$worldId, isTyping=$isTyping")
    
    // Validate input parameters
    if (worldId.isEmpty()) {
        console.error("[TYPING] Cannot send typing status: worldId is empty")
        return false
    }
    
    // Check connection status
    if (!isConnected()) {
        console.warn("[TYPING] Cannot send typing status: WebSocket not connected")
        return false
    }
    
    try {
        // Get current user
        val user = UserApi.getCurrentUser()
        console.log("[TYPING] Current user: userId=${user.key}, username=${user.username}")
        
        // Create typing status data
        val typingData = PlayerTypingData(
            userId = user.key,
            isTyping = isTyping
        )
        console.log("[TYPING] Created typing status data: userId=${typingData.userId}, isTyping=${typingData.isTyping}")
        
        // Create and send the message
        val message = WebSocketMessage.fromEnum(WebSocketAction.PLAYER_TYPING, ApiClient.json.encodeToString(typingData))
        val success = sendMessage(message)
        
        if (success) {
            console.log("[TYPING] Typing status sent successfully: userId=${user.key}, isTyping=$isTyping")
        } else {
            console.error("[TYPING] Failed to send typing status")
        }
        
        return success
    } catch (e: Throwable) {
        console.error("[TYPING] Error sending typing status", e)
        console.error("[TYPING] Exception details: ${e.message}")
        return false
    }
}

/**
 * Send a user join notification
 * 
 * This is called when the current user joins a world.
 * The server will broadcast this to all clients in the world.
 * 
 * @param worldId The ID of the world
 * @param position The initial position of the user
 * @param isPlayer True if this is a player (authenticated user), false if it's a visitor
 * @return true if the notification was sent successfully, false otherwise
 */
fun WebSocketApi.sendUserJoin(worldId: String, position: Position, isPlayer: Boolean) {
    console.log("WebSocketApi: Attempting to send USER_JOIN notification for world: $worldId")
    
    apiScope.launch {
        try {
            if (!isConnected()) {
                console.warn("Cannot send user join notification: WebSocket not connected")
                return@launch
            }
            
            val user = UserApi.getCurrentUser()
            console.log("WebSocketApi: Current user for join notification: ${user.key}, ${user.username}")
            
            val joinData = UserJoinData(
                userId = user.key,
                username = user.username,
                isPlayer = isPlayer,
                position = position,
                worldId = worldId
            )
            
            console.log("WebSocketApi: Created UserJoinData: $joinData")
            val message = WebSocketMessage.fromEnum(WebSocketAction.USER_JOIN, ApiClient.json.encodeToString(joinData))
            val success = sendMessage(message)
            
            if (success) {
                console.log("WebSocketApi: User join notification sent successfully")
            } else {
                console.error("WebSocketApi: Failed to send user join notification")
            }
        } catch (e: Throwable) {
            console.error("WebSocketApi: Error sending user join notification", e)
        }
    }
}

/**
 * Send a user leave notification
 * 
 * This is called when the current user leaves a world.
 * The server will broadcast this to all clients in the world.
 * 
 * @param worldId The ID of the world
 * @return true if the notification was sent successfully, false otherwise
 */
fun WebSocketApi.sendUserLeave(worldId: String) {
    apiScope.launch {
        try {
            if (!isConnected()) {
                console.warn("Cannot send user leave notification: WebSocket not connected")
                return@launch
            }
            
            val user = UserApi.getCurrentUser()
            val leaveData = UserLeaveData(
                userId = user.key,
                worldId = worldId
            )
            
            val message = WebSocketMessage.fromEnum(WebSocketAction.USER_LEAVE, ApiClient.json.encodeToString(leaveData))
            val success = sendMessage(message)
            
            if (success) {
                console.log("User leave notification sent successfully")
            } else {
                console.error("Failed to send user leave notification")
            }
        } catch (e: Throwable) {
            console.error("Error sending user leave notification", e)
        }
    }
}
