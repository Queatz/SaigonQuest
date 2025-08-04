package main.api

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import main.api.ApiConstants.API_BASE_URL
import models.ChatMessage
import models.Object
import models.PlayerInteractData
import models.PlayerPositionData
import models.Position
import models.TriggerEventData
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import org.w3c.dom.WebSocket
import kotlin.time.Clock

/**
 * API client for WebSocket operations
 * 
 * This API provides real-time communication with the server using WebSockets.
 * It handles connection management, message sending/receiving, and event listeners.
 * 
 * The WebSocket route (/ws) handles the following actions:
 * - ADD_OBJECT: Adds an object to the world
 * - REMOVE_OBJECT: Removes an object from the world
 * - UPDATE_PLAYER_POSITION: Updates a player's position
 * - PLAYER_INTERACT: Sends a player interaction with an object
 * - SEND_CHAT_MESSAGE: Sends a chat message to all users in the same world
 * - RECEIVE_CHAT_MESSAGE: Receives a chat message from another user
 * 
 * Logging:
 * The API uses a configurable logging system with the following levels:
 * - NONE: No logging
 * - ERROR: Only errors (default)
 * - WARN: Errors and warnings
 * - INFO: Normal operational messages
 * - DEBUG: Detailed debug information
 * 
 * You can change the log level at runtime using:
 *    WebSocketApi.setLogLevel(WebSocketApi.LogLevel.DEBUG) // For debugging
 *    WebSocketApi.setLogLevel(WebSocketApi.LogLevel.ERROR) // For production
 *
 * Usage:
 * 1. Connect to the WebSocket server:
 *    WebSocketApi.connect(worldId)
 * 
 * 2. Add listeners for events:
 *    WebSocketApi.addListener(WebSocketAction.ADD_OBJECT) { message ->
 *        // Handle addObject event
 *    }
 *    WebSocketApi.addListener(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
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
    private val listeners = mutableMapOf<WebSocketAction, MutableList<(WebSocketMessage) -> Unit>>()
    
    // Logging level control
    enum class LogLevel {
        NONE,    // No logging
        ERROR,   // Only errors
        WARN,    // Errors and warnings
        INFO,    // Normal operational messages
        DEBUG    // Detailed debug information
    }
    
    // Set the default log level to ERROR for production
    private var logLevel = LogLevel.ERROR
    
    /**
     * Set the logging level
     * 
     * This allows changing the verbosity of logging at runtime.
     * For debugging, set to LogLevel.DEBUG to see all messages.
     * For production, use LogLevel.ERROR (default) to only see errors.
     * 
     * @param level The new logging level
     */
    fun setLogLevel(level: LogLevel) {
        logInfo("Changing WebSocket log level from $logLevel to $level")
        logLevel = level
    }
    
    /**
     * Get the current logging level
     * 
     * @return The current logging level
     */
    fun getLogLevel(): LogLevel = logLevel
    
    // Connection state tracking
    private var _isConnected = false
    private var _isConnecting = false
    private var _lastError: String? = null
    private var _currentWorldId: String? = null
    private var _reconnectAttempts = 0
    private const val MAX_RECONNECT_ATTEMPTS = 5
    private const val RECONNECT_DELAY_MS = 2000 // 2 seconds
    
    // Heartbeat settings
    private const val HEARTBEAT_INTERVAL_MS = 30000 // 30 seconds
    private var heartbeatTimerId: Int? = null
    
    // Logging functions
    private fun logDebug(message: String) {
        if (logLevel >= LogLevel.DEBUG) {
            console.log("[DEBUG] $message")
        }
    }
    
    private fun logInfo(message: String) {
        if (logLevel >= LogLevel.INFO) {
            console.log("[INFO] $message")
        }
    }
    
    private fun logWarn(message: String) {
        if (logLevel >= LogLevel.WARN) {
            console.warn("[WARN] $message")
        }
    }
    
    private fun logError(message: String, error: Exception? = null) {
        if (logLevel >= LogLevel.ERROR) {
            console.error("[ERROR] $message")
            error?.let { console.error(it) }
        }
    }
    
    /**
     * Check if the WebSocket is currently connected
     */
    fun isConnected(): Boolean = _isConnected
    
    /**
     * Check if the WebSocket is currently connecting
     */
    fun isConnecting(): Boolean = _isConnecting
    
    /**
     * Get the last error message, if any
     */
    fun getLastError(): String? = _lastError
    
    /**
     * Get the current world ID
     */
    fun getCurrentWorldId(): String? = _currentWorldId
    
    /**
     * Connect to the WebSocket server
     */
    fun connect(worldId: String) {
        // If already connected to the same world, don't try to connect again
        if (_isConnected && _currentWorldId == worldId) {
            logInfo("WebSocket already connected to world: $worldId, skipping connection attempt")
            return
        }
        
        // If connecting, don't try to connect again
        if (_isConnecting) {
            logInfo("WebSocket already connecting, skipping connection attempt")
            return
        }
        
        // Close existing connection if any
        disconnect(false) // Don't clear worldId as we're about to set it
        
        // Update connection state
        _isConnecting = true
        _lastError = null
        _currentWorldId = worldId
        _reconnectAttempts = 0
        
        logInfo("Connecting to WebSocket server for world: $worldId")

        // Get the current user token
        val token = UserApi.getToken() ?: ""
        logDebug("Using token for WebSocket connection: ${if (token.isNotEmpty()) "Yes" else "No"}")

        // Create a new WebSocket connection
        val protocol = if (window.location.protocol == "https:") "wss:" else "ws:"
        val host = window.location.host
        val url = "$API_BASE_URL/ws?worldId=$worldId&token=$token"
        logDebug("WebSocket URL: $url")
        socket = WebSocket(url)

        // Set up event handlers
        socket?.onopen = { event ->
            _isConnected = true
            _isConnecting = false
            _lastError = null
            _reconnectAttempts = 0
            logInfo("WebSocket connection opened successfully")
            
            // Update connection manager
            WebSocketConnectionManager.updateConnectionState()
            
            // Start heartbeat to keep connection alive
            startHeartbeat()
            
            // Send user join notification with default position
            // The actual position will be updated later when the player moves
            val isAuthenticated = UserApi.getToken() != null
            sendUserJoin(worldId, Position(0f, 0f, 0f), isAuthenticated)
        }

        socket?.onmessage = { event ->
            val messageData = event.data.toString()
            logDebug("Received WebSocket message: $messageData")
            
            try {
                // Try to parse the message as a WebSocketMessage
                val message = ApiClient.json.decodeFromString<WebSocketMessage>(messageData)
                
                // Check if the message is valid
                if (message.isValid()) {
                    logDebug("Valid WebSocket message: action=${message.action}, data=${message.data}")
                    
                    // Debug log for specific actions we're interested in
                    when (message.action) {
                        WebSocketAction.USER_JOIN -> logDebug("WebSocketApi: Received USER_JOIN message")
                        WebSocketAction.USER_LEAVE -> logDebug("WebSocketApi: Received USER_LEAVE message")
                        WebSocketAction.UPDATE_PLAYER_POSITION -> logDebug("WebSocketApi: Received UPDATE_PLAYER_POSITION message")
                        else -> logDebug("WebSocketApi: Received ${message.action} message")
                    }
                    
                    // Check if we have any listeners for this action
                    message.action?.let { action ->
                        val actionListeners = listeners[action]
                        if (actionListeners.isNullOrEmpty()) {
                            logWarn("WebSocketApi: No listeners registered for action: $action")
                        } else {
                            logDebug("WebSocketApi: Notifying ${actionListeners.size} listeners for action: $action")
                            actionListeners.forEach { listener ->
                                scope.launch {
                                    listener(message)
                                }
                            }
                        }
                    }
                    
                    // Notify listeners for all actions (if we had a wildcard mechanism)
                } else {
                    val errorMsg = "Invalid WebSocket message: Missing required fields or unexpected action: ${message.action}"
                    _lastError = errorMsg
                    logWarn(errorMsg)
                }
            } catch (e: Exception) {
                _lastError = "Error parsing WebSocket message: ${e.message}"
                logError("Error parsing WebSocket message. Raw message: $messageData", e)
            }
        }

        socket?.onclose = { event ->
            _isConnected = false
            _isConnecting = false
            logInfo("WebSocket connection closed, attempting to reconnect...")
            
            // Update connection manager
            WebSocketConnectionManager.updateConnectionState()
            
            // Attempt to reconnect
            attemptReconnect()
        }

        socket?.onerror = { event ->
            _isConnecting = false
            _isConnected = false
            _lastError = "WebSocket error occurred: Unable to connect to server"
            logError("WebSocket error")
            logError("WebSocket URL: $url")
            logError("World ID: $worldId")
            logError("Connection state before error: Connected=${_isConnected}, Connecting=${_isConnecting}")
            
            // Update connection manager
            WebSocketConnectionManager.updateConnectionState()
            
            // Attempt to reconnect after error
            window.setTimeout({
                if (!_isConnected && !_isConnecting) {
                    logInfo("Attempting to reconnect after WebSocket error...")
                    attemptReconnect()
                }
            }, 1000) // Wait 1 second before attempting to reconnect
        }
    }
    
    /**
     * Attempt to reconnect to the WebSocket server
     * 
     * This will try to reconnect to the last known world ID up to MAX_RECONNECT_ATTEMPTS times
     * with a delay of RECONNECT_DELAY_MS between attempts.
     */
    private fun attemptReconnect() {
        if (_reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logError("Maximum reconnection attempts reached, giving up")
            _lastError = "Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts"
            return
        }
        
        _reconnectAttempts++
        
        val worldId = _currentWorldId
        if (worldId == null) {
            logError("Cannot reconnect: No world ID available")
            _lastError = "Cannot reconnect: No world ID available"
            return
        }
        
        logInfo("Attempting to reconnect (attempt $_reconnectAttempts of $MAX_RECONNECT_ATTEMPTS)...")
        
        // Schedule reconnection after delay
        window.setTimeout({
            if (!_isConnected && !_isConnecting) {
                logInfo("Reconnecting to world: $worldId")
                connect(worldId)
            }
        }, RECONNECT_DELAY_MS)
    }

    /**
     * Start the heartbeat to keep the WebSocket connection alive
     * 
     * This sends a ping message to the server every HEARTBEAT_INTERVAL_MS milliseconds
     * to prevent the connection from being closed due to inactivity.
     */
    private fun startHeartbeat() {
        // Stop any existing heartbeat
        stopHeartbeat()
        
        logDebug("Starting WebSocket heartbeat (interval: $HEARTBEAT_INTERVAL_MS ms)")
        
        // Start a new heartbeat timer
        heartbeatTimerId = window.setInterval({
            if (_isConnected && socket != null) {
                try {
                    // Send a simple ping message
                    // We use a custom ping format since WebSocket.send() doesn't support the WebSocket ping frame directly in all browsers
                    val timestamp = js("Date.now()")
                    val pingMessage = WebSocketMessage.fromEnum(
                        WebSocketAction.USER_JOIN, // Reuse an existing action as a ping
                        "{\"ping\": $timestamp}"
                    )
                    val messageString = ApiClient.json.encodeToString(pingMessage)
                    socket?.send(messageString)
                    logDebug("WebSocket heartbeat sent")
                } catch (e: Exception) {
                    logError("Error sending heartbeat", e)
                    // If we can't send a heartbeat, the connection might be dead
                    if (_isConnected) {
                        _isConnected = false
                        _isConnecting = false
                        WebSocketConnectionManager.updateConnectionState()
                        attemptReconnect()
                    }
                }
            } else {
                // If we're not connected, stop the heartbeat
                stopHeartbeat()
            }
        }, HEARTBEAT_INTERVAL_MS)
    }
    
    /**
     * Stop the heartbeat
     */
    private fun stopHeartbeat() {
        heartbeatTimerId?.let {
            logDebug("Stopping WebSocket heartbeat")
            window.clearInterval(it)
            heartbeatTimerId = null
        }
    }

    /**
     * Disconnect from the WebSocket server
     * 
     * @param clearWorldId If true, clears the current world ID to prevent reconnection attempts
     */
    fun disconnect(clearWorldId: Boolean = true) {
        // Stop heartbeat
        stopHeartbeat()
        
        // Send user leave notification if connected
        if (_isConnected && _currentWorldId != null) {
            logInfo("Sending user leave notification before disconnecting")
            sendUserLeave(_currentWorldId!!)
        }
        
        if (socket != null) {
            logInfo("Disconnecting from WebSocket server")
            socket?.close()
            socket = null
        }
        
        // Update connection state
        _isConnected = false
        _isConnecting = false
        
        // Reset reconnection state if requested
        if (clearWorldId) {
            _currentWorldId = null
            _reconnectAttempts = 0
        }
    }

    /**
     * Check if the WebSocket is ready to send messages
     * 
     * @return true if the WebSocket is connected, false otherwise
     */
    private fun checkConnection(): Boolean {
        if (!_isConnected) {
            console.warn("Cannot send message: WebSocket not connected")
            _lastError = "WebSocket not connected"
            return false
        }
        return true
    }

    /**
     * Send a message to the WebSocket server with an Object
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendObjectMessage(action: WebSocketAction, data: Object): Boolean {
        if (!checkConnection()) return false
        
        try {
            val message = WebSocketMessage.fromEnum(action, ApiClient.json.encodeToString(data))
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Exception) {
            console.error("Error sending object message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }

    /**
     * Send a message to the WebSocket server with a String
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendStringMessage(action: WebSocketAction, data: String): Boolean {
        if (!checkConnection()) return false
        
        try {
            val message = WebSocketMessage.fromEnum(action, ApiClient.json.encodeToString(data))
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Exception) {
            console.error("Error sending string message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }

    /**
     * Send a message to the WebSocket server with a PlayerPositionData
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendPlayerPositionMessage(action: WebSocketAction, data: PlayerPositionData): Boolean {
        if (!checkConnection()) return false
        
        try {
            val message = WebSocketMessage.fromEnum(action, ApiClient.json.encodeToString(data))
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Exception) {
            console.error("Error sending player position message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }

    /**
     * Send a message to the WebSocket server with a PlayerInteractData
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendPlayerInteractMessage(action: WebSocketAction, data: PlayerInteractData): Boolean {
        if (!checkConnection()) return false
        
        try {
            val message = WebSocketMessage.fromEnum(action, ApiClient.json.encodeToString(data))
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Exception) {
            console.error("Error sending player interact message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }

    /**
     * Send a message to the WebSocket server with a TriggerEventData
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendTriggerEventMessage(action: WebSocketAction, data: TriggerEventData): Boolean {
        if (!checkConnection()) return false
        
        try {
            val message = WebSocketMessage.fromEnum(action, ApiClient.json.encodeToString(data))
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Exception) {
            console.error("Error sending trigger event message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }
    
    /**
     * Send a message to the WebSocket server with a UserJoinData
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendUserJoinMessage(action: WebSocketAction, data: UserJoinData): Boolean {
        console.log("WebSocketApi: Preparing to send UserJoinMessage")
        
        if (!checkConnection()) {
            console.error("WebSocketApi: Cannot send UserJoinMessage - WebSocket not connected")
            return false
        }
        
        try {
            console.log("WebSocketApi: Creating WebSocketMessage with action: $action")
            val serializedData = ApiClient.json.encodeToString(data)
            console.log("WebSocketApi: Serialized UserJoinData: $serializedData")
            
            val message = WebSocketMessage.fromEnum(action, serializedData)
            console.log("WebSocketApi: Created WebSocketMessage: action=${message.action}, data length=${message.data.length}")
            
            val messageString = ApiClient.json.encodeToString(message)
            console.log("WebSocketApi: Final serialized message to send: $messageString")
            
            socket?.send(messageString)
            console.log("WebSocketApi: UserJoinMessage sent successfully")
            return true
        } catch (e: Exception) {
            console.error("WebSocketApi: Error sending user join message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }
    
    /**
     * Send a message to the WebSocket server with a UserLeaveData
     * 
     * @return true if the message was sent, false otherwise
     */
    private fun sendUserLeaveMessage(action: WebSocketAction, data: UserLeaveData): Boolean {
        if (!checkConnection()) return false
        
        try {
            val message = WebSocketMessage.fromEnum(action, ApiClient.json.encodeToString(data))
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Exception) {
            console.error("Error sending user leave message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }

    /**
     * Add an object to the world
     * 
     * @return true if the object was added successfully, false otherwise
     */
    fun addObject(obj: Object): Boolean {
        return sendObjectMessage(WebSocketAction.ADD_OBJECT, obj)
    }

    /**
     * Remove an object from the world
     * 
     * @return true if the object was removed successfully, false otherwise
     */
    fun removeObject(objectId: String): Boolean {
        return sendStringMessage(WebSocketAction.REMOVE_OBJECT, objectId)
    }
    
    /**
     * Update an object in the world
     * 
     * This sends the updated object to all clients in the world.
     * Used primarily for position updates when objects are moved.
     * 
     * @param obj The updated object
     * @return true if the object was updated successfully, false otherwise
     */
    fun updateObject(obj: Object): Boolean {
        return sendObjectMessage(WebSocketAction.OBJECT_UPDATE, obj)
    }
    
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
    suspend fun updatePlayerPosition(position: Position, isPlayer: Boolean): Boolean {
        try {
            if (!_isConnected) {
                console.warn("Cannot update player position: WebSocket not connected")
                return false
            }
            
            val user = UserApi.getCurrentUser()
            val positionData = PlayerPositionData(
                position = position,
                isPlayer = isPlayer,
                userId = user.key
            )
            return sendPlayerPositionMessage(WebSocketAction.UPDATE_PLAYER_POSITION, positionData)
        } catch (e: Exception) {
            console.error("Error updating player position", e)
            _lastError = "Error updating player position: ${e.message}"
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
    fun playerInteract(objectId: String, isPlayer: Boolean): Boolean {
        var success = false
        
        scope.launch {
            try {
                if (!_isConnected) {
                    console.warn("Cannot send player interaction: WebSocket not connected")
                    return@launch
                }
                
                val user = UserApi.getCurrentUser()
                val interactData = PlayerInteractData(
                    objectId = objectId,
                    isPlayer = isPlayer,
                    userId = user.key
                )
                success = sendPlayerInteractMessage(WebSocketAction.USER_INTERACT, interactData)
            } catch (e: Exception) {
                console.error("Error sending player interaction", e)
                _lastError = "Error sending player interaction: ${e.message}"
            }
        }
        
        return success
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
     * @return true if the event was triggered successfully, false otherwise
     */
    fun triggerObjectEvent(objectId: String, eventType: String, userId: String): Boolean {
        if (!_isConnected) {
            console.warn("Cannot trigger object event: WebSocket not connected")
            return false
        }
        
        val eventData = TriggerEventData(
            objectId = objectId,
            eventType = eventType,
            userId = userId
        )
        return sendTriggerEventMessage(WebSocketAction.TRIGGER_OBJECT_EVENT, eventData)
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
    fun sendUserJoin(worldId: String, position: Position, isPlayer: Boolean): Boolean {
        var success = false
        
        console.log("WebSocketApi: Attempting to send USER_JOIN notification for world: $worldId")
        
        scope.launch {
            try {
                if (!_isConnected) {
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
                success = sendUserJoinMessage(WebSocketAction.USER_JOIN, joinData)
                
                if (success) {
                    console.log("WebSocketApi: User join notification sent successfully")
                } else {
                    console.error("WebSocketApi: Failed to send user join notification: ${_lastError ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                console.error("WebSocketApi: Error sending user join notification", e)
                _lastError = "Error sending user join notification: ${e.message}"
            }
        }
        
        return success
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
    fun sendUserLeave(worldId: String): Boolean {
        var success = false
        
        scope.launch {
            try {
                if (!_isConnected) {
                    console.warn("Cannot send user leave notification: WebSocket not connected")
                    return@launch
                }
                
                val user = UserApi.getCurrentUser()
                val leaveData = UserLeaveData(
                    userId = user.key,
                    worldId = worldId
                )
                
                success = sendUserLeaveMessage(WebSocketAction.USER_LEAVE, leaveData)
                
                if (success) {
                    console.log("User leave notification sent successfully")
                } else {
                    console.error("Failed to send user leave notification: ${_lastError ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                console.error("Error sending user leave notification", e)
                _lastError = "Error sending user leave notification: ${e.message}"
            }
        }
        
        return success
    }
    
    /**
     * Send a message to the WebSocket server with a ChatMessage
     * 
     * @return true if the message was sent successfully, false otherwise
     */
    private fun sendChatMessageData(action: WebSocketAction, data: ChatMessage): Boolean {
        if (!checkConnection()) return false
        
        try {
            console.log("Preparing to send chat message with action: $action")
            val serializedData = ApiClient.json.encodeToString(data)
            console.log("Serialized chat message data: $serializedData")
            
            val message = WebSocketMessage.fromEnum(action, serializedData)
            console.log("Created WebSocketMessage: action=${message.action}, data length=${message.data.length}")
            
            val messageString = ApiClient.json.encodeToString(message)
            console.log("Final serialized message to send: $messageString")
            
            socket?.send(messageString)
            console.log("Chat message sent successfully: ${data.message}")
            return true
        } catch (e: Exception) {
            console.error("Error sending chat message data", e)
            _lastError = "Error sending chat message: ${e.message}"
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
    fun sendChatMessage(worldId: String, messageText: String, callback: (Boolean) -> Unit) {
        // Validate input parameters
        if (worldId.isEmpty()) {
            _lastError = "Cannot send chat message: worldId is empty"
            console.error(_lastError ?: "")
            callback(false)
            return
        }

        if (messageText.isEmpty()) {
            _lastError = "Cannot send chat message: message is empty"
            console.error(_lastError ?: "")
            callback(false)
            return
        }

        // Check connection status
        if (!_isConnected) {
            _lastError = "Cannot send chat message: WebSocket not connected"
            console.warn(_lastError ?: "")
            callback(false)
            return
        }

        scope.launch {
            try {
                // Get current user
                val user = try {
                    UserApi.getCurrentUser()
                } catch (e: Exception) {
                    console.error("Error getting current user", e)
                    _lastError = "Error getting current user: ${e.message}"
                    callback(false)
                    return@launch
                }

                // Validate user data
                if (user.key.isEmpty()) {
                    _lastError = "Cannot send chat message: User not authenticated"
                    console.error(_lastError ?: "")
                    callback(false)
                    return@launch
                }

                // Create chat message
                val chatMessage = ChatMessage(
                    worldId = worldId,
                    userId = user.key,
                    username = user.username,
                    message = messageText,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )

                // Send message
                console.log("Sending chat message: $messageText")
                val success = sendChatMessageData(WebSocketAction.SEND_CHAT_MESSAGE, chatMessage)

                if (!success) {
                    console.error("Failed to send chat message: ${_lastError ?: "Unknown error"}")
                }

                // Call the callback with the result
                callback(success)
            } catch (e: Exception) {
                console.error("Error creating chat message", e)
                _lastError = "Error creating chat message: ${e.message}"
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
    fun updateChatMessage(message: ChatMessage, newText: String, callback: (Boolean) -> Unit) {
        // Validate input parameters
        if (message.key.isEmpty()) {
            _lastError = "Cannot update chat message: message key is empty"
            console.error(_lastError ?: "")
            callback(false)
            return
        }

        if (newText.isEmpty()) {
            _lastError = "Cannot update chat message: new text is empty"
            console.error(_lastError ?: "")
            callback(false)
            return
        }

        // Check connection status
        if (!_isConnected) {
            _lastError = "Cannot update chat message: WebSocket not connected"
            console.warn(_lastError ?: "")
            callback(false)
            return
        }

        scope.launch {
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
                    console.error("Failed to update chat message: ${_lastError ?: "Unknown error"}")
                }

                // Call the callback with the result
                callback(success)
            } catch (e: Exception) {
                console.error("Error updating chat message", e)
                _lastError = "Error updating chat message: ${e.message}"
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
    fun deleteChatMessage(message: ChatMessage, callback: (Boolean) -> Unit) {
        // Validate input parameters
        if (message.key.isEmpty()) {
            _lastError = "Cannot delete chat message: message key is empty"
            console.error(_lastError ?: "")
            callback(false)
            return
        }

        // Check connection status
        if (!_isConnected) {
            _lastError = "Cannot delete chat message: WebSocket not connected"
            console.warn(_lastError ?: "")
            callback(false)
            return
        }

        scope.launch {
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
                    console.error("Failed to delete chat message: ${_lastError ?: "Unknown error"}")
                }

                // Call the callback with the result
                callback(success)
            } catch (e: Exception) {
                console.error("Error deleting chat message", e)
                _lastError = "Error deleting chat message: ${e.message}"
                callback(false)
            }
        }
    }

    /**
     * Add a listener for a specific action
     */
    fun addListener(action: WebSocketAction, listener: (WebSocketMessage) -> Unit) {
        console.log("WebSocketApi: Adding listener for action: $action")
        
        // Check if we're connected
        if (!_isConnected) {
            console.warn("WebSocketApi: Adding listener while not connected! This might cause issues.")
        }
        
        // Initialize the list if it doesn't exist
        if (!listeners.containsKey(action)) {
            console.log("WebSocketApi: Creating new listener list for action: $action")
            listeners[action] = mutableListOf()
        }
        
        // Check for duplicate listeners
        val existingListeners = listeners[action] ?: mutableListOf()
        if (existingListeners.contains(listener)) {
            console.warn("WebSocketApi: Duplicate listener being added for action: $action")
            return
        }
        
        // Add the listener
        listeners[action]?.add(listener)
        console.log("WebSocketApi: Listener added for action: $action, total listeners: ${listeners[action]?.size}")
    }
    
    /**
     * Add a listener for user join events
     * 
     * @param listener A function that takes a UserJoinData and returns Unit
     */
    fun addUserJoinListener(listener: (UserJoinData) -> Unit) {
        console.log("WebSocketApi: Registering USER_JOIN listener")
        
        // Check if we already have listeners for this action
        val existingListeners = listeners[WebSocketAction.USER_JOIN]?.size ?: 0
        console.log("WebSocketApi: Currently have $existingListeners USER_JOIN listeners")
        
        addListener(WebSocketAction.USER_JOIN) { message ->
            console.log("WebSocketApi: USER_JOIN listener triggered with message: ${message.data}")
            try {
                val joinData = ApiClient.json.decodeFromString<UserJoinData>(message.data)
                console.log("WebSocketApi: Successfully parsed UserJoinData: userId=${joinData.userId}, username=${joinData.username}")
                listener(joinData)
            } catch (e: Exception) {
                console.error("WebSocketApi: Error parsing user join data", e)
            }
        }
        
        // Verify listener was added
        val newListenerCount = listeners[WebSocketAction.USER_JOIN]?.size ?: 0
        console.log("WebSocketApi: Now have $newListenerCount USER_JOIN listeners")
    }
    
    /**
     * Add a listener for user leave events
     * 
     * @param listener A function that takes a UserLeaveData and returns Unit
     */
    fun addUserLeaveListener(listener: (UserLeaveData) -> Unit) {
        addListener(WebSocketAction.USER_LEAVE) { message ->
            try {
                val leaveData = ApiClient.json.decodeFromString<UserLeaveData>(message.data)
                listener(leaveData)
            } catch (e: Exception) {
                console.error("Error parsing user leave data", e)
            }
        }
    }
    
    /**
     * Add a listener for player position update events
     * 
     * @param listener A function that takes a PlayerPositionData and returns Unit
     */
    fun addPlayerPositionListener(listener: (PlayerPositionData) -> Unit) {
        addListener(WebSocketAction.UPDATE_PLAYER_POSITION) { message ->
            try {
                val positionData = ApiClient.json.decodeFromString<PlayerPositionData>(message.data)
                listener(positionData)
            } catch (e: Exception) {
                console.error("Error parsing player position data", e)
            }
        }
    }

    /**
     * Remove a listener for a specific action
     */
    fun removeListener(action: WebSocketAction, listener: (WebSocketMessage) -> Unit) {
        listeners[action]?.remove(listener)
    }

    /**
     * Remove all listeners for a specific action
     */
    fun removeAllListeners(action: WebSocketAction) {
        listeners.remove(action)
    }
}

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

// Data models moved to shared module: models.WebSocketModels
