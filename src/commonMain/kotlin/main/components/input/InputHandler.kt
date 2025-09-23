package main.components.input

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import lib.KeyboardEventTypes
import lib.KeyboardInfo
import lib.Matrix
import lib.PointerEventTypes
import lib.PointerInfo
import lib.Scene
import lib.Vector3
import lib.Ray
import lib.AbstractMesh
import Translations.str
import main.api.InventoryApi
import main.api.ObjectApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.playerInteract
import main.components.SceneManager
import main.components.camera.CameraController
import main.components.inventory.InventoryState
import main.components.objects.SceneObjectManager
import main.components.player.PlayerController
import main.components.world.DepthPicking
import main.components.world.isSkybox
import main.utils.createObjectDoubleClickEvent
import main.utils.createItemCollectedEvent
import models.World
import kotlin.math.abs

/**
 * Handler for processing keyboard and pointer inputs in the 3D scene.
 * 
 * This class encapsulates all input-related functionality including:
 * - Keyboard input processing (WASD and arrow keys)
 * - Pointer input processing (click-to-move)
 * - Connecting inputs to player movement
 */

// Unified pick result for pointer interactions
private data class PointerPickResult(
    val objectId: String?,
    val inventoryItemId: String?,
    val ray: Ray,
)

// Computes the closest picked object or inventory item under the current pointer
// Returns both candidates and the ray used so callers can reuse it for world checks
private fun pickUnderPointer(
    scene: Scene,
    cameraController: CameraController,
    objectManager: SceneObjectManager,
): PointerPickResult {
    val ray = scene.createPickingRay(
        x = scene.pointerX,
        y = scene.pointerY,
        world = Matrix.Identity(),
        camera = cameraController.getCamera(),
    )

    var closestDistance = Float.MAX_VALUE
    var closestObjectId: String? = null
    var closestInventoryItemId: String? = null
    var isInventoryItem = false

    val objectMeshes = objectManager.getObjectMeshes()

    for ((objectId, meshes) in objectMeshes) {
        val isInventoryObj = objectManager.isInventoryObject(objectId)

        meshes.forEach { mesh ->
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                if (isInventoryObj) {
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestInventoryItemId = objectId
                        isInventoryItem = true
                    }
                } else if (!isInventoryItem && distance < closestDistance) {
                    closestDistance = distance
                    closestObjectId = objectId
                }
            }
        }
    }

    return PointerPickResult(
        objectId = closestObjectId,
        inventoryItemId = closestInventoryItemId,
        ray = ray,
    )
}

private const val SLOP = 50

class InputHandler(
    private val scene: Scene,
    private val playerController: PlayerController,
    private val cameraController: CameraController,
    private val onShowOtherPlayerDialog: ((username: String, isPlayer: Boolean) -> Unit)? = null,
    private val onShowMessageDialog: ((message: String) -> Unit)? = null,
) {
    // Get the object manager from SceneManager
    private val objectManager: SceneObjectManager? = SceneManager.getObjectManager()
    
    // Object dragging state
    private var draggingObjectId: String? = null
    private var isObjectDragging = false
    
    // Track camera input state
    private var cameraInputsEnabled = true
    
    // Current world
    var currentWorld: World? = null
    
    // Check if the current user is the owner of the world
    private suspend fun isWorldOwner(): Boolean {
        try {
            if (currentWorld == null) {
                return false
            }
            
            // Get the current user
            val currentUser = UserApi.getCurrentUser()
            
            // Check if the user is the owner of the world
            return currentUser.key == currentWorld?.userId
        } catch (e: Throwable) {
            console.error("Error checking world ownership", e)
            return false
        }
    }
    
    // Track pressed keys for continuous movement
    private val pressedKeys = mutableSetOf<String>()
    
    // Track if pointer is being dragged
    private var isPointerDown = false
    private var pointerStartX = 0
    private var pointerStartY = 0
    private var isDragging = false
    
    // Double-tap handled by engine; delay single-tap to allow double-tap to preempt
    private val tapDoubleClickThreshold = 300L
    private var pendingTapSingleJob: Job? = null
    
    // Store the click point for object dragging
    private var objectClickPoint: Vector3? = null

    private val scope = CoroutineScope(Dispatchers.Default)
    
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
        
        // Handle movement keys (WASD and arrow keys)
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
        // Handle Z key for toggling sitting/standing
        else if (key == "z") {
            when (info.type) {
                KeyboardEventTypes.KEYUP -> {
                    // Toggle sitting/standing on key up (to avoid repeated toggling while key is held)
                    playerController.toggleSitting()
                    console.log("InputHandler: Toggled sitting/standing")
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
        
        // Handle Babylon tap/double-tap events explicitly (touch & click)
        if (info.type == PointerEventTypes.POINTERDOUBLETAP && objectManager != null) {
            // Double-tap cancels any pending single-tap action
            pendingTapSingleJob?.cancel()
            pendingTapSingleJob = null
            // First, check if we double-tapped a player: show dialog
            run {
                val otherPlayersController = SceneManager.getOtherPlayersController()
                if (otherPlayersController != null) {
                    val ray = scene.createPickingRay(
                        x = scene.pointerX,
                        y = scene.pointerY,
                        world = Matrix.Identity(),
                        camera = cameraController.getCamera(),
                    )
                    val meshes = otherPlayersController.getPlayerMeshes()
                    for (m in meshes) {
                        val hit = ray.intersectsMesh(m)
                        if (hit.pickedPoint != null) {
                            val userId = otherPlayersController.getUserIdForMesh(m)
                            if (userId != null) {
                                val data = otherPlayersController.getPlayerData(userId)
                                if (data != null) {
                                    showOtherPlayerDialog(data.first, data.second)
                                    return
                                }
                            }
                        }
                    }
                }
            }

            // Otherwise, double-tap on object: trigger edit if owner
            val pick = pickUnderPointer(
                scene = scene,
                cameraController = cameraController,
                objectManager = objectManager,
            )
            pick.objectId?.let { objectId ->
                scope.launch {
                    if (isWorldOwner()) {
                        val doubleClickEvent = createObjectDoubleClickEvent(objectId)
                        window.dispatchEvent(doubleClickEvent)
                    }
                }
                return
            }
        }

        if (info.type == PointerEventTypes.POINTERTAP && objectManager != null) {
            // First, check if we tapped a player: show dialog and do not move
            run {
                val otherPlayersController = SceneManager.getOtherPlayersController()
                if (otherPlayersController != null) {
                    val ray = scene.createPickingRay(
                        x = scene.pointerX,
                        y = scene.pointerY,
                        world = Matrix.Identity(),
                        camera = cameraController.getCamera(),
                    )
                    val meshes = otherPlayersController.getPlayerMeshes()
                    for (m in meshes) {
                        val hit = ray.intersectsMesh(m)
                        if (hit.pickedPoint != null) {
                            val userId = otherPlayersController.getUserIdForMesh(m)
                            if (userId != null) {
                                val data = otherPlayersController.getPlayerData(userId)
                                if (data != null) {
                                    // Any explicit player interaction should cancel pending single-object tap
                                    pendingTapSingleJob?.cancel()
                                    pendingTapSingleJob = null
                                    showOtherPlayerDialog(data.first, data.second)
                                    return
                                }
                            }
                        }
                    }
                }
            }

            val pick = pickUnderPointer(
                scene = scene,
                cameraController = cameraController,
                objectManager = objectManager,
            )

            // Inventory item tap
            pick.inventoryItemId?.let {
                console.log("[POINTER_TAP] Collecting inventory item: $it")
                // Cancel any pending object tap interaction
                pendingTapSingleJob?.cancel()
                pendingTapSingleJob = null
                collectInventoryItem(it)
                return
            }

            // Object tap: schedule single-tap interaction, allow double-tap to preempt
            pick.objectId?.let { objectId ->
                console.log("[POINTER_TAP] Schedule interact after threshold for object: $objectId")
                pendingTapSingleJob?.cancel()
                pendingTapSingleJob = scope.launch {
                    delay(tapDoubleClickThreshold)
                    objectManager.selectObject(objectId)
                    val isPlayer = UserApi.getToken() != null
                    WebSocketApi.playerInteract(objectId, isPlayer)
                    pendingTapSingleJob = null
                }
                return
            }
        }

        // Always handle object hovering for pointer move events to highlight objects
        if (info.type == PointerEventTypes.POINTERMOVE) {
            // Check if dragging (pointer is down and has moved more than a threshold)
            if (isPointerDown) {
                val deltaX = abs(scene.pointerX - pointerStartX)
                val deltaY = abs(scene.pointerY - pointerStartY)

                // Consider it a drag if moved more than SLOP pixels in any direction
                if (deltaX > SLOP || deltaY > SLOP) {
                    isDragging = true

                    // If dragging, cancel any pending single-tap and movement
                    pendingTapSingleJob?.cancel()
                    pendingTapSingleJob = null
                    playerController.stopMovement()
                }
            }
            handleObjectHovering()
        }
        
        // Handle object selection on pointer down
        if (info.type == PointerEventTypes.POINTERDOWN && objectManager != null) {
            // Track pointer down state and position
            isPointerDown = true
            pointerStartX = scene.pointerX
            pointerStartY = scene.pointerY
            isDragging = false

            // Starting a new pointer interaction: cancel any pending single-tap
            pendingTapSingleJob?.cancel()
            pendingTapSingleJob = null

            val pick = pickUnderPointer(
                scene = scene,
                cameraController = cameraController,
                objectManager = objectManager,
            )

            // If we found an object, select it
            pick.objectId?.let { closestObjectId ->
                console.log("[POINTER_DOWN] Selecting object: $closestObjectId")
                objectManager.selectObject(closestObjectId)
                return
            }

            // If we didn't click on an object, check if we clicked on a world mesh
            // and deselect any selected object
            val worldMeshes = playerController.getWorldMeshes()
            val player = playerController.getCollider()
            val moveCircle = playerController.getMoveCircle()

            var clickedOnWorldMesh = false

            for (mesh in worldMeshes) {
                // Skip player and move circle meshes
                if (mesh == player || mesh == moveCircle || mesh.isSkybox()) continue

                val hit = pick.ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    clickedOnWorldMesh = true
                    break
                }
            }

            if (clickedOnWorldMesh) {
                // If we clicked on a world mesh, deselect any selected object
                objectManager.deselectCurrentObject()
            }
        }

        if (info.type == PointerEventTypes.POINTERUP) {
            // If it was a click (not a drag) and no object is selected, handle movement
            if (!isDragging && !isObjectSelected) {
                handleClickToMove()
            }

            // Reset pointer state
            isPointerDown = false
            isDragging = false
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
                isPointerDown = true
                pointerStartX = scene.pointerX
                pointerStartY = scene.pointerY
                
                // Calculate the click point for object dragging
                val selectedObjectId = objectManager?.getSelectedObject()
                if (selectedObjectId != null) {
                    // Compute click point depth-first to ignore occlusion, with ray fallback
                    scope.launch {
                        val depthPoint = try {
                            DepthPicking.getVisibleWorldPointAt(
                                scene = scene,
                                x = scene.pointerX,
                                y = scene.pointerY,
                            )
                        } catch (_: Throwable) { null }

                        if (depthPoint != null) {
                            console.log("Stored click point for dragging (depth): (${depthPoint.x}, ${depthPoint.y}, ${depthPoint.z})")
                            objectClickPoint = depthPoint
                            return@launch
                        }

                        // Fallback to ray-based closest hit among world meshes
                        val ray = scene.createPickingRay(
                            x = scene.pointerX,
                            y = scene.pointerY,
                            world = Matrix.Identity(),
                            camera = cameraController.getCamera()
                        )

                        val playerWorldMeshes = playerController.getWorldMeshes()
                        val objectWorldMeshes = objectManager.getWorldMeshes()
                        val player = playerController.getCollider()
                        val moveCircle = playerController.getMoveCircle()

                        var closestDistance = Float.MAX_VALUE
                        var clickPoint: Vector3? = null

                        for (mesh in playerWorldMeshes) {
                            if (mesh == player || mesh == moveCircle || mesh.isSkybox()) continue
                            val hit = ray.intersectsMesh(mesh)
                            if (hit.pickedPoint != null) {
                                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                                if (distance < closestDistance) {
                                    closestDistance = distance
                                    clickPoint = hit.pickedPoint
                                }
                            }
                        }
                        for (mesh in objectWorldMeshes) {
                            if (mesh == player || mesh == moveCircle || mesh.isSkybox()) continue
                            val hit = ray.intersectsMesh(mesh)
                            if (hit.pickedPoint != null) {
                                val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                                if (distance < closestDistance) {
                                    closestDistance = distance
                                    clickPoint = hit.pickedPoint
                                }
                            }
                        }

                        if (clickPoint != null) {
                            console.log("Stored click point for dragging (ray): (${clickPoint.x}, ${clickPoint.y}, ${clickPoint.z})")
                            objectClickPoint = clickPoint
                        }
                    }
                }
            }
            
            PointerEventTypes.POINTERMOVE -> {
                // If pointer is down, we might be dragging an object
                if (isPointerDown) {
                    val objectManager = objectManager!!

                    val selectedObjectId = objectManager.getSelectedObject()
                    
                    // If we have a selected object and we're not already dragging, only start dragging
                    // after the pointer has moved beyond the SLOP threshold to allow taps on touch devices
                    if (selectedObjectId != null && !isObjectDragging) {
                        val dx = abs(scene.pointerX - pointerStartX)
                        val dy = abs(scene.pointerY - pointerStartY)
                        val movedBeyondSlop = dx > SLOP || dy > SLOP
                        if (movedBeyondSlop) {
                            // Check if the current user is the owner of the world before allowing object dragging
                            scope.launch {
                                if (isWorldOwner()) {
                                    // Disable camera inputs before starting drag
                                    if (cameraInputsEnabled) {
                                        disableCameraInputs()
                                        cameraInputsEnabled = false
                                    }
                                    
                                    // Start dragging the object, passing the click point
                                    isObjectDragging = objectManager.startDragging(selectedObjectId, objectClickPoint)
                                    draggingObjectId = selectedObjectId
                                    
                                    // Log and reset the click point after starting dragging
                                    if (isObjectDragging) {
                                        console.log("Started dragging with click point: $objectClickPoint")
                                        // Create dedicated picking depth renderer (world-only) for the drag session
                                        try {
                                            val excludeList = mutableListOf<AbstractMesh>()
                                            // Exclude all object meshes from the depth pass
                                            val objMeshes = objectManager.getObjectMeshes()
                                            for ((_, meshes) in objMeshes) {
                                                excludeList.addAll(meshes)
                                            }
                                            // Exclude skybox if available (prevents far depth writes)
                                            objectManager.getSkybox()?.let { excludeList.add(it) }
                                            DepthPicking.beginPickingDepth(
                                                scene = scene,
                                                excludeMeshes = excludeList.toTypedArray(),
                                            )
                                        } catch (t: Throwable) {
                                            console.warn("[InputHandler] Failed to create dedicated picking depth renderer: ${t.message}")
                                        }
                                        // Reset click point to ensure consistent behavior between first and subsequent drags
                                        objectClickPoint = null
                                    }
                                } else {
                                    console.log("Object dragging ignored - user is not the world owner")
                                    // Deselect the object if the user is not the owner
                                    objectManager.deselectCurrentObject()
                                }
                            }
                        }
                    }
                    
                    // If we're dragging an object, update its position
                    if (isObjectDragging && draggingObjectId != null) {
                        // Depth-first position for robust dragging under occlusion; fallback to ray
                        scope.launch {
                            val depthPoint = try {
                                DepthPicking.getVisibleWorldPointAt(
                                    scene = scene,
                                    x = scene.pointerX,
                                    y = scene.pointerY,
                                )
                            } catch (_: Throwable) { null }

                            if (depthPoint != null) {
                                val success = objectManager.updateDragging(depthPoint)
                                if (!success) {
                                    console.warn("Failed to update object position during drag (depth)")
                                }
                                return@launch
                            }

                            // Fallback to ray-based closest hit
                            val ray = scene.createPickingRay(
                                x = scene.pointerX,
                                y = scene.pointerY,
                                world = Matrix.Identity(),
                                camera = cameraController.getCamera()
                            )
                            val playerWorldMeshes = playerController.getWorldMeshes()
                            val objectWorldMeshes = objectManager.getWorldMeshes()
                            val player = playerController.getCollider()
                            val moveCircle = playerController.getMoveCircle()

                            var closestDistance = Float.MAX_VALUE
                            var closestPoint: Vector3? = null

                            for (mesh in playerWorldMeshes) {
                                if (mesh == player || mesh == moveCircle || mesh.isSkybox()) continue
                                val hit = ray.intersectsMesh(mesh)
                                if (hit.pickedPoint != null) {
                                    val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                                    if (distance < closestDistance) {
                                        closestDistance = distance
                                        closestPoint = hit.pickedPoint
                                    }
                                }
                            }
                            for (mesh in objectWorldMeshes) {
                                if (mesh == player || mesh == moveCircle || mesh.isSkybox()) continue
                                val hit = ray.intersectsMesh(mesh)
                                if (hit.pickedPoint != null) {
                                    val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                                    if (distance < closestDistance) {
                                        closestDistance = distance
                                        closestPoint = hit.pickedPoint
                                    }
                                }
                            }

                            if (closestPoint != null) {
                                val success = objectManager.updateDragging(closestPoint)
                                if (!success) {
                                    console.warn("Failed to update object position during drag (ray)")
                                }
                            } else {
                                console.warn("No intersection found for object dragging (fallback)")
                            }
                        }
                    }
                }
            }
            
            PointerEventTypes.POINTERUP -> {
                // End dragging if we were dragging an object
                if (isObjectDragging) {
                    try { DepthPicking.endPickingDepth() } catch (_: Throwable) {}
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
                
                // Reset the click point
                objectClickPoint = null
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
            x = scene.pointerX,
            y = scene.pointerY,
            world = Matrix.Identity(),
            camera = cameraController.getCamera()
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
        // Create a ray from the camera through the clicked point (used for player-click detection and fallback)
        val ray = scene.createPickingRay(
            x = scene.pointerX,
            y = scene.pointerY,
            world = Matrix.Identity(),
            camera = cameraController.getCamera()
        )

        // 1) Player-click detection stays the same: do not move if we clicked another player
        val otherPlayersController = SceneManager.getOtherPlayersController()
        if (otherPlayersController != null) {
            val playerMeshes = otherPlayersController.getPlayerMeshes()
            for (mesh in playerMeshes) {
                val hit = ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    val userId = otherPlayersController.getUserIdForMesh(mesh)
                    if (userId != null) {
                        val playerData = otherPlayersController.getPlayerData(userId)
                        if (playerData != null) {
                            showOtherPlayerDialog(playerData.first, playerData.second)
                            return // Don't move the player
                        }
                    }
                }
            }
        }

        // 2) Depth-first targeting: if we can unproject a visible world point, use it directly
        scope.launch {
            try {
                val depthPoint = DepthPicking.getVisibleWorldPointAt(
                    scene = scene,
                    x = scene.pointerX,
                    y = scene.pointerY,
                )
                if (depthPoint != null) {
                    playerController.setTargetPosition(depthPoint)
                    return@launch
                }
            } catch (e: Throwable) {
                console.warn("[ClickToMove] Depth-based targeting failed: ${e.message}")
            }

            // 3) Fallback: original ray-based closest intersection with world meshes
            var closestDistance = Float.MAX_VALUE
            var closestPoint: Vector3? = null

            val worldMeshes = playerController.getWorldMeshes()
            val player = playerController.getCollider()
            val moveCircle = playerController.getMoveCircle()

            for (mesh in worldMeshes) {
                if (mesh == player || mesh == moveCircle || mesh.isSkybox()) continue
                val hit = ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestPoint = hit.pickedPoint
                    }
                }
            }

            if (closestPoint != null) {
                playerController.setTargetPosition(closestPoint)
            }
        }
    }
    
    /**
     * Shows the dialog for interacting with another player
     * 
     * @param username The username of the player
     * @param isPlayer Whether the user is a player (true) or visitor (false)
     */
    private fun showOtherPlayerDialog(username: String, isPlayer: Boolean) {
        console.log("[DEBUG_LOG] Clicked on player: $username (${if (isPlayer) "Player" else "Visitor"})")
    
        // Use the callback to show the dialog if provided
        onShowOtherPlayerDialog?.invoke(username, isPlayer)
    }
    
    /**
     * Collects an inventory item from the world.
     * 
     * This method is called when a user clicks on an inventory item in the world.
     * It calls the ObjectApi.collectObject method to collect the item and add it
     * to the user's inventory, then removes the object from the scene.
     * 
     * @param objectId The ID of the inventory item to collect
     */
    private fun collectInventoryItem(objectId: String) {
        console.log("[DEBUG_LOG] Collecting inventory item: $objectId")
        
        // Use a coroutine to call the API
        scope.launch {
            // Prevent visitors (unauthenticated users) from picking up items
            val isPlayer = try {
                UserApi.getCurrentUser().isPlayer
            } catch (_: Throwable) {
                false
            }

            if (!isPlayer) {
                // Show a confirm dialog prompting login
                onShowMessageDialog?.invoke(str("Log in to pick up items"))
                return@launch
            }

            try {
                // Retrieve object details first to get the title before it may be removed on the server
                var itemTitle = "Item"
                try {
                    val obj = ObjectApi.getObject(objectId)
                    if (obj.title.isNotBlank()) {
                        itemTitle = obj.title
                    }
                } catch (e: Throwable) {
                    console.warn("[DEBUG_LOG] Could not fetch object details for title: $objectId", e)
                }

                // Call the unified transfer API to pick up the item (default: all)
                val result = InventoryApi.transferPickup(objectId)
                if (result.objectDeleted) {
                    console.log("[DEBUG_LOG] Successfully collected inventory item: $objectId (object deleted)")
                    // Remove the object locally to mirror server REMOVE_OBJECT
                    objectManager?.removeObject(objectId)
                } else {
                    console.log("[DEBUG_LOG] Collected from object; awaiting OBJECT_UPDATE for visual update: $objectId")
                }
                
                // Notify UI via custom event so InventoryTray can show feedback
                val event = createItemCollectedEvent(itemTitle)
                window.dispatchEvent(event)

                // Refresh inventory state so UI reflects newly collected items
                InventoryState.refreshInventory(
                    coroutineScope = scope,
                )
            } catch (e: Throwable) {
                console.error("[DEBUG_LOG] Error collecting inventory item", e)
                
                // Show error message
                onShowMessageDialog?.invoke(str("Error collecting item") + ": ${e.message}")
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
     * Cleans up resources used by the InputHandler.
     * This should be called when the InputHandler is no longer needed.
     */
    fun dispose() {
        // Cancel pending single-tap and clear other state
        pendingTapSingleJob?.cancel()
        pendingTapSingleJob = null
        // Clear other state
        pressedKeys.clear()
    }
}
