package main.components.player

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Node
import lib.Scene
import lib.Vector3
import main.api.ApiClient
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.shadow.ShadowManager
import main.components.world.CharacterDirection
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import models.ChatMessage
import models.PlayerPositionData
import models.Position
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction

/**
 * Controller for managing other players in the 3D scene.
 * 
 * This class handles:
 * - Creating and removing meshes for other players
 * - Updating other players' positions
 * - Applying animations when players move
 * - Adding join/leave effects
 */
class OtherPlayersController(private val scene: Scene) {
    
    // Map of player handles by userId
    private val playerHandles = mutableMapOf<String, WorldObjectHandle>()
    
    // Map of player meshes by userId (for backward compatibility)
    private val playerNodes = mutableMapOf<String, Node>()
    
    // Map of player target positions by userId
    private val playerTargetPositions = mutableMapOf<String, Vector3>()
    
    // Map of player join/leave animation times by userId
    private val playerAnimationTimes = mutableMapOf<String, Double>()
    
    // Map of username text billboard handles by userId
    private val usernameTextHandles = mutableMapOf<String, WorldObjectHandle>()
    
    // Map of chat message text billboard handles by userId
    private val chatMessageTextHandles = mutableMapOf<String, WorldObjectHandle>()
    
    // Map of chat message removal timeouts by userId
    private val chatMessageTimeouts = mutableMapOf<String, Int>()
    
    // Map of usernames by userId
    private val playerUsernames = mutableMapOf<String, String>()
    
    // Map of isPlayer flags by userId
    private val playerIsPlayerFlags = mutableMapOf<String, Boolean>()
    
    // Reference to the world object manager for creating text billboards
    private var worldObjectManager: WorldObjectManager? = null
    
    // Reference to the shadow manager for configuring shadows
    private var shadowManager: ShadowManager? = null
    
    // Base movement speed in units per second for framerate-independent movement
    private val baseMovementSpeed = 3.0f
    
    // Time tracking for framerate-independent movement
    private var lastFrameTime = 0.0
    
    // Animation duration in seconds
    private val joinLeaveDuration = 0.5
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default)
    
    // Current user ID
    private var myUserId: String? = null
    
    /**
     * Sets the world object manager for creating text billboards
     * 
     * @param manager The world object manager instance
     */
    fun setWorldObjectManager(manager: WorldObjectManager) {
        worldObjectManager = manager
        console.log("OtherPlayersController: WorldObjectManager set")
    }
    
    /**
     * Sets the shadow manager for configuring shadows
     * 
     * @param manager The shadow manager instance
     */
    fun setShadowManager(manager: ShadowManager) {
        shadowManager = manager
        console.log("OtherPlayersController: ShadowManager set")
        
        // Configure shadows for existing players
        configureShadowsForPlayers(manager)
    }
    
    /**
     * Configure shadow casting for a specific player
     * 
     * @param userId The user ID of the player
     */
    private fun configureShadowForPlayer(userId: String) {
        // Get the player handle
        val handle = playerHandles[userId] ?: return
        
        // Get the shadow manager
        val manager = shadowManager ?: return
        
        // Configure shadow casting for the player
        handle.node.let { node ->
            node.getChildMeshes().forEach { mesh ->
                // Add the mesh as a shadow caster
                manager.addShadowCaster(mesh)

                // Configure the mesh as a shadow receiver
                manager.configureShadowReceiver(mesh)
            }
            
            console.log("OtherPlayersController: Configured shadows for player $userId")
        }
    }
    
    /**
     * Get the current player's position
     */
    private fun getPlayerPosition(): Position {
        val playerController = main.components.SceneManager.getPlayerController()
        val playerPos = playerController?.getPlayerPosition()
        
        return if (playerPos != null) {
            Position(playerPos.x, playerPos.y, playerPos.z)
        } else {
            Position(0f, 0f, 0f)
        }
    }
    
    /**
     * Initialize the controller and set up WebSocket listeners
     */
    fun initialize() {
        // Get current user ID
        scope.launch {
            try {
                val user = UserApi.getCurrentUser()
                myUserId = user.key
                console.log("OtherPlayersController: Current user ID: $myUserId")
            } catch (e: Exception) {
                console.error("OtherPlayersController: Error getting current user", e)
            }
        }
        
        console.log("OtherPlayersController: Setting up WebSocket listeners")
        
        // Set up WebSocket listeners
        WebSocketApi.addUserJoinListener { joinData ->
            console.log("OtherPlayersController: Received USER_JOIN event: ${joinData.userId}, ${joinData.username}")
            handleUserJoin(joinData)
            
            // When a new user joins, send our own join notification to ensure they see us
            if (joinData.userId != myUserId) {
                scope.launch {
                    try {
                        val worldId = WebSocketApi.getCurrentWorldId()
                        if (worldId != null) {
                            val user = UserApi.getCurrentUser()
                            val playerPosition = getPlayerPosition()
                            console.log("OtherPlayersController: Sending reciprocal USER_JOIN notification to new user: ${joinData.userId}")
                            WebSocketApi.sendUserJoin(worldId, playerPosition, user.isPlayer)
                        }
                    } catch (e: Exception) {
                        console.error("OtherPlayersController: Error sending reciprocal join notification", e)
                    }
                }
            }
        }
        
        WebSocketApi.addUserLeaveListener { leaveData ->
            console.log("OtherPlayersController: Received USER_LEAVE event: ${leaveData.userId}")
            handleUserLeave(leaveData)
        }
        
        WebSocketApi.addPlayerPositionListener { positionData ->
            console.log("OtherPlayersController: Received UPDATE_PLAYER_POSITION event: ${positionData.userId}")
            handlePlayerPosition(positionData)
        }
        
        // Add listener for chat messages
        WebSocketApi.addListener(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                // Check if the message is valid
                if (message.isValid()) {
                    val chatMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    console.log("OtherPlayersController: Received RECEIVE_CHAT_MESSAGE event: ${chatMessage.userId}, ${chatMessage.message}")
                    
                    // Display the chat message above the player's head
                    displayChatMessage(chatMessage.userId, chatMessage.message)
                } else {
                    console.error("OtherPlayersController: Invalid chat message received: ${message.action}, ${message.data}")
                }
            } catch (e: Exception) {
                console.error("OtherPlayersController: Error parsing chat message", e)
            }
        }
        
        // Force a user join notification for the current user to trigger updates for all clients
        scope.launch {
            try {
                // Use WebSocketConnectionManager to check connection state
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                    val user = UserApi.getCurrentUser()
                    val worldId = WebSocketConnectionManager.currentWorldId.value
                    if (worldId != null) {
                        val playerPosition = getPlayerPosition()
                        console.log("OtherPlayersController: Sending explicit USER_JOIN notification")
                        WebSocketApi.sendUserJoin(worldId, playerPosition, user.isPlayer)
                    } else {
                        console.warn("OtherPlayersController: WebSocket is connected but no worldId available")
                    }
                } else {
                    console.warn("OtherPlayersController: Cannot send USER_JOIN notification - WebSocket not connected (state: ${WebSocketConnectionManager.connectionState.value})")
                }
            } catch (e: Exception) {
                console.error("OtherPlayersController: Error sending explicit join notification", e)
            }
        }
        
        // Set up animation loop
        scene.registerBeforeRender {
            updatePlayerPositions()
        }
    }
    
    /**
     * Handle user join event
     */
    private fun handleUserJoin(joinData: UserJoinData) {
        // Don't create a mesh for the current user
        if (joinData.userId == myUserId) {
            console.log("OtherPlayersController: Ignoring join event for current user")
            return
        }
    
        console.log("OtherPlayersController: User joined: ${joinData.username} (${joinData.userId})")
    
        // Store username and isPlayer flag
        playerUsernames[joinData.userId] = joinData.username
        playerIsPlayerFlags[joinData.userId] = joinData.isPlayer
    
        // Check if we already have this player
        if (playerNodes.containsKey(joinData.userId)) {
            console.log("OtherPlayersController: Player ${joinData.userId} already exists, updating position")
        
            // Update target position
            playerTargetPositions[joinData.userId] = Vector3(
                joinData.position.x,
                joinData.position.y,
                joinData.position.z
            )
            return
        }
    
        console.log("OtherPlayersController: Creating new player mesh for ${joinData.userId}")
    
        // Create player mesh with username
        createPlayerMesh(
            userId = joinData.userId,
            position = Vector3(
                joinData.position.x,
                joinData.position.y,
                joinData.position.z
            ),
            isPlayer = joinData.isPlayer,
            username = joinData.username
        )
    
        // Log the total number of other players
        console.log("OtherPlayersController: Total players now: ${playerNodes.size}")
    }
    
    /**
     * Handle user leave event
     */
    private fun handleUserLeave(leaveData: UserLeaveData) {
        console.log("OtherPlayersController: User left: ${leaveData.userId}")
    
        // Check if we have this player
        if (!playerNodes.containsKey(leaveData.userId)) {
            console.log("OtherPlayersController: Player ${leaveData.userId} not found, ignoring leave event")
            return
        }
    
        // Start leave animation
        playerNodes[leaveData.userId]?.let { mesh ->
            console.log("OtherPlayersController: Starting leave animation for ${leaveData.userId}")
            playerAnimationTimes[leaveData.userId] = window.performance.now()
        
            // After animation completes, remove the mesh
            window.setTimeout({
                removePlayerMesh(leaveData.userId)
            
                // Remove stored username and isPlayer flag
                playerUsernames.remove(leaveData.userId)
                playerIsPlayerFlags.remove(leaveData.userId)
            
                console.log("OtherPlayersController: Removed player mesh for ${leaveData.userId}")
                console.log("OtherPlayersController: Total players now: ${playerNodes.size}")
            }, (joinLeaveDuration * 1000).toInt())
        }
    }
    
    /**
     * Handle player position update
     */
    private fun handlePlayerPosition(positionData: PlayerPositionData) {
        // Don't update position for the current user
        if (positionData.userId == myUserId) {
            return
        }
    
        // Update target position
        val newPosition = Vector3(
            positionData.position.x,
            positionData.position.y,
            positionData.position.z
        )
    
        // Log position update
        val existingMesh = playerNodes[positionData.userId]
        if (existingMesh != null) {
            val currentPos = existingMesh.position
            val distance = currentPos.subtract(newPosition).length()
        
            // Only log significant movements to reduce console spam
            if (distance > 0.1f) {
                console.log("OtherPlayersController: Updating position for ${positionData.userId}, distance moved: $distance")
            }
        } else {
            console.log("OtherPlayersController: Received position update for unknown player: ${positionData.userId}")
        }
    
        playerTargetPositions[positionData.userId] = newPosition
    
        // Store isPlayer flag
        playerIsPlayerFlags[positionData.userId] = positionData.isPlayer
    
        // Create player mesh if it doesn't exist
        if (!playerNodes.containsKey(positionData.userId)) {
            console.log("OtherPlayersController: Creating player mesh from position update for ${positionData.userId}")
        
            // Generate a default username if none is available
            if (!playerUsernames.containsKey(positionData.userId)) {
                playerUsernames[positionData.userId] = "Player-${positionData.userId}"
            }
        
            createPlayerMesh(
                userId = positionData.userId,
                position = newPosition,
                isPlayer = positionData.isPlayer,
                username = playerUsernames[positionData.userId] // Use stored username
            )
            console.log("OtherPlayersController: Total players now: ${playerNodes.size}")
        }
    }
    
    /**
     * Create a character for another player
     * 
     * @param userId The user ID of the player
     * @param position The position of the player
     * @param isPlayer Whether the user is a player or visitor
     * @param username The username to display above the player (optional)
     */
    private fun createPlayerMesh(userId: String, position: Vector3, isPlayer: Boolean, username: String? = null) {
        // Remove existing player if any
        removePlayerMesh(userId)
        
        // Get the world object manager
        val manager = worldObjectManager
        if (manager == null) {
            console.error("OtherPlayersController: WorldObjectManager not set, cannot create character")
            return
        }
        
        // Create a sprite sheet for the player
        val spriteSheet = SpriteSheet.createPlayerSpriteSheet()
        
        // Default direction (south)
        val direction = CharacterDirection.S
        
        // Create the player character using the WorldObjectManager
        val playerHandle = manager.createCharacter(
            spriteSheet = spriteSheet,
            direction = direction,
            position = position
        )
        
        if (playerHandle == null) {
            console.error("OtherPlayersController: Failed to create character for $userId")
            return
        }
        
        // Start with scale 0 for join animation
        playerHandle.scale = Vector3(0f, 0f, 0f)
        
        // Store the player handle
        playerHandles[userId] = playerHandle
        
        // Store the mesh for backward compatibility
        playerHandle.node.let { node ->
            playerNodes[userId] = node
        }
        
        // Store target position and animation time
        playerTargetPositions[userId] = position
        playerAnimationTimes[userId] = window.performance.now()
        
        // Configure shadow casting for the player if shadow manager is available
        configureShadowForPlayer(userId)
        
        // Create username text billboard if WorldObjectManager is available
        worldObjectManager?.let { manager ->
            // Get username for this user
            try {
                // Calculate position above the player (adjust Y value as needed)
                val textPosition = Vector3(position.x, position.y + .5f, position.z)

                // Use the provided username if available, otherwise use a default name
                val displayUsername = username ?: "Player-$userId"

                // Create the text billboard with the username
                val textHandle = manager.createTextBillboard(
                    text = displayUsername,
                    position = textPosition,
                    fontSize = 18,
                    textColor = "white"
                )

                // Store the text handle
                if (textHandle != null) {
                    usernameTextHandles[userId] = textHandle
                }

                // Try to get the actual username from the server if possible
                // This would be a more advanced feature requiring additional API calls
                // For now, we'll use the default username
            } catch (e: Exception) {
                console.error("OtherPlayersController: Error creating username text for $userId", e)
            }
        }
    }
    
    /**
     * Remove a player and associated resources
     */
    private fun removePlayerMesh(userId: String) {
        // Remove the player handle if it exists
        playerHandles[userId]?.let { handle ->
            handle.remove()
            console.log("OtherPlayersController: Removed player handle for $userId")
        }
        playerHandles.remove(userId)

        // Remove the player's target position and animation time
        playerTargetPositions.remove(userId)
        playerAnimationTimes.remove(userId)
    
        // Remove the username text billboard if it exists
        usernameTextHandles[userId]?.let { textHandle ->
            textHandle.remove()
            console.log("OtherPlayersController: Removed username text for $userId")
        }
        usernameTextHandles.remove(userId)
    
        // Remove stored username and isPlayer flag
        playerUsernames.remove(userId)
        playerIsPlayerFlags.remove(userId)
    }
    
    /**
     * Update player positions and animations
     */
    private fun updatePlayerPositions() {
        // Calculate delta time for framerate-independent movement
        val currentTime = window.performance.now()
        val deltaTime = if (lastFrameTime > 0) {
            // Convert to seconds and cap at a reasonable value to prevent huge jumps after pauses
            minOf((currentTime - lastFrameTime) / 1000.0, 0.1)
        } else {
            // First frame, use a small default value
            0.016 // ~60fps
        }
        lastFrameTime = currentTime
        
        // Calculate the movement speed for this frame based on delta time
        val frameMovementSpeed = (baseMovementSpeed * deltaTime).toFloat()
        
        // Update each player
        playerHandles.forEach { (userId, handle) ->
            // Handle join/leave animations
            val animStartTime = playerAnimationTimes[userId] ?: currentTime
            val animElapsed = (currentTime - animStartTime) / 1000.0 // in seconds
            
            if (animElapsed < joinLeaveDuration) {
                // Join animation (scale up)
                val progress = (animElapsed / joinLeaveDuration).toFloat()
                handle.scale = Vector3(progress, progress, progress)
            } else if (!playerTargetPositions.containsKey(userId)) {
                // Leave animation (scale down)
                val progress = 1.0f - (animElapsed / joinLeaveDuration).toFloat()
                if (progress > 0) {
                    handle.scale = Vector3(progress, progress, progress)
                }
            } else {
                // Normal scale
                handle.scale = Vector3(1f, 1f, 1f)
                
                // Move towards target position
                val targetPos = playerTargetPositions[userId]
                if (targetPos != null) {
                    val currentPos = handle.position
                    val direction = targetPos.subtract(currentPos)
                    val distance = direction.length()
                    
                    // If we're close enough, snap to target position
                    if (distance < 0.1f) {
                        handle.position = targetPos
                        
                        // Update username text billboard position if it exists
                        usernameTextHandles[userId]?.let { textHandle ->
                            // Position the text billboard above the player
                            textHandle.position = Vector3(
                                targetPos.x,
                                targetPos.y + .5f,
                                targetPos.z
                            )
                        }
                    } else {
                        // Normalize direction and apply speed
                        direction.normalize()
                        direction.scaleInPlace(minOf(frameMovementSpeed, distance))
                        val newPosition = currentPos.add(direction)
                        handle.position = newPosition
                        
                        // Update username text billboard position if it exists
                        usernameTextHandles[userId]?.let { textHandle ->
                            // Position the text billboard above the player
                            textHandle.position = Vector3(
                                newPosition.x,
                                newPosition.y + .5f,
                                newPosition.z
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Configure shadow casting for all other players
     * 
     * @param shadowManager The shadow manager to use for configuring shadows
     */
    fun configureShadowsForPlayers(shadowManager: ShadowManager) {
        // Configure shadow casting for all player handles
        playerHandles.forEach { (userId, handle) ->
            handle.node.let { node ->
                node.getChildMeshes().forEach { mesh ->
                    // Add the mesh as a shadow caster
                    shadowManager.addShadowCaster(mesh)

                    // Configure the mesh as a shadow receiver
                    shadowManager.configureShadowReceiver(mesh)
                }
                
                console.log("OtherPlayersController: Configured shadows for player $userId")
            }
        }
    }
    
    /**
     * Displays a chat message above a player's head
     * 
     * @param userId The ID of the user who sent the message
     * @param message The message content
     */
    fun displayChatMessage(userId: String, message: String) {
        // Don't display messages for the current user (handled by PlayerController)
        if (userId == myUserId) {
            return
        }
        
        // Get the world object manager
        val manager = worldObjectManager ?: return
        
        // Get the player's position
        val playerPosition = playerHandles[userId]?.position ?: return
        
        // Remove existing chat message if any
        removeChatMessage(userId)
        
        // Truncate message to first 16 characters
        val displayText = if (message.length > 16) message.substring(0, 16) else message
        
        // Calculate position above the username (adjust Y value as needed)
        // Username is at y + .5f, so put chat message at y + .8f
        val textPosition = Vector3(playerPosition.x, playerPosition.y + .8f, playerPosition.z)
        
        // Create the text billboard with the chat message
        val textHandle = manager.createTextBillboard(
            text = displayText,
            position = textPosition,
            fontSize = 18,
            textColor = "black",
            backgroundColor = "white"
        )
        
        // Store the text handle
        if (textHandle != null) {
            chatMessageTextHandles[userId] = textHandle
            
            // Set a timeout to remove the message after 2 seconds
            val timeoutId = window.setTimeout({
                removeChatMessage(userId)
            }, 2000)
            
            // Store the timeout ID
            chatMessageTimeouts[userId] = timeoutId
            
            console.log("OtherPlayersController: Displayed chat message for $userId: $displayText")
        }
    }
    
    /**
     * Removes a chat message from above a player's head
     * 
     * @param userId The ID of the user whose message should be removed
     */
    private fun removeChatMessage(userId: String) {
        // Clear any existing timeout
        chatMessageTimeouts[userId]?.let { timeoutId ->
            window.clearTimeout(timeoutId)
            chatMessageTimeouts.remove(userId)
        }
        
        // Remove the text billboard if it exists
        chatMessageTextHandles[userId]?.let { textHandle ->
            textHandle.remove()
            chatMessageTextHandles.remove(userId)
            console.log("OtherPlayersController: Removed chat message for $userId")
        }
    }

    /**
     * Checks if a mesh belongs to another player
     * 
     * @param mesh The mesh to check
     * @return The userId if the mesh belongs to a player, null otherwise
     */
    fun getUserIdForMesh(mesh: dynamic): String? {
        // Check if the mesh is in the playerMeshes map
        return playerNodes.entries.find { (_, playerMesh) -> playerMesh === mesh }?.key
    }

    /**
     * Gets player data by userId
     * 
     * @param userId The user ID of the player
     * @return A Pair containing the username and isPlayer flag, or null if the player is not found
     */
    fun getPlayerData(userId: String): Pair<String, Boolean>? {
        // Get the username from the stored map
        val username = playerUsernames[userId] ?: "Player"
    
        // Get the isPlayer flag from the stored map
        val isPlayer = playerIsPlayerFlags[userId] ?: false
    
        return Pair(username, isPlayer)
    }

    /**
     * Gets all player meshes for intersection testing
     * 
     * @return Array of player meshes
     */
    fun getPlayerMeshes(): Array<AbstractMesh> {
        return playerNodes.values
            .toTypedArray()
            .map { it.getChildMeshes() }
            .toTypedArray()
            .flatten()
            .toTypedArray()
    }

    /**
     * Dispose of all resources
     */
    fun dispose() {
        // Remove all player handles
        playerHandles.values.forEach { it.remove() }
        playerHandles.clear()
        playerNodes.clear()
    
        // Remove all username text billboards
        usernameTextHandles.values.forEach { it.remove() }
        usernameTextHandles.clear()
    
        // Remove all chat message text billboards
        chatMessageTextHandles.values.forEach { it.remove() }
        chatMessageTextHandles.clear()
    
        // Clear all chat message timeouts
        chatMessageTimeouts.forEach { (_, timeoutId) -> window.clearTimeout(timeoutId) }
        chatMessageTimeouts.clear()
    
        // Remove WebSocket listeners
        WebSocketApi.removeAllListeners(WebSocketAction.RECEIVE_CHAT_MESSAGE)
    
        // Clear other tracking maps
        playerTargetPositions.clear()
        playerAnimationTimes.clear()
    
        // Clear the world object manager reference
        worldObjectManager = null
    
        // Clear the shadow manager reference
        shadowManager = null
    
        console.log("OtherPlayersController: Disposed all resources")
    }
}
