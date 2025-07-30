package main.components.player

import lib.AbstractMesh
import lib.Color3
import lib.CreateBoxOptions
import lib.CreateDiscOptions
import lib.Mesh
import lib.MeshBuilder
import lib.PBRMaterial
import lib.Ray
import lib.Scene
import lib.StandardMaterial
import lib.Vector3
import main.api.UserApi
import main.api.WebSocketApi
import models.Position
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.abs

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
class PlayerController(private val scene: Scene) {
    
    // The player mesh
    private var player: Mesh? = null
    
    // Target position for click-to-move
    private var playerTargetPosition: Vector3? = null
    
    // Visual indicator for move target
    private var moveCircle: Mesh? = null
    
    // World meshes for collision detection
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    // Movement state
    private var isMoving = false
    
    // Movement speed (units per frame)
    private val moveSpeed = 0.025f
    
    // Base movement speed in units per second for framerate-independent movement
    private val baseMovementSpeed = 3.0f
    
    // Time tracking for framerate-independent movement
    private var lastFrameTime = 0.0
    
    // Coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    
    // Properties for detecting minimal movement
    private var lastSignificantPosition: Vector3? = null
    private var timeWithMinimalMovement = 0.0
    private val minimalMovementThreshold = 0.01f // Distance threshold to consider movement as minimal
    private val giveUpTimeThreshold = 1.0 // Time in seconds to give up if movement is minimal
    
    /**
     * Creates the player mesh.
     * 
     * @param startPosition The starting position for the player
     */
    fun createPlayer(startPosition: Vector3) {
        // Create player box
        player = MeshBuilder.CreateBox("player", 
            object : CreateBoxOptions {
                override val width = 0.05f
                override val height = 1.0f
                override val depth = 0.25f
            }, scene)
        
        // Create player material
        val playerMaterial = PBRMaterial("playerMaterial", scene).apply {
            albedoColor = Color3(0.2f, 0.4f, 0.8f)
            metallic = 0.1f
            roughness = 0.2f
            emissiveColor = Color3(0.1f, 0.1f, 0.3f)
        }
        player?.material = playerMaterial
        
        // Position player
        player?.position = startPosition
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
    fun getPlayer(): Mesh? {
        return player
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
        return player?.position
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
            if (mesh === player || mesh === moveCircle) continue
            
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
            if (mesh === player || mesh === moveCircle) continue
            
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                // Return position with updated Y coordinate
                return Vector3(position.x, hit.pickedPoint!!.y + 0.5f, position.z)
            }
        }
        
        return null
    }
    
    /**
     * Updates the player's position based on movement direction or target position.
     * Uses delta time to ensure consistent movement speed regardless of framerate.
     * 
     * @param moveDirection The direction to move in (from keyboard input)
     * @return The new position if movement occurred, null otherwise
     */
    fun updatePlayerPosition(moveDirection: Vector3): Vector3? {
        if (player == null) return null
        
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
        
        // Handle keyboard movement
        if (moveDirection.length() > 0f) {
            finalMoveDirection = moveDirection.scale(frameMovementSpeed)
            // Cancel click-to-move if keyboard movement is active
            stopMovement()
        }
        // Handle click-to-move
        else if (isMoving && playerTargetPosition != null) {
            val currentPos = player!!.position
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
            }
        }
        
        // Apply movement if there is any
        if (finalMoveDirection.length() > 0f) {
            // Calculate new position
            val currentPos = player!!.position
            val newPos = currentPos.add(finalMoveDirection)
            
            // Check if the new position is valid
            if (isValidPosition(newPos)) {
                // Find the exact y position by raycasting
                val validPos = findValidYPosition(newPos)
                
                if (validPos != null) {
                    // Check for minimal movement if we're using click-to-move
                    if (isMoving && playerTargetPosition != null) {
                        // Initialize lastSignificantPosition if it's null
                        if (lastSignificantPosition == null) {
                            lastSignificantPosition = currentPos
                        }
                        
                        // Calculate movement distance since last significant position
                        val movementX = abs(validPos.x - lastSignificantPosition!!.x)
                        val movementZ = abs(validPos.z - lastSignificantPosition!!.z)
                        val significantMovement = movementX > minimalMovementThreshold || 
                                                 movementZ > minimalMovementThreshold
                        
                        if (significantMovement) {
                            // Reset tracking if there was significant movement
                            lastSignificantPosition = validPos
                            timeWithMinimalMovement = 0.0
                        } else {
                            // Accumulate time with minimal movement
                            timeWithMinimalMovement += deltaTime
                            
                            // Give up if we've been moving minimally for too long
                            if (timeWithMinimalMovement >= giveUpTimeThreshold) {
                                console.log("Giving up movement due to minimal progress")
                                stopMovement()
                            }
                        }
                    }
                    
                    // Update player position
                    player!!.position = validPos
                    
                    // Send player position update via WebSocket
                    coroutineScope.launch {
                        try {
                            val user = UserApi.getCurrentUser()
                            WebSocketApi.updatePlayerPosition(
                                position = Position(
                                    x = validPos.x,
                                    y = validPos.y,
                                    z = validPos.z
                                ),
                                isPlayer = user.isPlayer
                            )
                        } catch (e: Exception) {
                            console.error("Error updating player position", e)
                        }
                    }
                    
                    return validPos
                }
            } else if (isMoving) {
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
     * Disposes of the player controller resources.
     */
    fun dispose() {
        player?.dispose()
        moveCircle?.dispose()
    }
}
