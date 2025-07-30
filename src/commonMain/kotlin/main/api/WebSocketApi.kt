package main.api

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import models.ChatMessage
import models.Object
import models.Position
import models.User
import org.w3c.dom.WebSocket
import kotlin.time.Clock

/**
 * API client for WebSocket operations
 * 
 * This API provides real-time communication with the server using WebSockets.
 * It handles connection management, message sending/receiving, and event listeners.
 * 
 * The WebSocket route (/ws) handles the following actions:
 * - addObject: Adds an object to the world
 * - removeObject: Removes an object from the world
 * - updatePlayerPosition: Updates a player's position
 * - playerInteract: Sends a player interaction with an object
 * - sendChatMessage: Sends a chat message to all users in the same world
 * - receiveChatMessage: Receives a chat message from another user
 * 
 * Usage:
 * 1. Connect to the WebSocket server:
 *    WebSocketApi.connect(worldId)
 * 
 * 2. Add listeners for events:
 *    WebSocketApi.addListener("addObject") { message ->
 *        // Handle addObject event
 *    }
 *    WebSocketApi.addListener("receiveChatMessage") { message ->
 *        // Handle receiveChatMessage event
 *    }
 * 
 * 3. Send messages:
 *    WebSocketApi.addObject(objectModel)
 *    WebSocketApi.removeObject(objectId)
 *    WebSocketApi.updatePlayerPosition(position)
 *    WebSocketApi.playerInteract(objectId)
 *    WebSocketApi.sendChatMessage(worldId, messageText)
 * 
 * 4. Disconnect when done:
 *    WebSocketApi.disconnect()
 */
@OptIn(kotlin.time.ExperimentalTime::class)
object WebSocketApi {
    private val scope = MainScope()
    private var socket: WebSocket? = null
    private val listeners = mutableMapOf<String, MutableList<(WebSocketMessage) -> Unit>>()
    
    /**
     * Connect to the WebSocket server
     */
    fun connect(worldId: String) {
        // Close existing connection if any
        disconnect()

        // Get the current user token
        val token = UserApi.getToken() ?: ""

        // Create a new WebSocket connection
        val protocol = if (window.location.protocol == "https:") "wss:" else "ws:"
        val host = window.location.host
        socket = WebSocket("$protocol//$host/ws?worldId=$worldId&token=$token")

        // Set up event handlers
        socket?.onopen = { event ->
            console.log("WebSocket connection opened")
        }

        socket?.onmessage = { event ->
            val messageData = event.data.toString()
            try {
                val message = ApiClient.json.decodeFromString<WebSocketMessage>(messageData)

                // Validate that the action is one we expect
                if (WebSocketMessage.VALID_ACTIONS.contains(message.action)) {
                    // Notify listeners for this action
                    listeners[message.action]?.forEach { listener ->
                        scope.launch {
                            listener(message)
                        }
                    }
                    // Notify listeners for all actions
                    listeners["*"]?.forEach { listener ->
                        scope.launch {
                            listener(message)
                        }
                    }
                } else {
                    console.warn("Received WebSocket message with unexpected action: ${message.action}, ignoring")
                }
            } catch (e: Exception) {
                console.error("Error parsing WebSocket message. Raw message: $messageData", e)
            }
        }

        socket?.onclose = { event ->
            console.log("WebSocket connection closed")
        }

        socket?.onerror = { event ->
            console.error("WebSocket error", event)
        }
    }

    /**
     * Disconnect from the WebSocket server
     */
    fun disconnect() {
        socket?.close()
        socket = null
    }

    /**
     * Send a message to the WebSocket server with an Object
     */
    private fun sendObjectMessage(action: String, data: Object) {
        val message = WebSocketMessage(action, ApiClient.json.encodeToString(data))
        val messageString = ApiClient.json.encodeToString(message)
        socket?.send(messageString)
    }

    /**
     * Send a message to the WebSocket server with a String
     */
    private fun sendStringMessage(action: String, data: String) {
        val message = WebSocketMessage(action, ApiClient.json.encodeToString(data))
        val messageString = ApiClient.json.encodeToString(message)
        socket?.send(messageString)
    }

    /**
     * Send a message to the WebSocket server with a PlayerPositionData
     */
    private fun sendPlayerPositionMessage(action: String, data: PlayerPositionData) {
        val message = WebSocketMessage(action, ApiClient.json.encodeToString(data))
        val messageString = ApiClient.json.encodeToString(message)
        socket?.send(messageString)
    }

    /**
     * Send a message to the WebSocket server with a PlayerInteractData
     */
    private fun sendPlayerInteractMessage(action: String, data: PlayerInteractData) {
        val message = WebSocketMessage(action, ApiClient.json.encodeToString(data))
        val messageString = ApiClient.json.encodeToString(message)
        socket?.send(messageString)
    }

    /**
     * Send a message to the WebSocket server with a TriggerEventData
     */
    private fun sendTriggerEventMessage(action: String, data: TriggerEventData) {
        val message = WebSocketMessage(action, ApiClient.json.encodeToString(data))
        val messageString = ApiClient.json.encodeToString(message)
        socket?.send(messageString)
    }

    /**
     * Add an object to the world
     */
    fun addObject(obj: Object) {
        sendObjectMessage("addObject", obj)
    }

    /**
     * Remove an object from the world
     */
    fun removeObject(objectId: String) {
        sendStringMessage("removeObject", objectId)
    }
    
    /**
     * Update player position
     * 
     * This sends the player's current position to all other clients in the world.
     * The server will broadcast this to all other clients except the sender.
     * 
     * @param position The player's current position
     * @param isPlayer True if this is a player (authenticated user), false if it's a visitor
     */
    fun updatePlayerPosition(position: Position, isPlayer: Boolean) {
        scope.launch {
            try {
                val user = UserApi.getCurrentUser()
                val positionData = PlayerPositionData(
                    position = position,
                    isPlayer = isPlayer,
                    userId = user.key
                )
                sendPlayerPositionMessage("updatePlayerPosition", positionData)
            } catch (e: Exception) {
                console.error("Error updating player position", e)
            }
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
     */
    fun playerInteract(objectId: String, isPlayer: Boolean) {
        scope.launch {
            try {
                val user = UserApi.getCurrentUser()
                val interactData = PlayerInteractData(
                    objectId = objectId,
                    isPlayer = isPlayer,
                    userId = user.key
                )
                sendPlayerInteractMessage("playerInteract", interactData)
            } catch (e: Exception) {
                console.error("Error sending player interaction", e)
            }
        }
    }
    
    /**
     * Trigger an event on an object
     * 
     * This is called when an event condition is met, such as a player being nearby.
     * The server will trigger the specified event on the object.
     * 
     * @param objectId The ID of the object
     * @param eventType The type of event to trigger (e.g., "player_nearby", "visitor_nearby")
     * @param userId The ID of the user who triggered the event
     */
    fun triggerObjectEvent(objectId: String, eventType: String, userId: String) {
        val eventData = TriggerEventData(
            objectId = objectId,
            eventType = eventType,
            userId = userId
        )
        sendTriggerEventMessage("triggerObjectEvent", eventData)
    }
    
    /**
     * Send a message to the WebSocket server with a ChatMessage
     */
    private fun sendChatMessageData(action: String, data: ChatMessage) {
        val message = WebSocketMessage(action, ApiClient.json.encodeToString(data))
        val messageString = ApiClient.json.encodeToString(message)
        socket?.send(messageString)
    }
    
    /**
     * Send a chat message to all users in the same world
     * 
     * This sends a chat message to all users in the same world.
     * The server will broadcast this to all clients including the sender.
     * 
     * @param worldId The ID of the world where the message is being sent
     * @param messageText The content of the message
     */
    fun sendChatMessage(worldId: String, messageText: String) {
        scope.launch {
            try {
                val user = UserApi.getCurrentUser()
                val chatMessage = ChatMessage(
                    worldId = worldId,
                    userId = user.key,
                    username = user.username,
                    message = messageText,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
                sendChatMessageData("sendChatMessage", chatMessage)
            } catch (e: Exception) {
                console.error("Error sending chat message", e)
            }
        }
    }

    /**
     * Add a listener for a specific action
     */
    fun addListener(action: String, listener: (WebSocketMessage) -> Unit) {
        if (!listeners.containsKey(action)) {
            listeners[action] = mutableListOf()
        }
        listeners[action]?.add(listener)
    }

    /**
     * Remove a listener for a specific action
     */
    fun removeListener(action: String, listener: (WebSocketMessage) -> Unit) {
        listeners[action]?.remove(listener)
    }

    /**
     * Remove all listeners for a specific action
     */
    fun removeAllListeners(action: String) {
        listeners.remove(action)
    }
}

/**
 * WebSocket message model
 * 
 * This class defines the expected format for all WebSocket messages.
 * - action: The type of action being performed (must be one of the predefined actions)
 * - data: A JSON string containing the serialized data for the action
 * 
 * Valid actions include:
 * - addObject: Adds an object to the world
 * - removeObject: Removes an object from the world
 * - updatePlayerPosition: Updates a player's position
 * - playerInteract: Sends a player interaction with an object
 * - triggerObjectEvent: Triggers an event on an object
 */
@Serializable
data class WebSocketMessage(
    val action: String,
    val data: String
) {
    companion object {
        // Define valid actions as a set for validation
        val VALID_ACTIONS = setOf(
            "addObject", 
            "removeObject", 
            "updatePlayerPosition", 
            "playerInteract", 
            "triggerObjectEvent",
            "sendChatMessage",
            "receiveChatMessage"
        )
    }
}

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
