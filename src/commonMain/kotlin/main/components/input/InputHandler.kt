package main.components.input

import lib.KeyboardEventTypes
import lib.KeyboardInfo
import lib.Matrix
import lib.PointerEventTypes
import lib.PointerInfo
import lib.Scene
import lib.Vector3
import main.components.SceneManager
import main.components.camera.CameraController
import main.components.`object`.SceneObjectManager
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
    // Get the object manager from SceneManager
    private val objectManager: SceneObjectManager? = SceneManager.getObjectManager() as? SceneObjectManager
    
    // Object dragging state
    private var draggingObjectId: String? = null
    private var isObjectDragging = false
    
    // Track camera input state
    private var cameraInputsEnabled = true
    
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
        // Check if an object is selected or being dragged
        val isObjectSelected = objectManager?.getSelectedObject() != null
        val isObjectBeingDragged = isObjectDragging || draggingObjectId != null
        
        // If an object is selected or being dragged, handle object manipulation instead of player/camera movement
        if (isObjectSelected || isObjectBeingDragged) {
            // If we just started object manipulation, disable camera inputs
            if (cameraInputsEnabled) {
                disableCameraInputs()
                cameraInputsEnabled = false
                console.log("Camera inputs disabled for object manipulation")
            }
            
            // For POINTERMOVE events during dragging, we need to prevent any camera movement
            if (info.type == PointerEventTypes.POINTERMOVE && isObjectDragging) {
                // We're already handling this in the object manipulation method
                // Just log for debugging
                console.log("Handling pointer move during object dragging")
            }
            
            handleObjectManipulation(info)
            return
        } else {
            // If we're no longer manipulating objects, re-enable camera inputs
            if (!cameraInputsEnabled) {
                enableCameraInputs()
                cameraInputsEnabled = true
                console.log("Camera inputs re-enabled after object manipulation")
            }
        }
        
        // Handle object hovering for non-dragging pointer moves
        if (info.type == PointerEventTypes.POINTERMOVE && !isObjectBeingDragged) {
            handleObjectHovering()
        }
        
        // Normal player/camera movement handling
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
     * Disables camera inputs to prevent camera movement during object manipulation
     */
    private fun disableCameraInputs() {
        val camera = cameraController.getCamera()
        
        // First detach all controls
        camera.detachControl()
        
        // Also disable specific input types to ensure no camera movement during dragging
        camera.inputs.removeByType("ArcRotateCameraKeyboardMoveInput")
        camera.inputs.removeByType("ArcRotateCameraMouseWheelInput")
        camera.inputs.removeByType("ArcRotateCameraPointersInput")
        
        console.log("Camera inputs disabled for object dragging")
    }
    
    /**
     * Re-enables camera inputs after object manipulation is complete
     */
    private fun enableCameraInputs() {
        val camera = cameraController.getCamera()
        
        // Re-attach all controls with preventDefaultBehavior set to true
        // This prevents the browser's default behavior for these events
        camera.attachControl(true)
        
        console.log("Camera inputs re-enabled")
    }
    
    /**
     * Handles pointer events for object manipulation.
     * 
     * @param info The pointer event information
     */
    private fun handleObjectManipulation(info: PointerInfo) {
        // This method will handle object selection, dragging, etc.
        // For now, we're just preventing player/camera movement when objects are selected
        
        when (info.type) {
            PointerEventTypes.POINTERDOWN -> {
                // Track pointer down state for potential object dragging
                isPointerDown = true
                pointerStartX = scene.pointerX
                pointerStartY = scene.pointerY
            }
            
            PointerEventTypes.POINTERMOVE -> {
                // If pointer is down, we might be dragging an object
                if (isPointerDown && objectManager != null) {
                    val selectedObjectId = objectManager.getSelectedObject()
                    
                    // If we have a selected object and we're not already dragging
                    if (selectedObjectId != null && !isObjectDragging) {
                        // Disable camera inputs before starting drag
                        if (cameraInputsEnabled) {
                            disableCameraInputs()
                            cameraInputsEnabled = false
                        }
                        
                        // Start dragging the object
                        isObjectDragging = objectManager.startDragging(selectedObjectId)
                        draggingObjectId = selectedObjectId
                    }
                    
                    // If we're dragging an object, update its position
                    if (isObjectDragging && draggingObjectId != null) {
                        // Calculate new position based on pointer movement
                        // This is simplified - in a real implementation, we would use raycasting
                        val ray = scene.createPickingRay(
                            scene.pointerX,
                            scene.pointerY,
                            Matrix.Identity(),
                            cameraController.getCamera()
                        )
                        
                        // Find intersection with world meshes
                        // Get world meshes from both controllers to ensure we have all possible meshes
                        val playerWorldMeshes = playerController.getWorldMeshes()
                        val objectWorldMeshes = objectManager.getWorldMeshes()
                        val player = playerController.getCollider()
                        val moveCircle = playerController.getMoveCircle()
                        
                        console.log("Ray casting with ${playerWorldMeshes.size} player world meshes and ${objectWorldMeshes.size} object world meshes")
                        
                        // Find the closest intersection point
                        var closestDistance = Float.MAX_VALUE
                        var closestPoint: Vector3? = null
                        var hitCount = 0
                        
                        // Check player world meshes first
                        for (mesh in playerWorldMeshes) {
                            // Skip player and move circle meshes
                            if (mesh === player || mesh === moveCircle) continue
                            
                            val hit = ray.intersectsMesh(mesh)
                            if (hit.pickedPoint != null) {
                                hitCount++
                                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                                if (distance < closestDistance) {
                                    closestDistance = distance
                                    closestPoint = hit.pickedPoint
                                }
                            }
                        }
                        
                        // Then check object world meshes
                        for (mesh in objectWorldMeshes) {
                            // Skip player and move circle meshes (just in case they're included)
                            if (mesh === player || mesh === moveCircle) continue
                            
                            val hit = ray.intersectsMesh(mesh)
                            if (hit.pickedPoint != null) {
                                hitCount++
                                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                                if (distance < closestDistance) {
                                    closestDistance = distance
                                    closestPoint = hit.pickedPoint
                                }
                            }
                        }
                        
                        console.log("Found $hitCount ray cast hits")
                        
                        // If we found a valid point, update the object position
                        if (closestPoint != null) {
                            console.log("Dragging object to position: (${closestPoint.x}, ${closestPoint.y}, ${closestPoint.z})")
                            val success = objectManager.updateDragging(closestPoint)
                            if (!success) {
                                console.warn("Failed to update object position during drag")
                            }
                        } else {
                            console.warn("No intersection found for object dragging")
                        }
                    }
                }
            }
            
            PointerEventTypes.POINTERUP -> {
                // End dragging if we were dragging an object
                if (isObjectDragging && objectManager != null) {
                    objectManager.endDragging()
                    isObjectDragging = false
                    draggingObjectId = null
                    
                    // Re-enable camera inputs
                    if (!cameraInputsEnabled) {
                        enableCameraInputs()
                        cameraInputsEnabled = true
                    }
                }
                
                // Reset pointer state
                isPointerDown = false
            }
            
            else -> { /* Ignore other pointer events */ }
        }
    }
    
    /**
     * Handles object hovering to show outline effect
     */
    private fun handleObjectHovering() {
        if (objectManager == null) {
            console.log("[DEBUG_HOVER] ObjectManager is null, cannot handle hovering")
            return
        }
        
        // Create a ray from the camera through the pointer position
        val ray = scene.createPickingRay(
            scene.pointerX,
            scene.pointerY,
            Matrix.Identity(),
            cameraController.getCamera()
        )
        
        // Check for intersection with object meshes
        var closestDistance = Float.MAX_VALUE
        var closestObjectId: String? = null
        
        // Get the number of object meshes
        val objectMeshes = objectManager.getObjectMeshes()
        console.log("[DEBUG_HOVER] Checking ${objectMeshes.size} object meshes for hover")
        
        // Check each object mesh for intersection
        for ((objectId, mesh) in objectMeshes) {
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestObjectId = objectId
                    console.log("[DEBUG_HOVER] Found intersection with object: $objectId at distance: $distance")
                }
            }
        }
        
        // If we found an object, highlight it
        if (closestObjectId != null) {
            console.log("[DEBUG_HOVER] Highlighting object: $closestObjectId")
            val success = objectManager.highlightObject(closestObjectId)
            console.log("[DEBUG_HOVER] Highlight success: $success")
        } else {
            // If no object is hovered, remove any existing highlight
            console.log("[DEBUG_HOVER] No object found, removing highlight")
            objectManager.unhighlightObject()
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
        val player = playerController.getCollider()
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
