package main.components.player

import lib.AbstractMesh
import lib.Color3
import lib.CreateBoxOptions
import lib.CreateDiscOptions
import lib.Mesh
import lib.MeshBuilder
import lib.Ray
import lib.Scene
import lib.StandardMaterial
import lib.Vector3
import main.api.ApiClient
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.inventory.InventoryState
import main.components.world.CharacterDirection
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import models.ChatMessage
import models.Position
import models.WebSocketAction
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.abs
import app.softwork.routingcompose.Router
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import main.api.addTeleportPlayerListener
import main.api.addUpdatePlayerListener
import main.api.updatePlayer
import main.api.updatePlayerPosition
import main.components.world.PlayerActivity

/**
 * Controller for managing the player in the 3D scene.
 * 
 * This class encapsulates all player-related functionality including:
 * - Player creation and initialization
 * - Player movement and positioning (framerate-independent)
 * - Position validation
 * - Click-to-move functionality
 * 
 * The movement is framerate-independent, meaning the player will move at the same
 * speed regardless of the framerate. This is achieved by scaling the movement speed
 * by the time elapsed between frames (delta time).
 */
@OptIn(FlowPreview::class)
class PlayerController(private val scene: Scene) {
    
    // The player mesh (for compatibility with existing code)
    private var collider: Mesh? = null
    
    // The player character handle
    private var playerHandle: WorldObjectHandle? = null
    
    // Username text billboard handle
    private var usernameTextHandle: WorldObjectHandle? = null
    
    // Chat message text billboard handle
    private var chatMessageTextHandle: WorldObjectHandle? = null
    
    // Chat message timeout ID
    private var chatMessageTimeoutId: Int? = null
    
    // Status text billboard handle
    private var statusTextHandle: WorldObjectHandle? = null
    
    // Current player direction
    private var playerDirection: CharacterDirection = CharacterDirection.S
    
    // Current player activity (null means standing)
    private var playerActivity: PlayerActivity? = null
    
    // Time since last movement in seconds
    private var timeSinceLastMovement = 0.0
    
    // Auto-sit timeout in seconds (5 minutes)
    private val autoSitTimeout = 300.0 // 5 minutes = 300 seconds
    
    // Target position for click-to-move
    private var playerTargetPosition: Vector3? = null
    
    // Visual indicator for move target
    private var moveCircle: Mesh? = null
    
    // World meshes for collision detection
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    // Movement state
    private var isMoving = false
    
    // Base movement speed in units per second for framerate-independent movement
    private val baseMovementSpeed = 3.0f
    
    // Time tracking for framerate-independent movement
    private var lastFrameTime = 0.0
    
    // Player position flow for sending position updates
    private val playerPositionFlow = MutableStateFlow<Vector3?>(null)
    
    // Coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    
    init {
        // Collect position updates with 500ms debounce
        playerPositionFlow
            .filterNotNull()
            .debounce(500)
            .onEach { validPos ->
                try {
                    // Check connection state using WebSocketConnectionManager
                    if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                        val success = WebSocketApi.updatePlayerPosition(
                            position = Position(
                                x = validPos.x,
                                y = validPos.y,
                                z = validPos.z
                            ),
                            isPlayer = UserApi.getCurrentUser().isPlayer
                        )
                        if (!success) {
                            console.warn("Failed to update player position: ${WebSocketApi.getLastError() ?: "Unknown error"}")
                        }
                    } else {
                        // Don't spam the console with warnings - only log occasionally
                        if (window.performance.now() % 3000 < 16) { // Log roughly once every 3 seconds
                            console.warn("Cannot update player position: WebSocket not connected (state: ${WebSocketConnectionManager.connectionState.value})")
                        }
                    }
                } catch (e: Exception) {
                    console.error("Error updating player position", e)
                }
            }
            .launchIn(coroutineScope)
    }
    
    // Reference to the world object manager for creating text billboards
    private var worldObjectManager: WorldObjectManager? = null
    
    // Callback for inventory refresh
    private var inventoryRefreshCallback: (() -> Unit)? = null
    
    // Properties for detecting minimal movement
    private var lastSignificantPosition: Vector3? = null
    private var timeWithMinimalMovement = 0.0
    private val minimalMovementThreshold = 0.01f // Distance threshold to consider movement as minimal
    private val giveUpTimeThreshold = 1.0 // Time in seconds to give up if movement is minimal
    
    /**
     * Creates the player character or mesh.
     * 
     * @param startPosition The starting position for the player
     * @param worldObjectManager Optional WorldObjectManager to create a character instead of a box
     */
    fun createPlayer(startPosition: Vector3, worldObjectManager: WorldObjectManager) {
        // Store the WorldObjectManager for later use
        this.worldObjectManager = worldObjectManager

        // Create a sprite sheet for the player based on the user's sex and body type
        val spriteSheet = SpriteSheet.createPlayerSpriteSheetForBodyType(
            InventoryState.currentUser?.sex,
            InventoryState.currentUser?.bodyType,
        )
        
        // Get worn item sprite sheets if inventory is already loaded
        var wornItemSpriteSheets = emptyList<SpriteSheet>()
        if (InventoryState.currentUser != null && InventoryState.items.isNotEmpty()) {
            wornItemSpriteSheets = WornItemsRenderer.getWornItemSpriteSheetsForUser(InventoryState.currentUser)
            console.log("[DEBUG_LOG] PlayerController: Found ${wornItemSpriteSheets.size} worn items during initialization")
        } else {
            console.log("[DEBUG_LOG] PlayerController: Inventory not loaded yet, will update worn items later")
            
            // Load inventory data if not already loaded
            coroutineScope.launch {
                InventoryState.loadInventory(
                    coroutineScope = coroutineScope,
                    onSuccess = {
                        console.log("[DEBUG_LOG] PlayerController: Inventory loaded, refreshing worn items")
                        refreshWornItems()
                    }
                )
            }
        }

        // Create the player character using the WorldObjectManager
        // Pass empty list as additionalSpriteSheets and let WorldObjectManager handle worn items
        playerHandle = worldObjectManager.createCharacter(
            spriteSheet = spriteSheet,
            direction = playerDirection,
            position = startPosition,
            additionalSpriteSheets = emptyList(),
            user = InventoryState.currentUser
        )
        
        // Register a callback to update worn items when inventory changes
        inventoryRefreshCallback = {
            console.log("[DEBUG_LOG] PlayerController: Inventory changed, refreshing worn items")
            refreshWornItems()
        }
        InventoryState.addOnRefreshCallback(inventoryRefreshCallback!!)

        // Create a simple box for collision detection
        // This is needed because we can't directly access the character's mesh
        collider = MeshBuilder.CreateBox("player_collision",
            object : CreateBoxOptions {
                override val width = 0.05f
                override val height = 1.0f
                override val depth = 0.25f
            }, scene)

        // Make the collision box invisible
        collider?.isVisible = false

        // Position the collision box
        collider?.position = startPosition

        // Add username text above the player
        // Start with a default username
        val defaultUsername = "Player"

        // Calculate position above the player (adjust Y value as needed)
        val textPosition = Vector3(startPosition.x, startPosition.y + .5f, startPosition.z)

        // Create the text billboard with default text
        usernameTextHandle = worldObjectManager.createTextBillboard(
            text = defaultUsername,
            position = textPosition,
            fontSize = 18,
            textColor = "white"
        )

        // Update the username asynchronously
        coroutineScope.launch {
            try {
                // Get the current user's username
                val user = UserApi.getCurrentUser()

                // Create a new text billboard with the actual username
                // First remove the old one
                usernameTextHandle?.remove()

                // Create a new one with the correct username
                usernameTextHandle = worldObjectManager.createTextBillboard(
                    text = user.username,
                    position = textPosition,
                    fontSize = 18,
                    textColor = "white"
                )
            } catch (e: Exception) {
                console.error("Error getting username", e)
            }
        }
    }
    
    /**
     * Sets the world meshes for collision detection.
     * 
     * @param meshes The array of world meshes
     */
    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        worldMeshes = meshes
    }
    
    /**
     * Gets the world meshes.
     * 
     * @return The array of world meshes
     */
    fun getWorldMeshes(): Array<AbstractMesh> {
        return worldMeshes
    }
    
    /**
     * Gets the player mesh.
     * 
     * @return The player mesh
     */
    fun getCollider(): Mesh? {
        return collider
    }

    fun getPlayerHandle(): WorldObjectHandle? {
        return playerHandle
    }
    
    /**
     * Gets the current player activity.
     * 
     * @return The current activity, or null if standing
     */
    fun getPlayerActivity(): PlayerActivity? {
        return playerActivity
    }
    
    /**
     * Sets the player activity.
     * 
     * @param activity The new activity, or null to stand
     */
    fun setPlayerActivity(activity: PlayerActivity?) {
        console.log("[DEBUG_LOG] PlayerController: setPlayerActivity called with ${activity?.name ?: "null (Standing)"}")
        
        // Only update if the activity has changed
        if (activity != playerActivity) {
            console.log("[DEBUG_LOG] PlayerController: Activity changing from ${playerActivity?.name ?: "Standing"} to ${activity?.name ?: "Standing"}")
            playerActivity = activity
            
            // Update the character's appearance
            playerHandle?.let { handle ->
                console.log("[DEBUG_LOG] PlayerController: Updating character activity to ${activity?.name ?: "Standing"}")
                // Update the activity on the character
                handle.updateActivity(activity)
            } ?: console.log("[DEBUG_LOG] PlayerController: Warning - playerHandle is null, cannot update character activity")
            
            // Send activity update to other players via WebSocket
            coroutineScope.launch {
                try {
                    // Check connection state using WebSocketConnectionManager
                    if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                        val user = UserApi.getCurrentUser()
                        val success = WebSocketApi.updatePlayer(
                            name = user.username,
                            isPlayer = user.isPlayer,
                            activity = activity
                        )
                        if (!success) {
                            console.warn("Failed to send player activity update: ${WebSocketApi.getLastError() ?: "Unknown error"}")
                        } else {
                            console.log("[DEBUG_LOG] PlayerController: Activity update sent to other players: ${activity?.name ?: "Standing"}")
                        }
                    } else {
                        // Don't spam the console with warnings - only log occasionally
                        if (window.performance.now() % 3000 < 16) { // Log roughly once every 3 seconds
                            console.warn("Cannot update player activity: WebSocket not connected (state: ${WebSocketConnectionManager.connectionState.value})")
                        }
                    }
                } catch (e: Exception) {
                    console.error("Error sending player activity update", e)
                }
            }
            
            console.log("PlayerController: Activity changed to ${activity?.name ?: "Standing"}")
        } else {
            console.log("[DEBUG_LOG] PlayerController: Activity unchanged (already ${activity?.name ?: "Standing"})")
        }
    }
    
    /**
     * Toggles between sitting and standing.
     */
    fun toggleSitting() {
        console.log("[DEBUG_LOG] PlayerController: toggleSitting called, current activity: ${playerActivity?.name ?: "Standing"}")
        
        if (playerActivity == PlayerActivity.Sit) {
            // Currently sitting, so stand up
            console.log("[DEBUG_LOG] PlayerController: Standing up")
            setPlayerActivity(null)
        } else {
            // Currently standing, so sit down
            console.log("[DEBUG_LOG] PlayerController: Sitting down")
            setPlayerActivity(PlayerActivity.Sit)
        }
    }
    
    /**
     * Gets the move circle mesh.
     * 
     * @return The move circle mesh
     */
    fun getMoveCircle(): Mesh? {
        return moveCircle
    }
    
    /**
     * Gets the player's current position.
     * 
     * @return The player's position, or null if player doesn't exist
     */
    fun getPlayerPosition(): Vector3? {
        return playerHandle?.position
    }
    
    /**
     * Sets a target position for click-to-move.
     * 
     * @param position The target position
     */
    fun setTargetPosition(position: Vector3) {
        // Set the target position slightly above the ground
        playerTargetPosition = Vector3(position.x, position.y + 0.5f, position.z)
        isMoving = true
        
        // Update player activity to Moving
        setPlayerActivity(PlayerActivity.Moving)
        
        // Create move circle at target position
        createMoveCircle(position)
    }
    
    /**
     * Stops any current movement and resets movement tracking.
     */
    fun stopMovement() {
        playerTargetPosition = null
        isMoving = false
        lastSignificantPosition = null
        timeWithMinimalMovement = 0.0
        
        // Reset player activity to standing (null) if it was Moving
        if (playerActivity == PlayerActivity.Moving) {
            setPlayerActivity(null)
        }
    }
    
    /**
     * Creates a move circle at the target position.
     * 
     * @param position The position for the move circle
     */
    private fun createMoveCircle(position: Vector3) {
        // Remove existing move circle if any
        moveCircle?.dispose()
        
        // Create a new circle
        moveCircle = MeshBuilder.CreateDisc("moveCircle", 
            object : CreateDiscOptions {
                override val radius = 0.5f
                override val tessellation = 32
            }, scene)
        
        // Position the circle slightly above the ground to avoid z-fighting
        moveCircle?.position = Vector3(position.x, position.y + 0.01f, position.z)
        
        // Rotate the circle to be flat on the ground
        moveCircle?.rotation = Vector3(PI.toFloat() / 2f, 0f, 0f)
        
        // Create a material for the circle
        val material = StandardMaterial("moveCircleMaterial", scene)
        material.diffuseColor = Color3(0f, 0.8f, 1f)
        material.alpha = 0.7f
        material.backFaceCulling = false
        moveCircle?.material = material
        
        // Set up animation to scale down the circle
        var scaleValue = 1.0f
        var lastCircleAnimTime = 0.0
        
        // Animation speed in units per second (framerate-independent)
        val circleAnimSpeed = 1.2f // Complete animation in ~0.83 seconds

        // todo refactor
        scene.registerBeforeRender {
            if (moveCircle != null && scaleValue > 0) {
                // Calculate delta time for framerate-independent animation
                val currentTime = window.performance.now()
                val deltaTime = if (lastCircleAnimTime > 0) {
                    // Convert to seconds and cap at a reasonable value to prevent huge jumps after pauses
                    minOf((currentTime - lastCircleAnimTime) / 1000.0, 0.1)
                } else {
                    // First frame, use a small default value
                    0.016 // ~60fps
                }
                lastCircleAnimTime = currentTime
                
                // Scale the decrement by delta time for consistent animation speed
                scaleValue -= (circleAnimSpeed * deltaTime).toFloat()
                
                if (scaleValue <= 0) {
                    moveCircle?.dispose()
                    moveCircle = null
                } else {
                    moveCircle?.scaling = Vector3(scaleValue, scaleValue, scaleValue)
                    // Also fade out
                    (moveCircle?.material as? StandardMaterial)?.alpha = scaleValue * 0.7f
                }
            }
        }
    }
    
    /**
     * Checks if a position is valid (has ground below).
     * 
     * @param position The position to check
     * @return True if the position is valid, false otherwise
     */
    fun isValidPosition(position: Vector3): Boolean {
        // Create a ray from slightly above the position pointing down
        val rayOrigin = Vector3(position.x, position.y + 1f, position.z)
        val rayDirection = Vector3(0f, -1f, 0f)
        val ray = Ray()
        ray.origin = rayOrigin
        ray.direction = rayDirection
        
        // Check if the ray intersects with any world mesh
        for (mesh in worldMeshes) {
            // Skip player and move circle meshes
            if (mesh == collider || mesh == moveCircle) continue
            
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Finds a valid Y position at the given X,Z coordinates.
     * 
     * @param position The position with X,Z coordinates
     * @return The updated position with a valid Y coordinate, or null if no valid position found
     */
    fun findValidYPosition(position: Vector3): Vector3? {
        // Create a ray from high above the position pointing down
        val ray = Ray()
        ray.origin = Vector3(position.x, 10f, position.z)
        ray.direction = Vector3(0f, -1f, 0f)
        
        for (mesh in worldMeshes) {
            if (mesh == collider || mesh == moveCircle) continue
            
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                // Return position with updated Y coordinate
                return Vector3(position.x, hit.pickedPoint.y + 0.5f, position.z)
            }
        }
        
        return null
    }
    
    /**
     * Updates the player's position based on movement direction or target position.
     * Uses delta time to ensure consistent movement speed regardless of framerate.
     * Also updates the player's direction based on movement.
     * 
     * @param moveDirection The direction to move in (from keyboard input)
     * @return The new position if movement occurred, null otherwise
     */
    fun updatePlayerPosition(moveDirection: Vector3): Vector3? {
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
        
        var finalMoveDirection = Vector3.Zero()
        var isPlayerMoving = false
        
        // Handle keyboard movement
        if (moveDirection.length() > 0f) {
            finalMoveDirection = moveDirection.scale(frameMovementSpeed)
            
            // Update player direction based on movement
            updatePlayerDirection(moveDirection)
            
            // Cancel click-to-move if keyboard movement is active
            stopMovement()
            
            // Player is moving
            isPlayerMoving = true
            
            // If player is sitting, make them stand up when they move
            if (playerActivity == PlayerActivity.Sit) {
                setPlayerActivity(null)
            }
            
            // Reset time since last movement
            timeSinceLastMovement = 0.0
        }
        // Handle click-to-move
        else if (isMoving && playerTargetPosition != null) {
            val currentPos = playerHandle!!.position
            val targetPos = playerTargetPosition!!
            
            // Calculate direction and distance
            val direction = targetPos.subtract(currentPos)
            val distance = direction.length()
            
            // If we're close enough, stop moving
            if (distance < 0.1f) {
                stopMovement()
            } else {
                // Normalize direction and apply speed
                direction.normalize()
                direction.scaleInPlace(min(frameMovementSpeed, distance))
                finalMoveDirection = direction
                
                // Update player direction based on movement
                updatePlayerDirection(direction)
                
                // Player is moving
                isPlayerMoving = true
                
                // If player is sitting, make them stand up when they move
                if (playerActivity == PlayerActivity.Sit) {
                    setPlayerActivity(null)
                }
                
                // Reset time since last movement
                timeSinceLastMovement = 0.0
            }
        }
        
        // If player is not moving, increment time since last movement
        if (!isPlayerMoving) {
            timeSinceLastMovement += deltaTime
            
            // If player has been inactive for 5 minutes and is not sitting, make them sit
            if (timeSinceLastMovement >= autoSitTimeout && playerActivity != PlayerActivity.Sit) {
                console.log("PlayerController: Auto-sitting after ${autoSitTimeout} seconds of inactivity")
                setPlayerActivity(PlayerActivity.Sit)
            }
            
            // If player was in Moving activity, reset to standing
            if (playerActivity == PlayerActivity.Moving) {
                setPlayerActivity(null)
            }
        } else {
            // Player is moving, set activity to Moving if not already
            if (playerActivity != PlayerActivity.Moving) {
                setPlayerActivity(PlayerActivity.Moving)
            }
        }

        console.log("[PLAYER_MOVE] finalMoveDirection=$finalMoveDirection")
        
        // Apply movement if there is any
        if (finalMoveDirection.length() > 0f) {
            // Calculate new position
            val currentPos = playerHandle!!.position
            val newPos = currentPos.add(finalMoveDirection)
            
            // Check if the new position is valid
            if (isValidPosition(newPos)) {
                // Find the exact y position by raycasting
                val validPos = findValidYPosition(newPos)
                
                if (validPos == null) {
                    console.log("[PLAYER_MOVE] validPos is null")
                } else {
                    // Check for minimal movement if we're using click-to-move
                    if (isMoving && playerTargetPosition != null) {
                        console.log("[PLAYER_MOVE] isMoving = true && playerTargetPosition != null")
                        // Initialize lastSignificantPosition if it's null
                        if (lastSignificantPosition == null) {
                            lastSignificantPosition = currentPos
                        }
                        
                        // Calculate movement distance since last significant position
                        val movementX = abs(validPos.x - lastSignificantPosition!!.x)
                        val movementZ = abs(validPos.z - lastSignificantPosition!!.z)
                        val significantMovement =
                            movementX > minimalMovementThreshold || movementZ > minimalMovementThreshold
                        
                        if (significantMovement) {
                            console.log("[PLAYER_MOVE] significant")
                            // Reset tracking if there was significant movement
                            lastSignificantPosition = validPos
                            timeWithMinimalMovement = 0.0
                        } else {
                            // Accumulate time with minimal movement
                            timeWithMinimalMovement += deltaTime
                            
                            // Give up if we've been moving minimally for too long
                            if (timeWithMinimalMovement >= giveUpTimeThreshold) {
                                console.log("[PLAYER_MOVE] Giving up movement due to minimal progress")
                                stopMovement()
                            }
                        }
                    }

                    console.log("[PLAYER_MOVE] validPos = $validPos")
                    
                    // Update player position
                    collider!!.position = validPos

                    // Set the position directly on the node
                    playerHandle!!.position = validPos

                    // Update username text position to be above the player
                    usernameTextHandle?.position = Vector3(
                        validPos.x,
                        validPos.y + .5f,
                        validPos.z
                    )
                    
                    // Update status text position to be above the player
                    statusTextHandle?.position = Vector3(
                        validPos.x,
                        validPos.y + .8f,
                        validPos.z
                    )
                    
                    // Update player position flow to trigger position updates
                    playerPositionFlow.value = validPos
                    
                    return validPos
                }
            } else if (isMoving) {
                console.log("[PLAYER_MOVE] stopMovement()")
                // If the position is invalid and we're using click-to-move, stop moving
                stopMovement()
            }
        }
        
        return null
    }

    /**
     * Finds a valid starting position for the player.
     * 
     * @return A valid starting position, or Vector3.Zero() if none found
     */
    fun findValidStartPosition(): Vector3 {
        var startPosition = Vector3.Zero()
        
        // Try positions in a grid around the origin
        for (x in -5..5) {
            for (z in -5..5) {
                val testPosition = Vector3(x.toFloat(), 0f, z.toFloat())
                if (isValidPosition(testPosition)) {
                    // Find the exact y position by raycasting
                    val validPos = findValidYPosition(testPosition)
                    if (validPos != null) {
                        startPosition = validPos
                        return startPosition
                    }
                }
            }
        }
        
        return startPosition
    }
    
    /**
     * Updates the player's direction based on movement.
     * Maps a Vector3 movement direction to a CharacterDirection enum value.
     * 
     * @param moveDirection The direction vector to map
     */
    private fun updatePlayerDirection(moveDirection: Vector3) {
        // Normalize the direction vector to get a unit vector
        val normalizedDirection = moveDirection.clone()
        if (normalizedDirection.length() > 0) {
            normalizedDirection.normalize()
        }
        
        // Determine direction using shared utility
        val newDirection = main.components.world.calculateFacingFromVector(normalizedDirection, playerDirection)
        
        // Only update if the direction has changed
        if (newDirection != playerDirection) {
            playerDirection = newDirection
            
            // Update the character's direction if we have a player handle
            playerHandle?.let { handle ->
                // Use the new updateDirection method to update the character's direction
                handle.updateDirection(playerDirection)
            }
        }
    }
    
    /**
     * Displays a chat message above the player's head
     * 
     * @param message The message content
     */
    fun displayChatMessage(message: String) {
        // Get the player's position
        val playerPosition = collider?.position ?: return
        
        // Remove existing chat message if any
        removeChatMessage()
        
        // Remove status text if any (will be restored when chat message is removed)
        removePlayerStatus()
        
        // Truncate message to first 16 characters
        val displayText = if (message.length > 16) message.substring(0, 16) else message
        
        // Calculate position above the username (adjust Y value as needed)
        // Username is at y + .5f, so put chat message at y + .8f
        val textPosition = Vector3(playerPosition.x, playerPosition.y + .8f, playerPosition.z)
        
        // Use the stored WorldObjectManager
        val manager = worldObjectManager ?: return
        
        // Create the text billboard with the chat message
        chatMessageTextHandle = manager.createTextBillboard(
            text = displayText,
            position = textPosition,
            fontSize = 18,
            textColor = "black",
            backgroundColor = "white"
        )
        
        // Set a timeout to remove the message after 2 seconds
        chatMessageTimeoutId = window.setTimeout({
            removeChatMessage()
        }, 2000)
        
        console.log("PlayerController: Displayed chat message: $displayText")
    }
    
    /**
     * Removes a chat message from above the player's head
     */
    private fun removeChatMessage() {
        // Clear any existing timeout
        chatMessageTimeoutId?.let { timeoutId ->
            window.clearTimeout(timeoutId)
            chatMessageTimeoutId = null
        }
        
        // Remove the text billboard if it exists
        chatMessageTextHandle?.let { textHandle ->
            textHandle.remove()
            chatMessageTextHandle = null
            console.log("PlayerController: Removed chat message")
            
            // Show status text again if available
            coroutineScope.launch {
                try {
                    val user = UserApi.getCurrentUser()
                    if (user.status.isNotBlank()) {
                        displayPlayerStatus(user.status)
                    }
                } catch (e: Exception) {
                    console.error("PlayerController: Error getting current user status", e)
                }
            }
        }
    }

    /**
     * Initializes the chat message listener
     */
    fun initializeChatMessageListener() {
        // Add listener for chat messages
        WebSocketApi.addListener(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                // Check if the message is valid
                if (message.isValid()) {
                    val chatMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    console.log("PlayerController: Received RECEIVE_CHAT_MESSAGE event: ${chatMessage.userId}, ${chatMessage.message}")
                    
                    // Only display messages from the current user
                    coroutineScope.launch {
                        try {
                            val user = UserApi.getCurrentUser()
                            if (chatMessage.userId == user.key) {
                                // Display the chat message above the player's head
                                displayChatMessage(chatMessage.message)
                            }
                        } catch (e: Exception) {
                            console.error("PlayerController: Error getting current user", e)
                        }
                    }
                } else {
                    console.error("PlayerController: Invalid chat message received: ${message.action}, ${message.data}")
                }
            } catch (e: Exception) {
                console.error("PlayerController: Error parsing chat message", e)
            }
        }
        
        console.log("PlayerController: Chat message listener initialized")
    }
    
    /**
     * Initializes the player status display
     * 
     * This method fetches the current user's status and displays it if not blank.
     * It also sets up a listener for status updates from the WebSocket.
     */
    fun initializePlayerStatus() {
        console.log("[DEBUG_LOG] PlayerController: initializePlayerStatus called")
        console.log("[DEBUG_LOG] PlayerController: Initializing player status display")
        
        // Check if WorldObjectManager is set
        if (worldObjectManager == null) {
            console.error("[DEBUG_LOG] PlayerController: WorldObjectManager is null, cannot initialize status")
            return
        }
        
        // Check if player position is available
        if (collider?.position == null) {
            console.error("[DEBUG_LOG] PlayerController: Player position is null, cannot initialize status")
            return
        }
        
        // Set up a listener for player status updates from WebSocket
        // This is crucial for the player to see their own status updates
        console.log("[DEBUG_LOG] PlayerController: Setting up WebSocket listener for player status updates")
        WebSocketApi.addUpdatePlayerListener { updateData ->
            coroutineScope.launch {
                try {
                    // Get current user to check if this update is for the current player
                    val currentUser = UserApi.getCurrentUser()
                    
                    // Only process updates for the current user (opposite of OtherPlayersController)
                    if (updateData.userId == currentUser.key) {
                        console.log("[DEBUG_LOG] PlayerController: Received status update for current user: '${updateData.status ?: ""}'")
                        
                        // Display status if provided and not blank
                        val status = updateData.status?.orEmpty() ?: ""
                        if (status.isNotBlank()) {
                            console.log("[DEBUG_LOG] PlayerController: Updating status display to: '$status'")
                            displayPlayerStatus(status)
                        } else {
                            // Remove status if it was previously displayed
                            console.log("[DEBUG_LOG] PlayerController: Removing status display (blank status)")
                            removePlayerStatus()
                        }
                    }
                } catch (e: Exception) {
                    console.error("[DEBUG_LOG] PlayerController: Error processing status update", e)
                }
            }
        }
        
        // Display initial status if available
        coroutineScope.launch {
            try {
                console.log("[DEBUG_LOG] PlayerController: Fetching current user data")
                val user = UserApi.getCurrentUser()
                console.log("[DEBUG_LOG] PlayerController: Current user: ${user.username}, status: '${user.status}'")
                
                if (user.status.isNotBlank()) {
                    console.log("[DEBUG_LOG] PlayerController: Displaying initial status: '${user.status}'")
                    displayPlayerStatus(user.status)
                } else {
                    console.log("[DEBUG_LOG] PlayerController: No initial status to display (status is blank)")
                }
            } catch (e: Exception) {
                console.error("[DEBUG_LOG] PlayerController: Error getting current user status", e)
            }
        }
    }

    /**
     * Initializes the teleport listener
     * 
     * This method sets up a listener for teleport events that will navigate to the specified world URL.
     */
    fun initializeTeleportListener(router: Router) {
        console.log("PlayerController: Initializing teleport listener")
        
        // Add listener for teleport events
        // The WebSocketApi.addTeleportPlayerListener already handles navigation to the world URL
        WebSocketApi.addTeleportPlayerListener(router) { teleportData ->
            console.log("PlayerController: Received teleport event with data: $teleportData")
        }
        
        console.log("PlayerController: Teleport listener initialized")
    }

    /**
     * Updates the player's username text
     * 
     * @param username The new username to display
     */
    fun updatePlayerName(username: String) {
        // Get the player's position
        val playerPosition = collider?.position ?: return
        
        // Use the stored WorldObjectManager
        val manager = worldObjectManager ?: return
        
        // Remove existing username text if any
        usernameTextHandle?.remove()
        
        // Calculate position above the player (adjust Y value as needed)
        val textPosition = Vector3(playerPosition.x, playerPosition.y + .5f, playerPosition.z)
        
        // Create a new text billboard with the updated username
        usernameTextHandle = manager.createTextBillboard(
            text = username,
            position = textPosition,
            fontSize = 18,
            textColor = "white"
        )
        
        console.log("PlayerController: Updated username text to $username")
    }
    
    /**
     * Displays the player's status above their head
     * 
     * @param status The status text to display
     */
    fun displayPlayerStatus(status: String) {
        console.log("PlayerController: displayPlayerStatus called with status: '$status'")
        
        // Don't display empty status
        if (status.isBlank()) {
            console.log("PlayerController: Status is blank, removing any existing status")
            removePlayerStatus()
            return
        }
        
        // If chat message is showing, don't show status
        if (chatMessageTextHandle != null) {
            console.log("PlayerController: Chat message is showing, not displaying status")
            return
        }
        
        // Get the player's position
        val playerPosition = collider?.position
        if (playerPosition == null) {
            console.error("PlayerController: Player position is null, cannot display status")
            return
        }
        
        // Use the stored WorldObjectManager
        val manager = worldObjectManager
        if (manager == null) {
            console.error("PlayerController: WorldObjectManager is null, cannot display status")
            return
        }
        
        // Remove existing status text if any
        removePlayerStatus()
        
        // Calculate position above the username (adjust Y value as needed)
        // Username is at y + .5f, so put status message at y + .8f (same as chat)
        val textPosition = Vector3(playerPosition.x, playerPosition.y + .8f, playerPosition.z)
        console.log("PlayerController: Creating status text at position (${textPosition.x}, ${textPosition.y}, ${textPosition.z})")
        
        // Create the text billboard with the status message
        statusTextHandle = manager.createTextBillboard(
            text = status,
            position = textPosition,
            fontSize = 18,
            textColor = "white",
            backgroundColor = "#00000080" // Semi-transparent black background
        )
        
        if (statusTextHandle == null) {
            console.error("PlayerController: Failed to create status text billboard")
        } else {
            console.log("PlayerController: Successfully displayed player status: '$status'")
        }
    }
    
    /**
     * Removes the player's status text
     */
    private fun removePlayerStatus() {
        console.log("PlayerController: removePlayerStatus called")
        
        // Remove the text billboard if it exists
        if (statusTextHandle == null) {
            console.log("PlayerController: No status text to remove")
            return
        }
        
        statusTextHandle?.let { textHandle ->
            textHandle.remove()
            statusTextHandle = null
            console.log("PlayerController: Successfully removed player status")
        }
    }

    /**
     * Refreshes the player's worn items based on the current inventory state.
     * This method is called when the inventory changes.
     */
    fun refreshWornItems() {
        console.log("[DEBUG_LOG] PlayerController: refreshWornItems called")
        
        // Get the world object manager
        val manager = worldObjectManager ?: return
        
        // Get the player handle
        val handle = playerHandle ?: return
        
        // Get worn item sprite sheets from WornItemsRenderer
        val wornItemSpriteSheets = WornItemsRenderer.getWornItemSpriteSheetsForUser(InventoryState.currentUser)
        
        // Check if we have any worn items
        if (wornItemSpriteSheets.isEmpty()) {
            console.log("[DEBUG_LOG] PlayerController: No worn items to display")
            
            // Check if user has worn items configured
            val wornItems = InventoryState.currentUser?.wornItems ?: emptyList()
            if (wornItems.isNotEmpty()) {
                console.log("[DEBUG_LOG] PlayerController: User has ${wornItems.size} worn items configured, but no sprite sheets were created")
                wornItems.forEach { itemId ->
                    val item = InventoryState.items[itemId]
                    console.log("[DEBUG_LOG] PlayerController: Worn item: $itemId, item: ${item?.title}, wearable: ${item?.wearable}, wearableFile: ${item?.wearableFile}")
                }
            } else {
                console.log("[DEBUG_LOG] PlayerController: User has no worn items configured")
            }
        } else {
            console.log("[DEBUG_LOG] PlayerController: Found ${wornItemSpriteSheets.size} worn items to display")
            wornItemSpriteSheets.forEach { spriteSheet ->
                console.log("[DEBUG_LOG] PlayerController: Sprite sheet: ${spriteSheet.imageUrl}")
            }
        }
        
        // Get the Character object from the handle
        // Since we can't directly access the Character, we need to recreate it
        // Get the current position, direction, and activity
        val currentPosition = handle.position
        val currentDirection = playerDirection
        val currentActivity = playerActivity
        
        // Remove the existing character
        handle.remove()
        
        // Create a new character with the worn items, using the appropriate sprite sheet based on user's sex and body type
        val spriteSheet = SpriteSheet.createPlayerSpriteSheetForBodyType(
            InventoryState.currentUser?.sex,
            InventoryState.currentUser?.bodyType,
        )
        playerHandle = manager.createCharacter(
            spriteSheet = spriteSheet,
            direction = currentDirection,
            position = currentPosition,
            activity = currentActivity,
            additionalSpriteSheets = emptyList(),
            user = InventoryState.currentUser
        )
        
        console.log("[DEBUG_LOG] PlayerController: Player character updated with worn items")
    }
    
    /**
     * Disposes of the player controller resources.
     */
    fun dispose() {
        collider?.dispose()
        moveCircle?.dispose()
        playerHandle?.remove()
        usernameTextHandle?.remove()
        
        // Remove chat message and clear timeout
        removeChatMessage()
        
        // Remove the inventory refresh callback
        inventoryRefreshCallback?.let { callback ->
            InventoryState.removeOnRefreshCallback(callback)
            inventoryRefreshCallback = null
        }
        
        // Remove WebSocket listeners
        WebSocketApi.removeAllListeners(WebSocketAction.RECEIVE_CHAT_MESSAGE)
    }
}
