package main.components.input

import lib.AbstractMesh
import lib.KeyboardEventTypes
import lib.KeyboardInfo
import lib.Matrix
import lib.PointerEventTypes
import lib.PointerInfo
import lib.Scene
import lib.Vector3
import main.components.camera.CameraController
import main.components.player.PlayerController
import kotlin.math.abs

/**
 * Handler for processing keyboard and pointer inputs in the 3D scene.
 * 
 * This class encapsulates all input-related functionality including:
 * - Keyboard input processing (WASD and arrow keys)
 * - Pointer input processing (click-to-move)
 * - Connecting inputs to player movement
 */
class InputHandler(
    private val scene: Scene,
    private val playerController: PlayerController,
    private val cameraController: CameraController
) {
    
    // Track pressed keys for continuous movement
    private val pressedKeys = mutableSetOf<String>()
    
    // Track if pointer is being dragged
    private var isPointerDown = false
    private var pointerStartX = 0
    private var pointerStartY = 0
    private var isDragging = false
    
    /**
     * Sets up keyboard input handling.
     */
    fun setupKeyboardHandling() {
        scene.onKeyboardObservable.add { info ->
            handleKeyboardEvent(info)
        }
    }
    
    /**
     * Sets up pointer input handling.
     */
    fun setupPointerHandling() {
        scene.onPointerObservable.add { info ->
            handlePointerEvent(info)
        }
    }
    
    /**
     * Handles keyboard events.
     * 
     * @param info The keyboard event information
     */
    private fun handleKeyboardEvent(info: KeyboardInfo) {
        val event = info.event
        val key = event.key.lowercase()
        
        // Only handle WASD and arrow keys
        if (key in listOf("w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright")) {
            when (info.type) {
                KeyboardEventTypes.KEYDOWN -> {
                    // Add key to pressed keys set
                    pressedKeys.add(key)
                    
                    // Cancel any click-to-move in progress
                    if (pressedKeys.isNotEmpty()) {
                        playerController.stopMovement()
                    }
                }
                KeyboardEventTypes.KEYUP -> {
                    // Remove key from pressed keys set
                    pressedKeys.remove(key)
                }
                else -> { /* Ignore other keyboard events */ }
            }
        }
    }
    
    /**
     * Handles pointer events.
     * 
     * @param info The pointer event information
     */
    private fun handlePointerEvent(info: PointerInfo) {
        when (info.type) {
            PointerEventTypes.POINTERDOWN -> {
                // Track pointer down state and position
                isPointerDown = true
                pointerStartX = scene.pointerX
                pointerStartY = scene.pointerY
                isDragging = false
            }
            
            PointerEventTypes.POINTERMOVE -> {
                // Check if dragging (pointer is down and has moved more than a threshold)
                if (isPointerDown) {
                    val deltaX = abs(scene.pointerX - pointerStartX)
                    val deltaY = abs(scene.pointerY - pointerStartY)
                    
                    // Consider it a drag if moved more than 5 pixels in any direction
                    if (deltaX > 5 || deltaY > 5) {
                        isDragging = true
                        
                        // If dragging, cancel any movement
                        playerController.stopMovement()
                    }
                }
            }
            
            PointerEventTypes.POINTERUP -> {
                // If it was a click (not a drag) and player exists, handle movement
                if (!isDragging) {
                    handleClickToMove()
                }
                
                // Reset pointer state
                isPointerDown = false
                isDragging = false
            }
            
            else -> { /* Ignore other pointer events */ }
        }
    }
    
    /**
     * Handles click-to-move functionality.
     */
    private fun handleClickToMove() {
        // Create a ray from the camera through the clicked point
        val ray = scene.createPickingRay(
            scene.pointerX,
            scene.pointerY,
            Matrix.Identity(),
            cameraController.getCamera()
        )
        
        // Check for intersection with world meshes
        var closestDistance = Float.MAX_VALUE
        var closestPoint: Vector3? = null
        
        // Get world meshes for collision detection
        val worldMeshes = playerController.getWorldMeshes()
        val player = playerController.getPlayer()
        val moveCircle = playerController.getMoveCircle()
        
        for (mesh in worldMeshes) {
            // Skip player and move circle meshes
            if (mesh === player || mesh === moveCircle) continue
            
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestPoint = hit.pickedPoint
                }
            }
        }
        
        // If we found a valid point, set it as the target
        if (closestPoint != null) {
            playerController.setTargetPosition(closestPoint)
        }
    }
    
    /**
     * Calculates the movement direction based on pressed keys.
     * 
     * @return The movement direction vector
     */
    fun calculateMovementDirection(): Vector3 {
        if (pressedKeys.isEmpty()) {
            return Vector3.Zero()
        }
        
        val camera = cameraController.getCamera()
        var moveDirection = Vector3.Zero()
        
        // Get camera's forward and right directions
        val cameraForward = Vector3.Zero()
        val cameraRight = Vector3.Zero()
        
        // Get forward direction from camera (ignoring Y component for flat movement)
        val forward = camera.getDirection(Vector3.Forward())
        cameraForward.x = forward.x
        cameraForward.z = forward.z
        cameraForward.normalize()
        
        // Get right direction (perpendicular to forward)
        cameraRight.x = cameraForward.z
        cameraRight.z = -cameraForward.x
        cameraRight.normalize()
        
        // Calculate movement based on pressed keys
        var moveX = 0f
        var moveZ = 0f
        
        // WASD keys
        if ("w" in pressedKeys) moveZ += 1f
        if ("s" in pressedKeys) moveZ -= 1f
        if ("a" in pressedKeys) moveX -= 1f
        if ("d" in pressedKeys) moveX += 1f
        
        // Arrow keys (only control player, not camera)
        if ("arrowup" in pressedKeys) moveZ += 1f
        if ("arrowdown" in pressedKeys) moveZ -= 1f
        if ("arrowleft" in pressedKeys) moveX -= 1f
        if ("arrowright" in pressedKeys) moveX += 1f
        
        // Combine directions relative to camera
        if (moveX != 0f || moveZ != 0f) {
            // Forward/backward movement
            if (moveZ != 0f) {
                moveDirection = moveDirection.add(cameraForward.scale(moveZ))
            }
            
            // Left/right movement
            if (moveX != 0f) {
                moveDirection = moveDirection.add(cameraRight.scale(moveX))
            }
            
            // Normalize for consistent speed in diagonal movement
            if (moveDirection.length() > 0) {
                moveDirection.normalize()
            }
        }
        
        return moveDirection
    }
    
    /**
     * Gets the set of currently pressed keys.
     * 
     * @return The set of pressed keys
     */
    fun getPressedKeys(): Set<String> {
        return pressedKeys
    }
    
    /**
     * Checks if any keys are currently pressed.
     * 
     * @return True if any keys are pressed, false otherwise
     */
    fun areKeysPressed(): Boolean {
        return pressedKeys.isNotEmpty()
    }
}
