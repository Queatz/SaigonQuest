package main.api

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import models.Position

/**
 * Manages WebSocket connections for the application.
 * 
 * This manager provides a centralized way to handle WebSocket connections,
 * ensuring that connections are only established when needed and properly maintained.
 * 
 * It handles two main types of WebSocket messages:
 * - Local-to-world updates (player movement, added objects, etc.)
 * - Global-to-player updates (friend requests, notifications, etc.)
 * 
 * Usage:
 * 1. Initialize the connection manager:
 *    WebSocketConnectionManager.initialize()
 * 
 * 2. Connect to a world:
 *    WebSocketConnectionManager.connectToWorld(worldId)
 * 
 * 3. Disconnect from a world:
 *    WebSocketConnectionManager.disconnectFromWorld(worldId)
 * 
 * 4. Disconnect completely:
 *    WebSocketConnectionManager.disconnect()
 */
object WebSocketConnectionManager {
    private val scope = MainScope()
    private var isInitialized = false
    
    // Connection states
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
    
    private val _connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    val connectionState: State<ConnectionState> = _connectionState
    
    // Current world ID
    private val _currentWorldId = mutableStateOf<String?>(null)
    val currentWorldId: State<String?> = _currentWorldId
    
    /**
     * Initialize the connection manager
     * 
     * This sets up the reconnection logic and should be called once at application startup.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        
        // Set up reconnection logic
        scope.launch {
            while (true) {
                if (_connectionState.value == ConnectionState.DISCONNECTED && 
                    _currentWorldId.value != null) {
                    reconnect()
                }
                delay(5000) // Check every 5 seconds
            }
        }
    }
    
    /**
     * Connect to a world
     * 
     * This establishes a WebSocket connection to the specified world.
     * If already connected to the same world, it does nothing.
     * If connected to a different world, it disconnects first.
     * 
     * @param worldId The ID of the world to connect to
     */
    fun connectToWorld(worldId: String) {
        // If already connected to the same world, do nothing
        if (_connectionState.value == ConnectionState.CONNECTED && 
            _currentWorldId.value == worldId) {
            console.log("WebSocketConnectionManager: Already connected to world: $worldId")
            return
        }
        
        // If connected to a different world, disconnect first
        if (_connectionState.value == ConnectionState.CONNECTED && 
            _currentWorldId.value != null && 
            _currentWorldId.value != worldId) {
            disconnectFromWorld(_currentWorldId.value!!)
        }
        
        _currentWorldId.value = worldId
        
        if (_connectionState.value != ConnectionState.CONNECTED) {
            WebSocketApi.connect(worldId)
            _connectionState.value = ConnectionState.CONNECTING
        }
    }
    
    /**
     * Disconnect from a specific world
     * 
     * This disconnects the WebSocket connection if currently connected to the specified world.
     * 
     * @param worldId The ID of the world to disconnect from
     */
    fun disconnectFromWorld(worldId: String) {
        // Only disconnect if connected to the specified world
        if (_currentWorldId.value == worldId) {
            WebSocketApi.disconnect()
            _connectionState.value = ConnectionState.DISCONNECTED
            _currentWorldId.value = null
        }
    }
    
    /**
     * Disconnect from the current world
     * 
     * This disconnects the WebSocket connection regardless of which world is connected.
     */
    fun disconnect() {
        WebSocketApi.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentWorldId.value = null
    }
    
    /**
     * Reconnect to the current world
     * 
     * This attempts to reconnect to the current world if disconnected.
     */
    private fun reconnect() {
        _currentWorldId.value?.let { worldId ->
            WebSocketApi.connect(worldId)
            _connectionState.value = ConnectionState.CONNECTING
        }
    }
    
    /**
     * Update connection state based on WebSocketApi status
     * 
     * This should be called whenever the WebSocketApi connection state changes.
     */
    fun updateConnectionState() {
        _connectionState.value = when {
            WebSocketApi.isConnected() -> ConnectionState.CONNECTED
            WebSocketApi.isConnecting() -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
    }
}
