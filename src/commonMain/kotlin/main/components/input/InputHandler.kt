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
import main.components.GraphicsSettings
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
import models.canUserEdit
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.MouseEvent
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
    private val onOpenInventory: (() -> Unit)? = null,
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
            
            // Check if the user can edit the world (owner or co-owner)
            return currentWorld?.canUserEdit(currentUser.key) == true
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

    // Multi-touch tracking and suppression to avoid accidental moves after camera gestures
    private var activePointers: Int = 0
    private var multiTouchActive: Boolean = false
    private var preventClickToMoveUntil: Double = 0.0 // performance.now() in ms
    private val gestureSuppressionMs: Double = 500.0 // half a second after multi-touch ends
    
    // Single-finger camera drag detection (to suppress click-to-move on lift)
    private var didRotateCameraThisPointer: Boolean = false
    private val singleFingerCameraSlop: Int = 10
    
    // Store the click point for object dragging
    private var objectClickPoint: Vector3? = null

    // Job for observer mode depth picking
    private var observerModeTargetJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default)

    private var onActivity: (() -> Unit)? = null

    fun setOnActivity(callback: () -> Unit) {
        onActivity = callback
    }

    // Returns true when we should ignore taps/click-to-move (e.g., right after multi-touch)
    private fun isClickToMoveSuppressed(): Boolean {
        return window.performance.now() < preventClickToMoveUntil
    }
    
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
        onActivity?.invoke()
        val event = info.event
        val key = event.key.lowercase()

        // Handle Escape key to cancel object placement mode
        if ((key == "escape" || key == "esc") && info.type == KeyboardEventTypes.KEYUP) {
            if (objectManager?.isCreatePlacementModeActive() == true) {
                objectManager.cancelCreatePlacementMode()
                return
            }
        }
        
        // Handle movement keys (WASD, arrow keys, and shift)
        if (key in listOf("w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright", "shift")) {
            when (info.type) {
                KeyboardEventTypes.KEYDOWN -> {
                    // Add key to pressed keys set
                    pressedKeys.add(key)
                    
                    // Cancel any click-to-move in progress (unless it's just shift)
                    if (key != "shift" && pressedKeys.isNotEmpty()) {
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
    
    private fun updateObserverTarget() {
        if (!GraphicsSettings.isObserverModeEnabled()) return
        if (isObjectDragging || draggingObjectId != null || objectManager?.getSelectedObject() != null) return

        observerModeTargetJob?.cancel()
        observerModeTargetJob = scope.launch {
            val worldPoint = DepthPicking.getVisibleWorldPointAt(
                scene = scene,
                x = scene.pointerX,
                y = scene.pointerY
            )
            if (worldPoint != null && isPointWithinReach(worldPoint, cameraController.getCamera().position, factor = 1f)) {
                cameraController.updateCameraTarget(
                    position = worldPoint,
                )
            }
        }
    }

    /**
     * Handles pointer events.
     *
     * @param info The pointer event information
     */
    private fun handlePointerEvent(info: PointerInfo) {
        onActivity?.invoke()

        // Intercept events for object placement mode
        if (objectManager?.isCreatePlacementModeActive() == true) {
            val handled = when (info.type) {
                PointerEventTypes.POINTERMOVE -> {
                    objectManager.updateCreatePlacementPreview(scene.pointerX, scene.pointerY)
                    true
                }
                PointerEventTypes.POINTERDOWN -> {
                    // Only confirm on left-click on the canvas
                    val mouseEvent = info.event as? MouseEvent
                    if (mouseEvent != null && mouseEvent.button.toInt() == 0 && !mouseEvent.ctrlKey && !mouseEvent.shiftKey && !mouseEvent.altKey) {
                        objectManager.confirmCreatePlacement(scene.pointerX, scene.pointerY)
                    }
                    true
                }
                PointerEventTypes.POINTERUP,
                PointerEventTypes.POINTERTAP,
                PointerEventTypes.POINTERDOUBLETAP -> true
                else -> false
            }
            if (handled) return
        }

        // Check if an object is selected or being dragged
        var isObjectSelected = objectManager?.getSelectedObject() != null
        val isObjectBeingDragged = isObjectDragging || draggingObjectId != null

        console.log("[POINTER] ${info.type} - isObjectSelected: $isObjectSelected, isObjectBeingDragged: $isObjectBeingDragged")

        // Track multi-touch pointer counts to suppress accidental moves after pinch/zoom
        run {
            val now = window.performance.now()
            when (info.type) {
                PointerEventTypes.POINTERDOWN -> {
                    val prev = activePointers
                    activePointers = (activePointers + 1).coerceAtLeast(1)
                    if (prev < 2 && activePointers >= 2) {
                        // Multi-touch just started
                        multiTouchActive = true
                        // Cancel any pending tap actions and movement
                        pendingTapSingleJob?.cancel()
                        pendingTapSingleJob = null
                        playerController.stopMovement()
                        console.log("[POINTER] Multi-touch started; suppressing tap/move until gesture ends")
                    }
                }
                PointerEventTypes.POINTERUP -> {
                    val prev = activePointers
                    activePointers = (activePointers - 1).coerceAtLeast(0)
                    if (prev >= 2 && activePointers < 2) {
                        // Multi-touch just ended
                        multiTouchActive = false
                        preventClickToMoveUntil = now + gestureSuppressionMs
                        // Cancel any pending tap actions
                        pendingTapSingleJob?.cancel()
                        pendingTapSingleJob = null
                        console.log("[POINTER] Multi-touch ended; suppressing taps/move for ${gestureSuppressionMs}ms")
                    }
                    // If all pointers lifted, reset basic dragging flag
                    if (activePointers == 0) {
                        isPointerDown = false
                    }
                }
            }
        }

        // Handle Babylon tap/double-tap events explicitly (touch & click)
        if (info.type == PointerEventTypes.POINTERDOUBLETAP && objectManager != null) {
            // Ignore double-tap actions right after a multi-touch camera gesture
            if (isClickToMoveSuppressed()) {
                console.log("[POINTER] Double-tap suppressed due to recent multi-touch gesture")
                return
            }
            // Double-tap cancels any pending single-tap action
            pendingTapSingleJob?.cancel()
            pendingTapSingleJob = null
            // First, check if we double-tapped a player: show dialog
            run {
                val ray = scene.createPickingRay(
                    x = scene.pointerX,
                    y = scene.pointerY,
                    world = Matrix.Identity(),
                    camera = cameraController.getCamera(),
                )

                // Local player check: open inventory
                val localPlayerMeshes = playerController.getPlayerMeshes()
                for (m in localPlayerMeshes) {
                    val hit = ray.intersectsMesh(m)
                    if (hit.pickedPoint != null) {
                        openInventory()
                        return
                    }
                }

                // Other players check
                val otherPlayersController = SceneManager.getOtherPlayersController()
                if (otherPlayersController != null) {
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
            // Ignore tap actions right after a multi-touch camera gesture
            if (isClickToMoveSuppressed()) {
                console.log("[POINTER] Tap suppressed due to recent multi-touch gesture")
                // Cancel any pending single-tap
                pendingTapSingleJob?.cancel()
                pendingTapSingleJob = null
                return
            }
            // First, check if we tapped a player: show dialog and do not move
            run {
                val ray = scene.createPickingRay(
                    x = scene.pointerX,
                    y = scene.pointerY,
                    world = Matrix.Identity(),
                    camera = cameraController.getCamera(),
                )

                // Local player check: open inventory
                val localPlayerMeshes = playerController.getPlayerMeshes()
                for (m in localPlayerMeshes) {
                    val hit = ray.intersectsMesh(m)
                    if (hit.pickedPoint != null) {
                        // Any explicit player interaction should cancel pending single-object tap
                        pendingTapSingleJob?.cancel()
                        pendingTapSingleJob = null
                        openInventory()
                        return
                    }
                }

                // Other players check
                val otherPlayersController = SceneManager.getOtherPlayersController()
                if (otherPlayersController != null) {
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
                // Drag to move camera
                if (info.event.ctrlKey) {
                    cameraController.stopAnimating()
                }

                val deltaX = abs(scene.pointerX - pointerStartX)
                val deltaY = abs(scene.pointerY - pointerStartY)

                // Detect single-finger camera rotation with a small slop to avoid accidental moves on lift
                // Only when not manipulating objects and not multi-touching
                if (!isObjectBeingDragged && !isObjectSelected && activePointers <= 1) {
                    if (!didRotateCameraThisPointer && (deltaX > singleFingerCameraSlop || deltaY > singleFingerCameraSlop)) {
                        didRotateCameraThisPointer = true
                        playerController.stopMovement()
                    }
                }

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

        if (!isObjectBeingDragged && !isObjectSelected) {
            val isNavigationStart = info.type == PointerEventTypes.POINTERDOWN
            val isWheel = info.type == PointerEventTypes.POINTERWHEEL

            // If moving the camera with the mouse (zoom), stop player movement
            if (isNavigationStart || isWheel) {
                cameraController.stopAnimating()

                // In observer mode, set the camera target to the point under the cursor when starting to rotate
                // We skip this on wheel events because zoomToMouseLocation handles it automatically
                if (GraphicsSettings.isObserverModeEnabled() && isNavigationStart) {
                    updateObserverTarget()
                }
            }
        }

        // Handle object selection on pointer down
        if (info.type == PointerEventTypes.POINTERDOWN && objectManager != null) {
            // Track pointer down state and position
            isPointerDown = true
            pointerStartX = scene.pointerX
            pointerStartY = scene.pointerY
            isDragging = false
            didRotateCameraThisPointer = false

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

            // Check if we clicked on a player (local or other)
            run {
                val ray = pick.ray
                
                // Local player check
                val localPlayerMeshes = playerController.getPlayerMeshes()
                for (m in localPlayerMeshes) {
                    val hit = ray.intersectsMesh(m)
                    if (hit.pickedPoint != null) {
                        objectManager?.deselectCurrentObject()
                        return
                    }
                }
                
                // Other players check
                val otherPlayersController = SceneManager.getOtherPlayersController()
                if (otherPlayersController != null) {
                    val playerMeshes = otherPlayersController.getPlayerMeshes()
                    for (mesh in playerMeshes) {
                        val hit = ray.intersectsMesh(mesh)
                        if (hit.pickedPoint != null) {
                            objectManager?.deselectCurrentObject()
                            return
                        }
                    }
                }
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

            // We may have changed selection state above; recompute before deciding camera handling below
            isObjectSelected = objectManager.getSelectedObject() != null
        }

        if (info.type == PointerEventTypes.POINTERUP) {
            // If it was a click (not a drag) and no object is selected, handle movement
            if (!isDragging && !isObjectSelected) {
                // Suppress click-to-move if a multi-touch gesture just occurred or is ongoing
                if (isClickToMoveSuppressed() || multiTouchActive || activePointers > 0 || didRotateCameraThisPointer) {
                    console.log("[POINTER] Click-to-move suppressed on POINTERUP due to gesture context (multi-touch or single-finger camera drag)")
                } else {
                    handleClickToMove()
                }
            }

            // Reset pointer state if no pointers remain
            if (activePointers == 0) {
                isPointerDown = false
                isDragging = false
                didRotateCameraThisPointer = false
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
                                    val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint)
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
                                    val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint)
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
        var isHoveringPlayer = false

        // Local player check
        val localPlayerMeshes = playerController.getPlayerMeshes()
        for (m in localPlayerMeshes) {
            val hit = ray.intersectsMesh(m)
            if (hit.pickedPoint != null) {
                document.body?.style?.cursor = "pointer"
                isHoveringPlayer = true
                isHoveringOverPlayer = true
                break
            }
        }

        if (!isHoveringPlayer) {
            val otherPlayersController = SceneManager.getOtherPlayersController()
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

        // 1) Player-click detection: do not move if we clicked a player (local or other)

        // Local player check: open inventory
        val localPlayerMeshes = playerController.getPlayerMeshes()
        for (m in localPlayerMeshes) {
            val hit = ray.intersectsMesh(m)
            if (hit.pickedPoint != null) {
                openInventory()
                return
            }
        }

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

                if (depthPoint != null && isPointWithinReach(depthPoint)) {
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

            if (closestPoint != null && isPointWithinReach(closestPoint)) {
                playerController.setTargetPosition(closestPoint)
            }
        }
    }

    private fun isPointWithinReach(
        point: Vector3,
        playerPos: Vector3 = playerController.getCollider()!!.position,
        factor: Float = .5f
    ): Boolean {
        val distance = Vector3.Distance(playerPos, point)
        val maxDistance = (cameraController.getCamera().maxZ - cameraController.getCamera().minZ) * factor
        return distance <= maxDistance
    }
    
    private fun openInventory() {
        console.log("[DEBUG_LOG] Opening inventory (local player clicked)")
        onOpenInventory?.invoke()
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
     * Checks if the sprint key (SHIFT) is currently pressed.
     * 
     * @return True if sprinting, false otherwise
     */
    fun isSprinting(): Boolean {
        return "shift" in pressedKeys
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
        // Reset multi-touch suppression state
        activePointers = 0
        multiTouchActive = false
        preventClickToMoveUntil = 0.0
        isPointerDown = false
        isDragging = false
    }
}
