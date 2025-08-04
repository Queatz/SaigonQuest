package main.components.input

import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.document
import kotlinx.browser.window
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
import main.components.player.OtherPlayerDialog
import main.components.player.PlayerController
import main.utils.createObjectDoubleClickEvent
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.renderComposable
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
    
    // Double-click detection
    private var lastClickTime = 0.0
    private var lastClickedObjectId: String? = null
    private val doubleClickThreshold = 300.0 // milliseconds
    
    // Dialog state
    private val showDialog = mutableStateOf(false)
    private val dialogUsername = mutableStateOf("")
    private val dialogIsPlayer = mutableStateOf(false)
    
    // Dialog container ID
    private val dialogContainerId = "other-player-dialog-container"
    
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

        console.log("[POINTER] ${info.type} - isObjectSelected: $isObjectSelected, isObjectBeingDragged: $isObjectBeingDragged")
        
        // Always handle object hovering for pointer move events to highlight objects
        if (info.type == PointerEventTypes.POINTERMOVE) {
            handleObjectHovering()
        }
        
        // Handle object selection on pointer down
        if (info.type == PointerEventTypes.POINTERDOWN && objectManager != null) {
            // Use ray casting to find the object under the pointer
            val ray = scene.createPickingRay(
                scene.pointerX,
                scene.pointerY,
                Matrix.Identity(),
                cameraController.getCamera()
            )
            
            // Check for intersection with object meshes
            var closestDistance = Float.MAX_VALUE
            var closestObjectId: String? = null
            
            // Get the object meshes
            val objectMeshes = objectManager.getObjectMeshes()
            
            // Check each object mesh for intersection
            for ((objectId, meshes) in objectMeshes) {
                meshes.forEach { mesh ->
                    val hit = ray.intersectsMesh(mesh)
                    if (hit.pickedPoint != null) {
                        val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                        if (distance < closestDistance) {
                            closestDistance = distance
                            closestObjectId = objectId
                            console.log("[POINTER_DOWN] Found intersection with object: $objectId at distance: $distance")
                        }
                    }
                }
            }
            
            // If we found an object, select it
            if (closestObjectId != null) {
                console.log("[POINTER_DOWN] Selecting object: $closestObjectId")
                objectManager.selectObject(closestObjectId)
                
                // Check for double-click
                val currentTime = window.performance.now()
                if (closestObjectId == lastClickedObjectId && 
                    (currentTime - lastClickTime) < doubleClickThreshold) {
                    // This is a double-click on the same object
                    console.log("[POINTER_DOWN] Double-click detected on object: $closestObjectId")
                    
                    // Create and dispatch a custom event for object double-click
                    val doubleClickEvent = createObjectDoubleClickEvent(closestObjectId)
                    window.dispatchEvent(doubleClickEvent)
                }
                
                // Update last click info for future double-click detection
                lastClickTime = currentTime
                lastClickedObjectId = closestObjectId
            } else {
                // If we didn't click on an object, check if we clicked on a world mesh
                // and deselect any selected object
                val worldMeshes = playerController.getWorldMeshes()
                val player = playerController.getCollider()
                val moveCircle = playerController.getMoveCircle()
                
                var clickedOnWorldMesh = false
                
                for (mesh in worldMeshes) {
                    // Skip player and move circle meshes
                    if (mesh === player || mesh === moveCircle) continue
                    
                    val hit = ray.intersectsMesh(mesh)
                    if (hit.pickedPoint != null) {
                        clickedOnWorldMesh = true
                        break
                    }
                }
                
                if (clickedOnWorldMesh) {
                    // If we clicked on a world mesh, deselect any selected object
                    val wasObjectDeselected = objectManager.deselectObject()
                    if (wasObjectDeselected) {
                        console.log("[POINTER_DOWN] Deselected object by clicking on world mesh")
                    }
                }
            }
        }
        
        // If an object is selected or being dragged, handle object manipulation
        if (isObjectSelected || isObjectBeingDragged) {
            // If we just started object manipulation, disable camera inputs
            if (cameraInputsEnabled) {
                disableCameraInputs()
                cameraInputsEnabled = false
                console.log("Camera inputs disabled for object manipulation")
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
                // If it was a click (not a drag) and no object is selected, handle movement
                if (!isDragging && !isObjectSelected) {
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
        // Use the CameraController to disable camera inputs
        cameraController.disableCameraInputs()
    }
    
    /**
     * Re-enables camera inputs after object manipulation is complete
     */
    private fun enableCameraInputs() {
        // Use the CameraController to enable camera inputs
        cameraController.enableCameraInputs()
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
                console.log("[POINTER] DOWN", objectManager)
                isPointerDown = true
                pointerStartX = scene.pointerX
                pointerStartY = scene.pointerY
            }
            
            PointerEventTypes.POINTERMOVE -> {
                // If pointer is down, we might be dragging an object
                if (isPointerDown) {
                    console.log("[POINTER] MOVE", objectManager)
                    val objectManager = objectManager!!

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
                console.log("[POINTER] UP", objectManager)
                // End dragging if we were dragging an object
                if (isObjectDragging) {
                    objectManager!!.endDragging()
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
    
    // Track if we're hovering over a player
    private var isHoveringOverPlayer = false
    
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
        
        // First check for intersection with player meshes
        val otherPlayersController = SceneManager.getOtherPlayersController()
        var isHoveringPlayer = false
        
        if (otherPlayersController != null) {
            val playerMeshes = otherPlayersController.getPlayerMeshes()
            
            for (mesh in playerMeshes) {
                val hit = ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    // We're hovering over a player, change cursor to pointer
                    document.body?.style?.cursor = "pointer"
                    isHoveringPlayer = true
                    isHoveringOverPlayer = true
                    break
                }
            }
        }
        
        // If not hovering over a player, check for objects
        if (!isHoveringPlayer) {
            // Reset player hover state
            if (isHoveringOverPlayer) {
                isHoveringOverPlayer = false
                // Only reset cursor if we were previously hovering over a player
                // This allows the object manager to handle cursor for objects
                document.body?.style?.cursor = "default"
            }
            
            // Check for intersection with object meshes
            var closestDistance = Float.MAX_VALUE
            var closestObjectId: String? = null
            
            // Get the number of object meshes
            val objectMeshes = objectManager.getObjectMeshes()
            console.log("[DEBUG_HOVER] Checking ${objectMeshes.size} object meshes for hover")
            
            // Check each object mesh for intersection
            for ((objectId, meshes) in objectMeshes) {
                meshes.forEach { mesh ->
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
    
        // Get the other players controller
        val otherPlayersController = main.components.SceneManager.getOtherPlayersController()
    
        // First check for intersection with other player meshes
        if (otherPlayersController != null) {
            val playerMeshes = otherPlayersController.getPlayerMeshes()
        
            for (mesh in playerMeshes) {
                val hit = ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    // We clicked on another player
                    val userId = otherPlayersController.getUserIdForMesh(mesh)
                    if (userId != null) {
                        // Get player data
                        val playerData = otherPlayersController.getPlayerData(userId)
                        if (playerData != null) {
                            // Show the dialog with player data
                            showOtherPlayerDialog(playerData.first, playerData.second)
                            return // Don't move the player
                        }
                    }
                }
            }
        }
    
        // If we didn't click on another player, proceed with normal click-to-move
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
     * Shows the dialog for interacting with another player
     * 
     * @param username The username of the player
     * @param isPlayer Whether the user is a player (true) or visitor (false)
     */
    private fun showOtherPlayerDialog(username: String, isPlayer: Boolean) {
        console.log("Clicked on player: $username (${if (isPlayer) "Player" else "Visitor"})")
    
        // Update dialog state
        dialogUsername.value = username
        dialogIsPlayer.value = isPlayer
        showDialog.value = true
    
        // Create or get the dialog container
        var container = document.getElementById(dialogContainerId)
        if (container == null) {
            container = document.createElement("div")
            container.id = dialogContainerId
            document.body?.appendChild(container)
        }
    
        // Render the dialog
        renderComposable(dialogContainerId) {
            if (showDialog.value) {
                OtherPlayerDialog(
                    username = dialogUsername.value,
                    isPlayer = dialogIsPlayer.value,
                    onAddFriend = {
                        // Handle Add Friend button click
                        console.log("Add Friend clicked for player: ${dialogUsername.value}")
                        // TODO: Implement friend request functionality
                    
                        // Close the dialog
                        showDialog.value = false
                    },
                    onTrade = {
                        // Handle Trade button click
                        console.log("Trade clicked for player: ${dialogUsername.value}")
                        // TODO: Implement trade functionality
                    
                        // Close the dialog
                        showDialog.value = false
                    },
                    onClose = {
                        // Handle Close button click
                        console.log("Dialog closed for player: ${dialogUsername.value}")
                    
                        // Close the dialog
                        showDialog.value = false
                    }
                )
            } else {
                // Render an empty div when dialog is not shown
                Div {}
            }
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
