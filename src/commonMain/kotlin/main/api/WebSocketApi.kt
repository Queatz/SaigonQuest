package main.api

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.ApiConstants.API_BASE_URL
import main.api.UserApi.getVisitorId
import models.WebSocketAction
import org.w3c.dom.WebSocket

// Token for managing WebSocket listeners safely
data class ListenerToken(
    val action: WebSocketAction,
    val listener: (WebSocketMessage) -> Unit,
)

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
 *    WebSocketApi.updatePlayerPosition(position)
 *    WebSocketApi.playerInteract(objectId)
 *    WebSocketApi.sendChatMessage(worldId, messageText)
 * 
 * 4. Disconnect when done:
 *    WebSocketApi.disconnect()
 */
@OptIn(kotlin.time.ExperimentalTime::class)
object WebSocketApi {
    // Public scope for extension functions that need to launch coroutines
    val apiScope = MainScope()
    
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
    
    private fun logError(message: String, error: Throwable? = null) {
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
        val url = "$API_BASE_URL/ws?worldId=$worldId&token=$token&visitorId=${if (token.isNotBlank()) "" else getVisitorId()}"
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
            // This is now handled by the WebSocketPlayerExtensions.kt extension function
            // The extension function will be called by the client code after connection
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
            } catch (e: Throwable) {
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
                } catch (e: Throwable) {
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
     * Remove a listener for a specific action
     */
    fun removeListener(action: WebSocketAction, listener: (WebSocketMessage) -> Unit) {
        listeners[action]?.remove(listener)
    }

    // Remove a listener using a token
    fun removeListener(token: ListenerToken) {
        removeListener(token.action, token.listener)
    }

    /**
     * Remove all listeners for a specific action
     */
    fun removeAllListeners(action: WebSocketAction) {
        listeners.remove(action)
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

    // Add a listener and return a ListenerToken for safe removal
    fun addListenerWithToken(action: WebSocketAction, listener: (WebSocketMessage) -> Unit): ListenerToken {
        addListener(action, listener)
        return ListenerToken(action, listener)
    }
    
    /**
     * Send a message to the WebSocket server
     *
     * This is a utility method that can be used by extension functions to send messages
     * without needing access to private members.
     * 
     * @param message The WebSocketMessage to send
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendMessage(message: WebSocketMessage): Boolean {
        if (!_isConnected) {
            console.warn("Cannot send message: WebSocket not connected")
            _lastError = "WebSocket not connected"
            return false
        }
        
        try {
            val messageString = ApiClient.json.encodeToString(message)
            socket?.send(messageString)
            return true
        } catch (e: Throwable) {
            console.error("Error sending message", e)
            _lastError = "Error sending message: ${e.message}"
            return false
        }
    }
    
    /**
     * Get the number of listeners for a specific action
     * 
     * This is a utility method that can be used by extension functions to get the number
     * of listeners for a specific action without needing access to private members.
     * 
     * @param action The WebSocketAction to get the listener count for
     * @return The number of listeners for the specified action
     */
    fun getListenerCount(action: WebSocketAction): Int {
        return listeners[action]?.size ?: 0
    }
}
