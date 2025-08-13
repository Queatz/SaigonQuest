package main.components.player

import app.softwork.routingcompose.Router
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Mesh
import lib.Scene
import lib.Vector3
import main.api.ListenerToken
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.api.updatePlayer
import main.api.updatePlayerPosition
import main.components.inventory.InventoryState
import main.components.world.CharacterDirection
import main.components.world.PlayerActivity
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import models.Position

// Controller for the local player: creation, movement, HUD, and sync.
@OptIn(FlowPreview::class)
class PlayerController(private val scene: Scene) {
    
    // The player mesh (for compatibility with existing code)
    private var collider: Mesh? = null
    
    // The player character handle
    private var playerHandle: WorldObjectHandle? = null
    
    // HUD manager for local player
    private val hud = SelfHudManager()
    
    // Current player direction
    private var playerDirection: CharacterDirection = CharacterDirection.S
    
    // Current player activity (null means standing)
    private var playerActivity: PlayerActivity? = null
    
    // Time since last movement in seconds
    private var timeSinceLastMovement = 0.0
    
    // Auto-sit timeout in seconds (5 minutes)
    private val autoSitTimeout = 300.0 // 5 minutes = 300 seconds
    
    // Visual indicator for move target
    private var moveCircle: Mesh? = null
    
    // World meshes for collision detection
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    // Time tracking for framerate-independent movement
    private val movementDelta = FrameDeltaTracker()

    // Movement engine encapsulating movement state
    private val mover = PlayerMovementEngine(
        onMoveDirection = { dir -> updatePlayerDirection(dir) },
        getActivity = { getPlayerActivity() },
        setActivity = { new -> setPlayerActivity(new) },
    )
    
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
                } catch (e: Throwable) {
                    console.error("Error updating player position", e)
                }
            }
            .launchIn(coroutineScope)
    }
    
    // Reference to the world object manager for creating text billboards
    private var worldObjectManager: WorldObjectManager? = null
    
    // Callback for inventory refresh
    private var inventoryRefreshCallback: (() -> Unit)? = null
    
    // Networking listeners abstraction
    private val netSync = PlayerNetworkSync(coroutineScope)
    
    /**
     * Creates the player character or mesh.
     * 
     * @param startPosition The starting position for the player
     * @param worldObjectManager Optional WorldObjectManager to create a character instead of a box
     */
    fun createPlayer(startPosition: Vector3, worldObjectManager: WorldObjectManager) {
        // Store manager and wire HUD restore on chat clear
        this.worldObjectManager = worldObjectManager
        hud.setManager(worldObjectManager)
        hud.setOnChatCleared {
            coroutineScope.launch {
                try {
                    val user = UserApi.getCurrentUser()
                    if (user.status.isNotBlank()) collider?.position?.let { hud.showStatus(it, user.status) }
                } catch (t: Throwable) { console.error("PlayerController: restore status after chat failed", t) }
            }
        }

        // Delegate creation to PlayerInitializer
        PlayerInitializer.create(
            scene = scene,
            startPosition = startPosition,
            worldObjectManager = worldObjectManager,
            playerDirection = playerDirection,
            hud = hud,
            coroutineScope = coroutineScope,
            onRefreshWornItems = { refreshWornItems() },
            onSetCollider = { collider = it },
            onSetPlayerHandle = { playerHandle = it },
            onRegisterInventoryCallback = { cb -> inventoryRefreshCallback = cb }
        )
    }
    
    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        worldMeshes = meshes
    }
    
    fun getWorldMeshes(): Array<AbstractMesh> {
        return worldMeshes
    }
    
    fun getCollider(): Mesh? {
        return collider
    }

    fun getPlayerHandle(): WorldObjectHandle? {
        return playerHandle
    }
    
    fun getPlayerActivity(): PlayerActivity? {
        return playerActivity
    }
    
    fun setPlayerActivity(activity: PlayerActivity?) {
        if (activity == playerActivity) return
        playerActivity = activity
        playerHandle?.updateActivity(activity)
        coroutineScope.launch {
            try {
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                    val user = UserApi.getCurrentUser()
                    WebSocketApi.updatePlayer(name = user.username, isPlayer = user.isPlayer, activity = activity)
                }
            } catch (e: Throwable) {
                console.error("Error sending player activity update", e)
            }
        }
    }
    
    /**
     * Toggles between sitting and standing.
     */
    fun toggleSitting() {
        if (playerActivity == PlayerActivity.Sit) setPlayerActivity(null)
        else setPlayerActivity(PlayerActivity.Sit)
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
        mover.setTargetPosition(position)
        moveCircle?.dispose()
        moveCircle = MoveCircleEffect.create(scene, position)
    }
    
    /**
     * Stops any current movement and resets movement tracking.
     */
    fun stopMovement() { mover.stopMovement() }
    
    /**
     * Creates a move circle at the target position.
     * 
     * @param position The position for the move circle
     */
    private fun createMoveCircle(position: Vector3) { /* replaced by MoveCircleEffect */ }
    
    /**
     * Checks if a position is valid (has ground below).
     * 
     * @param position The position to check
     * @return True if the position is valid, false otherwise
     */
    fun isValidPosition(position: Vector3): Boolean =
        WorldCollisionUtils.isValidPosition(worldMeshes, collider, moveCircle, position)
    
    /**
     * Finds a valid Y position at the given X,Z coordinates.
     * 
     * @param position The position with X,Z coordinates
     * @return The updated position with a valid Y coordinate, or null if no valid position found
     */
    fun findValidYPosition(position: Vector3): Vector3? =
        WorldCollisionUtils.findValidYPosition(worldMeshes, collider, moveCircle, position, yPadding = 0.5f)
    
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
        val deltaTime = movementDelta.nextDeltaSeconds()
        val frameMovementSpeed = (3.0f * deltaTime).toFloat()
        
        val handle = playerHandle ?: return null
        val coll = collider ?: return null
        val validPos = mover.update(moveDirection, handle, coll, worldMeshes, moveCircle)
        if (validPos != null) {
            coll.position = validPos
            handle.position = validPos
            hud.updateAllPositions(validPos)
            playerPositionFlow.value = validPos
            return validPos
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
        val pos = collider?.position ?: return
        hud.showChat(pos, message)
    }
    
    /**
     * Removes a chat message from above the player's head
     */
    private fun removeChatMessage() { hud.clearChat() }

    /**
     * Initializes the chat message listener
     */
    private var chatListenerToken: ListenerToken? = null
    private var updatePlayerListenerToken: ListenerToken? = null

    fun initializeChatMessageListener() { netSync.initChatListener { msg -> displayChatMessage(msg) } }
    
    /**
     * Initializes the player status display
     * 
     * This method fetches the current user's status and displays it if not blank.
     * It also sets up a listener for status updates from the WebSocket.
     */
    fun initializePlayerStatus() {
        if (worldObjectManager == null || collider?.position == null) return
        netSync.initStatusListener(onShowStatus = { displayPlayerStatus(it) }, onClearStatus = { removePlayerStatus() })
    }

    /**
     * Initializes the teleport listener
     * 
     * This method sets up a listener for teleport events that will navigate to the specified world URL.
     */
    fun initializeTeleportListener(router: Router) { netSync.initTeleportListener(router) }

    /**
     * Initializes the pause listener (single-device enforcement)
     */
    fun initializePauseListener(router: Router) { netSync.initPauseListener(router) }

    /**
     * Updates the player's username text
     * 
     * @param username The new username to display
     */
    fun updatePlayerName(username: String) { collider?.position?.let { hud.updateUsername(it, username) } }
    
    /**
     * Displays the player's status above their head
     * 
     * @param status The status text to display
     */
    fun displayPlayerStatus(status: String) {
        if (status.isBlank()) { removePlayerStatus(); return }
        val pos = collider?.position ?: return
        hud.showStatus(pos, status)
    }
    
    /**
     * Removes the player's status text
     */
    private fun removePlayerStatus() { hud.clearStatus() }

    /**
     * Refreshes the player's worn items based on the current inventory state.
     * This method is called when the inventory changes.
     */
    fun refreshWornItems() {
        val manager = worldObjectManager ?: return
        val handle = playerHandle ?: return
        playerHandle = WornItemsRefresher.refresh(manager, handle, playerDirection, playerActivity)
    }
    
    /**
     * Disposes of the player controller resources.
     */
    fun dispose() {
        collider?.dispose()
        moveCircle?.dispose()
        playerHandle?.remove()
        hud.dispose()
        
        // Remove the inventory refresh callback
        inventoryRefreshCallback?.let { callback ->
            InventoryState.removeOnRefreshCallback(callback)
            inventoryRefreshCallback = null
        }
        
        // Remove WebSocket listeners scoped to this controller
        netSync.dispose()
    }
}
