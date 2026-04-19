package main.components.objects

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Color3
import lib.CreateSphereOptions
import lib.SelectionOutlineLayer
import lib.MeshBuilder
import lib.Node
import lib.PBRMaterial
import lib.Scene
import lib.TransformNode
import lib.Vector3
import main.components.GraphicsSettings
import main.api.InventoryApi
import main.api.ItemApi
import main.api.ObjectApi
import main.api.WebSocketApi
import main.components.camera.CameraController
import main.components.inventory.InventoryState
import main.components.shadow.ShadowManager
import main.components.world.CharacterDirection
import main.components.world.DepthPicking
import main.components.world.InventoryObject
import main.components.world.PlayerActivity
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import main.components.world.isSkybox
import models.Item
import models.Object
import models.ObjectKind
import models.Position
import models.WebSocketAction
import models.ObjectTypingData
import models.QuestStep

/**
 * Implementation of the ObjectManager interface for managing objects in the 3D scene
 * 
 * @param scene The BabylonJS scene
 * @param cameraController The camera controller
 */
class SceneObjectManager(
    private val scene: Scene,
    private val cameraController: CameraController,
    private var worldObjectManager: WorldObjectManager,
) {
    
    // Currently selected object
    private var selectedObject: String? = null
    
    // Map of object IDs to their meshes
    private val objectMeshes = mutableMapOf<String, Node>()
    
    // Map of object IDs to their status text handles
    private val objectStatusTextHandles = mutableMapOf<String, WorldObjectHandle>()
    private val objectStatusOffsets = mutableMapOf<String, Float>()
    
    // Performance caches
    private val cachedMeshes = mutableMapOf<String, List<AbstractMesh>>()
    private val cachedHighestY = mutableMapOf<String, Float>()

    // Cache last known status text to restore after typing ends
    private val objectStatusTexts = mutableMapOf<String, String>()
    
    // Tracks current displayed status text (including prefixes)
    private val objectDisplayedStatusTexts = mutableMapOf<String, String>()

    // Typing indicator HUD per object ("💬" bubble) and its safety timeouts
    private val objectTypingHandles = mutableMapOf<String, WorldObjectHandle>()
    private val objectTypingTimeouts = mutableMapOf<String, Int>()
    
    // Tracks last known (file, collection) used to render each object, to detect when re-creation is needed
    private val objectModelSources = mutableMapOf<String, Pair<String, String>>()

    // Tracks object kinds for quick lookup (World vs Inventory)
    private val objectKinds = mutableMapOf<String, ObjectKind>()
    private val activeQuestIndicators = mutableMapOf<String, QuestStep>()
    
    // Tracks vertical offset for each object (applied during dragging)
    private val objectVerticalOffsets = mutableMapOf<String, Float>()
    
    // Currently dragging object
    private var draggingObjectId: String? = null
    
    // Drag state
    private var isDragging = false
    private var dragStartPosition: Vector3? = null
    private var dragClickOffset: Vector3? = null
    
    // Track hovered object
    private var hoveredObjectId: String? = null
    
    // Track the closest interactable object (within INTERACTION_RADIUS_SQ)
    private var interactableObjectId: String? = null
    
    // Track objects that are within proximity radius
    private val nearbyObjectIds = mutableSetOf<String>()

    // Spatial hash for efficient proximity checks (10x10 unit cells)
    private val spatialHash = SpatialHash(10f)

    // Add a selection outline layer for the outline effect
    private val selectionOutlineLayer = SelectionOutlineLayer("objectSelectionOutlineLayer", scene)

    private var shadowManager: ShadowManager? = null
    
    // Asset manager for 3D models
    private val assetManager = ObjectAssetManager(scene)
    
    init {
        // Configure the selection outline layer
        selectionOutlineLayer.outlineThickness = 4.0f
        selectionOutlineLayer.outlineColor = Color3.White()

        GraphicsSettings.addObserver {
            // Update outline thickness based on graphics mode if needed
            if (GraphicsSettings.isLowGraphicsModeEnabled()) {
                selectionOutlineLayer.outlineThickness = 2.0f
            } else {
                selectionOutlineLayer.outlineThickness = 4.0f
            }
        }
    }

    fun setShadowManager(shadowManager: ShadowManager) {
        this.shadowManager = shadowManager
    }

    /**
     * Updates the proximity status of an object.
     * When nearby, always show its status (without highlight).
     *
     * @param objectId The ID of the object
     * @param isNearby Whether the player is nearby
     */
    fun setObjectNearby(objectId: String, isNearby: Boolean) {
        if (isNearby) {
            nearbyObjectIds.add(objectId)
            // Reveal status if it's nearby
            animateStatusText(objectId, true)
        } else {
            nearbyObjectIds.remove(objectId)
            // Hide status only if it's no longer nearby AND not hovered AND not interactable AND not a quest indicator
            if (hoveredObjectId != objectId && interactableObjectId != objectId && !activeQuestIndicators.containsKey(objectId)) {
                animateStatusText(objectId, false)
            }
        }
    }

    /**
     * Sets the current interactable object (the closest one within interaction radius).
     * This object will be highlighted and its status text will be prefixed with "🆇 ".
     *
     * @param objectId The ID of the object, or null to clear
     */
    fun setObjectInteractable(objectId: String?) {
        if (interactableObjectId == objectId) return

        val previousId = interactableObjectId
        interactableObjectId = objectId

        // 1) Update previous interactable object
        if (previousId != null) {
            // Restore status text (removes "🆇 " prefix)
            val originalStatus = objectStatusTexts[previousId] ?: ""
            val node = objectMeshes[previousId]
            if (node != null) {
                handleObjectStatusText(previousId, originalStatus, node)
            }

            // Hide status if neither hovered nor nearby nor interactable nor quest indicator anymore
            if (previousId != hoveredObjectId && !nearbyObjectIds.contains(previousId) && !activeQuestIndicators.containsKey(previousId)) {
                animateStatusText(previousId, false)
            }
        }

        // 2) Update new interactable object
        if (objectId != null) {
            val node = objectMeshes[objectId]
            if (node != null) {
                // Update status text (adds "🆇 " prefix)
                val status = objectStatusTexts[objectId] ?: ""
                handleObjectStatusText(objectId, status, node)

                // Ensure visibility
                animateStatusText(objectId, true)
            }
        }

        // Update selection outline
        updateSelectionOutline()
    }

    /**
     * Gets the current interactable object ID.
     */
    fun getInteractableObjectId(): String? = interactableObjectId
    
    /**
     * Highlights an object with a white outline when hovered
     * 
     * @param objectId The ID of the object to highlight
     * @return true if the object was highlighted successfully, false otherwise
     */
    fun highlightObject(objectId: String): Boolean {
        // If already hovered, no need to re-highlight
        if (hoveredObjectId == objectId) return true

        // Remove highlight from previously hovered object
        unhighlightObject()
        
        // Find the object node
        val node = objectMeshes[objectId]
        if (node != null) {
            try {
                // Get all meshes from the node (including the node itself if it's a mesh)
                val allMeshes = node.getChildMeshes(false).toMutableList()
                if (node is AbstractMesh) {
                    allMeshes.add(node)
                }

                // Store the hovered object ID
                hoveredObjectId = objectId

                // Update selection outline
                updateSelectionOutline()
                
                // Show status text for the hovered object
                animateStatusText(objectId, true)
                
                // Change cursor to grab
                document.body?.style?.cursor = "grab"

                return true
            } catch (error: Throwable) {
                console.error("[DEBUG_HIGHLIGHT] Error highlighting object", error)
                return false
            }
        }
        return false
    }
    
    /**
     * Removes highlight from the currently hovered object
     */
    fun unhighlightObject() {
        hoveredObjectId?.let { objectId ->

            val node = objectMeshes[objectId]
            if (node != null) {
                try {
                    // Get all meshes from the node (including the node itself if it's a mesh)
                    val allMeshes = node.getChildMeshes(false).toMutableList()
                    if (node is AbstractMesh) {
                        allMeshes.add(node)
                    }

                    // Reset cursor
                    document.body?.style?.cursor = "default"

                    // Hide status text for the unhovered object, unless still nearby, interactable, or a quest indicator
                    if (!nearbyObjectIds.contains(objectId) && interactableObjectId != objectId && !activeQuestIndicators.containsKey(objectId)) {
                        animateStatusText(objectId, false)
                    }

                } catch (error: Throwable) {
                    console.error("[DEBUG_HIGHLIGHT] Error unhighlighting object", error)
                }
            } else {
            }
            hoveredObjectId = null
            updateSelectionOutline()
        }
    }
    
    /**
     * Updates the selection outline layer by clearing it and re-adding all meshes for the currently
     * hovered and interactable objects. This is a workaround since SelectionOutlineLayer does not
     * have a public removeSelection method.
     */
    private fun updateSelectionOutline() {
        selectionOutlineLayer.clearSelection()

        // 1. Add interactable object highlight
        interactableObjectId?.let { id ->
            val node = objectMeshes[id]
            if (node != null) {
                node.getChildMeshes(false).forEach { mesh ->
                    selectionOutlineLayer.addSelection(mesh)
                }
                if (node is AbstractMesh) selectionOutlineLayer.addSelection(node)
            }
        }

        // 2. Add hovered object highlight
        hoveredObjectId?.let { id ->
            // Avoid re-adding if it's the same as interactable
            if (id != interactableObjectId) {
                val node = objectMeshes[id]
                if (node != null) {
                    node.getChildMeshes(false).forEach { mesh ->
                        selectionOutlineLayer.addSelection(mesh)
                    }
                    if (node is AbstractMesh) selectionOutlineLayer.addSelection(node)
                }
            }
        }
    }
    
    /**
     * Selects an object in the scene and highlights it
     * 
     * @param objectId The ID of the object to select
     * @return true if the object was selected successfully, false otherwise
     */
    fun selectObject(objectId: String): Boolean {
        // Deselect the currently selected object if any
        deselectCurrentObject()
        
        // Find the object node
        val node = objectMeshes[objectId]
        if (node != null) {
            try {
                // Store the selected object ID
                selectedObject = objectId
                
                console.log("[DEBUG_SELECT] Object selected: $objectId")
                onSuccess?.invoke("Object selected")
                return true
            } catch (error: Throwable) {
                console.error("[DEBUG_SELECT] Error selecting object", error)
                onError?.invoke("Failed to select object: ${error.message ?: "Unknown error"}")
                return false
            }
        } else {
            console.warn("[DEBUG_SELECT] Object node not found: $objectId")
            onError?.invoke("Object not found in scene")
            return false
        }
    }
    
    /**
     * Deselects the currently selected object
     */
    fun deselectCurrentObject() {
        selectedObject?.let { objectId ->
            selectedObject = null
        }
    }

    /**
     * Locates an object in the scene by moving the camera to focus on it
     * 
     * @param objectId The ID of the object to locate
     * @return true if the object was located successfully, false otherwise
     */
    fun locateObject(objectId: String): Boolean {
        // Find the object node
        val node = objectMeshes[objectId]
        if (node != null) {
            try {
                // Select the object to highlight it
                selectObject(objectId)
                
                // Move the camera to focus on the object
                cameraController.focusOnPosition(node.position)
                
                console.log("Camera focused on object: $objectId")
                onSuccess?.invoke("Located object")
                return true
            } catch (error: Throwable) {
                console.error("Error locating object", error)
                onError?.invoke("Failed to locate object: ${error.message ?: "Unknown error"}")
                return false
            }
        } else {
            console.warn("Object node not found: $objectId")
            onError?.invoke("Object not found in scene")
            return false
        }
    }

    /**
     * Moves the player character to an object's location with a random offset.
     * Also focuses the camera on the object.
     * 
     * @param objectId The ID of the object to move to
     * @param playerController The player controller to handle movement
     */
    fun movePlayerToObject(objectId: String, playerController: main.components.player.PlayerController) {
        val node = objectMeshes[objectId] ?: return
        val pos = node.getAbsolutePosition().clone()
        
        // Random offset between 0.5 and 1.5 units away from the object center
        val angle = Random.nextDouble(0.0, 2.0 * PI)
        val radius = Random.nextDouble(0.5, 1.5)
        pos.x += (radius * cos(angle)).toFloat()
        pos.z += (radius * sin(angle)).toFloat()
        
        // Teleport player and focus camera
        playerController.teleportTo(pos)
        cameraController.focusOnPosition(node.getAbsolutePosition())
        
        // Highlight the object
        selectObject(objectId)
    }
    
    /**
     * Removes an object from the scene
     * 
     * @param objectId The ID of the object to remove
     */
    fun removeObject(objectId: String) {
        console.log("[DEBUG_REMOVE] Removing object with ID: $objectId from scene")
        
        try {
            // If this object is currently hovered, unhighlight it first
            if (hoveredObjectId == objectId) {
                console.log("[DEBUG_REMOVE] Object is hovered, unhighlighting before removal: $objectId")
                unhighlightObject()
            }
            
            // If this object is the current interactable, clear it
            if (interactableObjectId == objectId) {
                interactableObjectId = null
                updateSelectionOutline()
            }
            
            // If this object is selected, deselect it
            if (selectedObject == objectId) {
                console.log("[DEBUG_REMOVE] Object is selected, deselecting: $objectId")
                deselectCurrentObject()
            }
            
            // Remove any status text for this object
            val statusTextHandle = objectStatusTextHandles[objectId]
            if (statusTextHandle != null) {
                console.log("[DEBUG_REMOVE] Removing status text for object: $objectId")
                statusTextHandle.remove()
                objectStatusTextHandles.remove(objectId)
                objectStatusTexts.remove(objectId)
                objectDisplayedStatusTexts.remove(objectId)
                objectStatusOffsets.remove(objectId)
                console.log("[DEBUG_REMOVE] Successfully removed status text for object: $objectId")
            }

            // Remove typing indicator if present
            objectTypingTimeouts.remove(objectId)?.let { window.clearTimeout(it) }
            objectTypingHandles.remove(objectId)?.remove()
            
            // Find and remove the object from the scene
            val node = objectMeshes[objectId]
            if (node != null) {
                console.log("[DEBUG_REMOVE] Removing node for object: $objectId")
                
                // Remove from shadow manager
                node.getChildMeshes().forEach { mesh ->
                    shadowManager?.removeShadowCaster(mesh)
                }

                (node as? TransformNode)?.dispose()
                objectMeshes.remove(objectId)
                spatialHash.remove(objectId)
                cachedMeshes.remove(objectId)
                cachedHighestY.remove(objectId)
                console.log("[DEBUG_REMOVE] Successfully removed node for object: $objectId")
            } else {
                console.log("[DEBUG_REMOVE] No node found for object: $objectId")
            }
            
            // Cleanup model source tracking
            if (objectModelSources.remove(objectId) != null) {
                console.log("[DEBUG_REMOVE] Removed model source tracking for object: $objectId")
            }
            // Cleanup object kind tracking
            if (objectKinds.remove(objectId) != null) {
                console.log("[DEBUG_REMOVE] Removed kind tracking for object: $objectId")
            }
        } catch (e: Throwable) {
            console.error("[DEBUG_REMOVE] Error removing object: $objectId", e)
        }
        
        // Note: The actual HTTP deletion and WebSocket notification is handled in ObjectsContent.kt
    }
    
    /**
     * Updates the position of an object in the scene and persists the change
     * 
     * @param objectId The ID of the object to move
     * @param newPosition The new position for the object
     */
    fun moveObject(objectId: String, newPosition: Position) {
        console.log("Moving object $objectId to position (${newPosition.x}, ${newPosition.y}, ${newPosition.z})")
        
        // Update spatial hash
        spatialHash.update(objectId, Vector3(newPosition.x, newPosition.y, newPosition.z))

        // Update the object's position in the scene
        // In a real implementation, we would find the mesh by ID and update its position
        
        // Persist the position change
        coroutineScope.launch {
            try {
                // First, get the current object data
                val obj = ObjectApi.getObject(objectId)
                
                // Update the position
                val updatedObj = obj.copy(position = newPosition)
                
                // Save the updated object
                val savedObj = ObjectApi.updateObject(objectId, updatedObj)
                
                console.log("Object position updated and persisted: $objectId")
                
                // Provide success feedback
                onSuccess?.invoke("Object '${savedObj.title}' moved successfully")
            } catch (error: Throwable) {
                console.error("Error updating object position", error)
                
                // Provide error feedback
                onError?.invoke("Failed to move object: ${error.message ?: "Unknown error"}")
            }
        }
    }
    
    // Create a coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // Constants
    private val Z_FIGHTING_BUFFER = 0.005f
    
    // JSON parser
    private val json = utils.json
    
    // Callback for error handling and user feedback
    var onError: ((String) -> Unit)? = null
    
    // Callback for success feedback
    var onSuccess: ((String) -> Unit)? = null
    
    /**
     * Handles dropping an inventory item into the scene
     * 
     * @param x The x coordinate of the drop
     * @param y The y coordinate of the drop
     * @param data The serialized item data
     * @param worldId The ID of the current world
     */
    fun handleInventoryItemDrop(x: Int, y: Int, data: String, worldId: String, quantityOverride: Int? = null) {
        console.log("[DEBUG_DROP] Inventory item dropped at ($x, $y) in world $worldId")
        
        try {
            // Parse the drag payload (InventoryDragPayload)
            val payload = json.decodeFromString(main.components.inventory.InventoryDragPayload.serializer(), data)
            console.log("[DEBUG_DROP] Parsed inventory drag payload: itemKey=${payload.itemKey}, inventoryItemKey=${payload.inventoryItemKey}, quantity=${payload.quantity}")
            
            // Determine drop quantity without using window.prompt
            val maxQuantity = if (payload.quantity > 0) payload.quantity else 1
            val dropQuantity = quantityOverride?.coerceIn(1, maxQuantity) ?: 1
            
            

            // Persist the object placement via unified transfer endpoint
            coroutineScope.launch {
                try {
                    // Calculate the 3D position from the 2D screen coordinates (depth-first)
                    console.log("[DEBUG_DROP] Calculating 3D position from screen coordinates ($x, $y)")
                    val position = calculatePositionFromScreenCoordinates(x, y)
                    console.log("[DEBUG_DROP] Calculated position: (${position.x}, ${position.y}, ${position.z})")

                    // Fetch the item definition
                    val item = ItemApi.getItemById(payload.itemKey)

                    val savedObj = InventoryApi.transferDrop(
                        InventoryApi.TransferDropRequest(
                            itemKey = payload.itemKey,
                            quantity = dropQuantity,
                            worldId = worldId,
                            position = position,
                            title = item.title.ifBlank { null },
                        )
                    )

                    // Server will broadcast ADD_OBJECT; rely on broadcast to render

                    // Refresh inventory state to reflect server-authoritative changes
                    InventoryState.refreshInventory(
                        coroutineScope = coroutineScope,
                    )

                    console.log("Item successfully placed and persisted: ${savedObj.key}")

                    // Provide success feedback
                    onSuccess?.invoke("Dropped '${item.title}' x$dropQuantity")
                } catch (error: Throwable) {
                    console.error("Error persisting item placement", error)
                    
                    // Provide error feedback
                    onError?.invoke("Failed to drop item: ${error.message ?: "Unknown error"}")
                }
            }
        } catch (error: Throwable) {
            console.error("Error parsing inventory drag payload", error)
            
            // Provide error feedback
            onError?.invoke("Failed to parse inventory data: ${error.message ?: "Unknown error"}")
        }
    }
    
    /**
     * Creates a 3D representation of an inventory item in the scene
     * 
     * @param obj The object containing the item data
     * @param item The item to create
     * @param overrideStatus Optional status to display instead of obj.status
     */
    private fun createInventoryItemInScene(obj: Object, item: Item, overrideStatus: String? = null) {
        console.log("[DEBUG_ITEM] Creating 3D representation for item: ${obj.key}")
        
        try {
            // Create position vector from the object properties
            val position = Vector3(obj.position.x, obj.position.y, obj.position.z)
            
            // Create the inventory object
            val inventoryObject = InventoryObject(
                item = item,
                initialPosition = position,
                objectId = obj.key
            )
            
            // Render the object in the scene
            val node = inventoryObject.render(scene)
            if (node != null) {
                // Start animations
                startAnimations(node)
                    
                // Store the object in our map for future reference
                objectMeshes[obj.key] = node
                spatialHash.update(obj.key, node.position)
                // Track as inventory-based source to stabilize model-change checks
                objectModelSources[obj.key] = Pair("", "")
                // Track object kind
                objectKinds[obj.key] = ObjectKind.Inventory

                // Handle status text
                handleObjectStatusText(obj.key, overrideStatus ?: obj.status, node)

                node.getChildMeshes().also { meshes ->
                    console.log("[DEBUG_ITEM] Adding shadows: ${meshes}")
                    // Add all meshes as shadow casters
                    shadowManager?.addShadowCasters(meshes)

                    // Configure all meshes to receive shadows
                    shadowManager?.configureShadowReceivers(meshes)
                }

                console.log("[DEBUG_ITEM] Successfully created inventory item in scene: ${obj.key}")
            } else {
                console.error("[DEBUG_ITEM] Failed to render inventory item: ${obj.key}")
            }
        } catch (error: Throwable) {
            console.error("[DEBUG_ITEM] Error creating inventory item", error)
        }
    }
    
    /**
     * Handles dropping an object into the scene
     * 
     * @param x The x coordinate of the drop
     * @param y The y coordinate of the drop
     * @param data The serialized object data
     * @param worldId The ID of the current world
     */
    fun handleObjectDrop(x: Int, y: Int, data: String, worldId: String) {
        console.log("[DEBUG_DROP] Object dropped at ($x, $y) in world $worldId")
        
        try {
            // Parse the object data
            val obj = json.decodeFromString(Object.serializer(), data)
            console.log("[DEBUG_DROP] Parsed object data: title=${obj.title}, key=${obj.key}")
            
            // Persist the object placement
            coroutineScope.launch {
                // Calculate the 3D position from the 2D screen coordinates (depth-first)
                console.log("[DEBUG_DROP] Calculating 3D position from screen coordinates ($x, $y)")
                val position = calculatePositionFromScreenCoordinates(x, y)
                console.log("[DEBUG_DROP] Calculated position: (${position.x}, ${position.y}, ${position.z})")

                // Update the object with the new position and world ID
                val updatedObj = obj.copy(
                    position = Position(
                        x = position.x,
                        y = position.y + obj.verticalOffset,
                        z = position.z
                    ),
                    worldId = worldId
                )
                console.log("[DEBUG_DROP] Updated object with new position: (${updatedObj.position.x}, ${updatedObj.position.y}, ${updatedObj.position.z}) (offset: ${obj.verticalOffset})")
                
                // Persist the object placement
                try {
                    // If the object has a key, update it; otherwise create a new one
                    val savedObj = if (obj.key.isNotEmpty()) {
                        ObjectApi.updateObject(obj.key, updatedObj)
                    } else {
                        ObjectApi.createObject(updatedObj)
                    }
                    
                    // Server will broadcast ADD_OBJECT; no client self-broadcast
                    
                    // Create a 3D representation of the object in the scene
                    createObjectInScene(savedObj)
                    
                    console.log("Object successfully placed and persisted: ${savedObj.key}")
                    
                    // Provide success feedback
                    onSuccess?.invoke("Object '${savedObj.title}' placed successfully")
                } catch (error: Throwable) {
                    console.error("Error persisting object placement", error)
                    
                    // Provide error feedback
                    onError?.invoke("Failed to place object: ${error.message ?: "Unknown error"}")
                }
            }
        } catch (error: Throwable) {
            console.error("Error parsing object data", error)
            
            // Provide error feedback
            onError?.invoke("Failed to parse object data: ${error.message ?: "Unknown error"}")
        }
    }
    
    /**
     * Calculates a 3D position from 2D screen coordinates
     * 
     * @param x The x coordinate on the screen
     * @param y The y coordinate on the screen
     * @return The calculated 3D position
     */
    private suspend fun calculatePositionFromScreenCoordinates(clientX: Int, clientY: Int): Position {
        console.log("[DEBUG_POSITION] calculatePositionFromScreenCoordinates called with clientX=$clientX, clientY=$clientY")

        // Depth-first: try to get the visible world point at the drop pixel
        val depthPoint = try {
            DepthPicking.getVisibleWorldPointAt(
                scene = scene,
                x = clientX,
                y = clientY,
            )
        } catch (_: Throwable) { null }

        var basePoint: Vector3? = depthPoint

        if (basePoint == null) {
            // Fallback: original ray-based closest intersection with world meshes
            val ray = scene.createPickingRay(
                x = clientX,
                y = clientY,
                world = lib.Matrix.Identity(),
                camera = cameraController.getCamera()
            )
            var closestDistance = Float.MAX_VALUE
            var closestPoint: Vector3? = null

            val worldMeshes = getWorldMeshes()
            val playerController = main.components.SceneManager.getPlayerController()
            val player = playerController?.getCollider()
            val moveCircle = playerController?.getMoveCircle()

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

            basePoint = closestPoint
            if (basePoint == null) {
                // Ultimate fallback: player's current position or zero
                basePoint = playerController?.getPlayerPosition() ?: Vector3.Zero()
            }
        }

        // Slight Y offset to avoid z-fighting with ground
        val finalPosition = Vector3(
            basePoint.x,
            basePoint.y + Z_FIGHTING_BUFFER,
            basePoint.z
        )
        return Position(
            x = finalPosition.x,
            y = finalPosition.y,
            z = finalPosition.z
        )
    }
    
    /**
     * Creates a 3D representation of an object in the scene
     * 
     * @param obj The object to create
     */
    private fun createObjectPreferInventory(obj: Object) {
        // Track the kind for this object
        objectKinds[obj.key] = obj.kind
        // Track vertical offset
        objectVerticalOffsets[obj.key] = obj.verticalOffset

        if (obj.kind != ObjectKind.Inventory) {
            // Render as a regular world object (file/collection or fallback sphere)
            createObjectInScene(obj)
            return
        }

        // Inventory object: render from its inventory definition
        coroutineScope.launch {
            try {
                val inv = InventoryApi.getObjectInventory(obj.key)
                val items = InventoryApi.getInventoryItemsByIds(inv.itemOrder)
                val first = items.firstOrNull()
                if (first != null) {
                    // Load item definition and render as inventory object
                    val itemDef = ItemApi.getItemById(first.item)
                    
                    // Show name and quantity in the status
                    val inventoryStatus = if (first.quantity > 1) {
                        "${itemDef.title} x${first.quantity}"
                    } else {
                        itemDef.title
                    }
                    
                    // Use inventory status if current status is blank
                    val finalStatus = if (obj.status.isBlank()) inventoryStatus else obj.status
                    
                    createInventoryItemInScene(obj, itemDef, finalStatus)
                } else {
                    console.log("[INVENTORY_RENDER] No items for ${obj.key}; skipping render")
                }
            } catch (e: Throwable) {
                console.warn("[INVENTORY_RENDER] Failed inventory render for ${obj.key}: ${e.message}; skipping render")
            }
        }
    }

    private fun createObjectInScene(obj: Object) {
        console.log("[DEBUG_OBJECT] Creating 3D representation for object: ${obj.key}")
        
        // Create a position, rotation, and scale vectors from the object properties
        val position = Vector3(obj.position.x, obj.position.y, obj.position.z)
        val rotation = Vector3(obj.rotation.x, obj.rotation.y, obj.rotation.z)
        val scale = Vector3(obj.scale.x, obj.scale.y, obj.scale.z)

        fun createFallbackSphereForObject() {
            // Create a permanent fallback sphere for objects without file and collection
            val fallbackSphere = createFallbackSphere(obj.key, position, rotation, scale)

            // Store the fallback sphere in our map for future reference
            console.log("[DEBUG_OBJECT] Storing fallback sphere in objectMeshes map with key: ${obj.key}")
            objectMeshes[obj.key] = fallbackSphere
            spatialHash.update(obj.key, fallbackSphere.position)

            // Track model source used for this object (may be blank strings)
            objectModelSources[obj.key] = Pair(obj.file, obj.collection)
            // Track kind as World for regular objects
            objectKinds[obj.key] = ObjectKind.World

            // Start animations
            startAnimations(fallbackSphere)

            // Handle status text
            handleObjectStatusText(obj.key, obj.status, fallbackSphere)
        }

        try {
            // Check if the object has a file and collection specified
            if (obj.file.isNotEmpty() && obj.collection.isNotEmpty()) {
                console.log("[DEBUG_OBJECT] Loading 3D model from file: ${obj.file}, collection: ${obj.collection}")

                // Use the asset manager to load the 3D model
                assetManager.getNode(
                    file = obj.file,
                    collection = obj.collection,
                    onSuccess = { node ->
                        console.log("[DEBUG_OBJECT] Successfully loaded 3D model for object: ${obj.key}")
                        
                        // Set the mesh name to the object key for easier identification
                        node.name = obj.key

                        node.getChildMeshes().also { meshes ->
                            // Add all meshes as shadow casters
                            shadowManager?.addShadowCasters(meshes)

                            // Configure all meshes to receive shadows
                            shadowManager?.configureShadowReceivers(meshes)
                        }
                        
                        // Ensure the node has no parent (position is global to scene)
                        node.parent = null
                        console.log("[DEBUG_OBJECT] Set node parent to null to ensure global positioning")
                        
                        // Set the position, rotation, and scale
                        console.log("[DEBUG_OBJECT] Setting position to: (${position.x}, ${position.y}, ${position.z})")
                        node.position = position
                        
                        // Set rotation
                        console.log("[DEBUG_OBJECT] Setting rotation to: (${rotation.x}, ${rotation.y}, ${rotation.z})")
                        node.rotation = rotation
                        
                        // Set scale
                        console.log("[DEBUG_OBJECT] Setting scale to: (${scale.x}, ${scale.y}, ${scale.z})")
                        node.scaling = scale
                        
                        // Store the mesh in our map for future reference
                        console.log("[DEBUG_OBJECT] Storing mesh in objectMeshes map with key: ${obj.key}")
                        objectMeshes[obj.key] = node
                        spatialHash.update(obj.key, node.position)
                        
                        // Track model source used for this object
                        objectModelSources[obj.key] = Pair(obj.file, obj.collection)
                        // Track kind as World for regular objects
                        objectKinds[obj.key] = ObjectKind.World
                        
                        // Start animations
                        startAnimations(node)
                        
                        // Verify the mesh was added to the map
                        val storedMesh = objectMeshes[obj.key]
                        if (storedMesh != null) {
                            console.log("[DEBUG_OBJECT] Successfully stored mesh in map. Current map size: ${objectMeshes.size}")
                        } else {
                            console.error("[DEBUG_OBJECT] Failed to store mesh in map!")
                        }
                        
                        console.log("[DEBUG_OBJECT] Successfully created 3D object: ${obj.key}")
                        
                        // Handle status text
                        handleObjectStatusText(obj.key, obj.status, node)
                    },
                    onError = { errorMessage ->
                        console.error("[DEBUG_OBJECT] Error loading 3D model: $errorMessage")
                        createFallbackSphereForObject()
                    }
                )
            } else {
                console.log("[DEBUG_OBJECT] No file or collection specified, creating fallback sphere")
                createFallbackSphereForObject()
            }
        } catch (error: Throwable) {
            console.error("[DEBUG_OBJECT] Error creating 3D object", error)
            onError?.invoke("Failed to create 3D object: ${error.message ?: "Unknown error"}")
            createFallbackSphereForObject()
        }
    }
    
    /**
     * Creates a fallback sphere for objects
     * 
     * @param name The name of the sphere
     * @param position The position of the sphere
     * @param rotation The rotation of the sphere
     * @param scale The scale of the sphere
     * @return The created node containing the sphere
     */
    private fun createFallbackSphere(
        name: String,
        position: Vector3,
        rotation: Vector3,
        scale: Vector3,
        color: Color3 = Color3(1f, 1f, 1f),
        emissiveColor: Color3 = Color3.White().scale(.2f),
        diameter: Float = .5f,
        segments: Int = 8,
        alpha: Float = .8f,
        isPickable: Boolean = true,
    ): Node {
        // Create a transform node that will act as our container
        val container = TransformNode("container_$name", scene)
        
        // Set the container's position, rotation, and scale
        container.position = position
        container.rotation = rotation
        container.scaling = scale
        
        // Create a sphere mesh
        val sphere = MeshBuilder.CreateSphere(
            name,
            object : CreateSphereOptions {
                override val diameter = diameter
                override val segments = segments
            },
            scene
        )
        
        // Create a material
        val material = PBRMaterial("material_$name", scene)
        material.albedoColor = color
        material.metallic = 0.8f
        material.roughness = 0.2f
        material.alpha = alpha
        material.emissiveColor = emissiveColor
        
        // Apply the material to the sphere
        sphere.material = material
        
        // Reset the sphere's position to origin since it will inherit from parent
        sphere.position = Vector3(0f, 0f, 0f)
        sphere.rotation = Vector3(0f, 0f, 0f)
        sphere.scaling = Vector3(1f, 1f, 1f)

        // Configure pickability
        sphere.isPickable = isPickable
        
        // Make the sphere a child of the container
        sphere.parent = container
        
        console.log("[DEBUG_OBJECT] Created fallback sphere for object: $name in container (diameter=$diameter, color=$color, pickable=$isPickable)")
        
        return container
    }

    /**
     * Starts dragging an object
     * 
     * @param objectId The ID of the object to drag
     * @param clickPoint The point where the user clicked (ray intersection point)
     * @return true if dragging started successfully, false otherwise
     */
    // --- Create Object Placement Mode ---
    private var isCreatePlacementActive: Boolean = false
    private var createPlacementPreview: Node? = null
    private var onCreatePlacementConfirm: ((Vector3) -> Unit)? = null
    private var onCreatePlacementCancel: (() -> Unit)? = null
    private var pendingCreatePosition: Vector3? = null

    fun isCreatePlacementModeActive(): Boolean = isCreatePlacementActive

    /** Public API to allow editor dialog to consume the pending create position once. */
    fun consumePendingCreatePosition(): Vector3? {
        val p = pendingCreatePosition
        pendingCreatePosition = null
        return p
    }

    /** Starts the object placement mode with a small red non-pickable sphere under the cursor. */
    fun startCreatePlacementMode(
        onConfirm: ((Vector3) -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
    ) {
        if (isCreatePlacementActive) return
        isCreatePlacementActive = true
        onCreatePlacementConfirm = onConfirm
        onCreatePlacementCancel = onCancel

        scene.getEngine().getRenderingCanvas()?.focus()

        try {
            // Create preview sphere (red, small, non-pickable)
            createPlacementPreview = createFallbackSphere(
                name = "create_preview",
                position = Vector3(0f, 0f, 0f),
                rotation = Vector3(0f, 0f, 0f),
                scale = Vector3(1f, 1f, 1f),
                color = Color3(1f, 0f, 0f),
                emissiveColor = Color3(1f, 0f, 0f),
                diameter = 0.5f,
                segments = 8,
                alpha = 0.9f,
                isPickable = false,
            )

            // Begin dedicated picking depth to respect proper occlusion and exclusions
            try {
                DepthPicking.beginPickingDepth(scene, excludeMeshes = createPlacementPreview!!.getChildMeshes())
            } catch (t: Throwable) {
                t.printStackTrace()
                console.warn("[CreatePlacement] Failed to begin picking depth: ${t.message}")
            }

            // Visual cursor feedback
            document.body?.style?.cursor = "crosshair"
        } catch (t: Throwable) {
            console.error("[CreatePlacement] Failed to start placement mode", t)
            cancelCreatePlacementMode()
        }
    }

    /** Updates the placement preview position. Called by InputHandler. */
    fun updateCreatePlacementPreview(x: Int, y: Int) {
        if (!isCreatePlacementActive) return
        coroutineScope.launch {
            try {
                val wp = DepthPicking.getVisibleWorldPointAt(scene, x, y)
                if (wp != null) {
                    createPlacementPreview?.position = Vector3(wp.x, wp.y, wp.z)
                }
            } catch (_: Throwable) {}
        }
    }

    /** Confirms the object placement. Called by InputHandler. */
    fun confirmCreatePlacement(x: Int, y: Int) {
        if (!isCreatePlacementActive) return
        coroutineScope.launch {
            val wp = DepthPicking.getVisibleWorldPointAt(scene, x, y)
            if (wp != null) {
                pendingCreatePosition = Vector3(wp.x, wp.y, wp.z)
                val confirm = onCreatePlacementConfirm
                endCreatePlacementMode()
                confirm?.invoke(pendingCreatePosition!!)
            }
        }
    }

    /** Cancels the object placement mode, removing preview. */
    fun cancelCreatePlacementMode() {
        val cancel = onCreatePlacementCancel
        endCreatePlacementMode()
        cancel?.invoke()
    }

    private fun endCreatePlacementMode() {
        if (!isCreatePlacementActive) return
        isCreatePlacementActive = false

        onCreatePlacementConfirm = null
        onCreatePlacementCancel = null

        // Dispose preview node
        try { (createPlacementPreview as? TransformNode)?.dispose() } catch (_: Throwable) {}
        createPlacementPreview = null

        // End picking depth
        try { DepthPicking.endPickingDepth() } catch (_: Throwable) {}

        // Restore cursor
        document.body?.style?.cursor = "default"
    }

    fun startDragging(objectId: String, clickPoint: Vector3? = null): Boolean {
        // Find the object node
        val node = objectMeshes[objectId]
        if (node != null) {
            try {
                // Select the object to highlight it
                selectObject(objectId)
                
                // Store the dragging state
                draggingObjectId = objectId
                isDragging = true
                dragStartPosition = Vector3(node.position.x, node.position.y, node.position.z)
                
                // Calculate and store the offset between the click point and the object's pivot
                if (clickPoint != null) {
                    // The offset is the difference between the object's position and the click point
                    dragClickOffset = Vector3(
                        node.position.x - clickPoint.x,
                        0f, // Ignore vertical offset from grab point to ensure the object stays at its target vertical height (on the ground/surface)
                        node.position.z - clickPoint.z
                    )
                    console.log("Calculated drag offset: (${dragClickOffset!!.x}, ${dragClickOffset!!.y}, ${dragClickOffset!!.z})")
                } else {
                    // If no click point is provided, set the offset to zero
                    dragClickOffset = Vector3.Zero()
                }
                
                // Change cursor to grabbing
                document.body?.style?.cursor = "grabbing"
                
                console.log("Started dragging object: $objectId")
                onSuccess?.invoke("Started moving object")
                return true
            } catch (error: Throwable) {
                console.error("Error starting drag", error)
                onError?.invoke("Failed to start moving object: ${error.message ?: "Unknown error"}")
                return false
            }
        } else {
            console.warn("Object node not found: $objectId")
            onError?.invoke("Object not found in scene")
            return false
        }
    }
    
    /**
     * Updates the position of the currently dragged object
     * 
     * @param position The new position for the object (ray intersection point)
     * @return true if the position was updated successfully, false otherwise
     */
    fun updateDragging(position: Vector3): Boolean {
        if (!isDragging || draggingObjectId == null) {
            console.warn("Cannot update dragging: isDragging=$isDragging, draggingObjectId=$draggingObjectId")
            return false
        }
        
        try {
            // Find the object node
            val node = objectMeshes[draggingObjectId!!]
            if (node != null) {
                // Ensure the node has no parent (position is global to scene)
                node.parent = null
                
                // Apply the stored offset to the position
                val offset = dragClickOffset ?: Vector3.Zero()
                val verticalOffset = objectVerticalOffsets[draggingObjectId!!] ?: 0f
                val adjustedPosition = Vector3(
                    position.x + offset.x,
                    position.y + Z_FIGHTING_BUFFER + verticalOffset, // Apply ground height + z-fighting buffer + target vertical offset, ignore vertical grab offset
                    position.z + offset.z
                )
                
                // Update the node position with the adjusted position
                console.log("Updating object ${draggingObjectId} position to (${adjustedPosition.x}, ${adjustedPosition.y}, ${adjustedPosition.z})")
                console.log("Applied offset: (${offset.x}, ${offset.y}, ${offset.z})")
                node.position = adjustedPosition
                spatialHash.update(draggingObjectId!!, adjustedPosition)
                
                return true
            } else {
                console.warn("Object node not found for dragging: $draggingObjectId")
            }
            return false
        } catch (error: Throwable) {
            console.error("Error updating drag position", error)
            return false
        }
    }
    
    /**
     * Ends dragging and persists the new position
     * 
     * @return true if dragging ended successfully, false otherwise
     */
    fun endDragging(): Boolean {
        if (!isDragging || draggingObjectId == null) {
            return false
        }
        
        try {
            // Find the object node
            val node = objectMeshes[draggingObjectId!!]
            if (node != null) {
                // Ensure the node has no parent (position is global to scene)
                node.parent = null
                
                // Log the raw position values for debugging
                console.log("[DEBUG_DROP] Raw position values: x=${node.position.x}, y=${node.position.y}, z=${node.position.z}")
                
                // Convert the BabylonJS Vector3 to our Position model
                // Access position values directly without calling toFloat() since they're already numeric
                val newPosition = Position(
                    x = node.position.x,
                    y = node.position.y,
                    z = node.position.z
                )
                
                console.log("[DEBUG_DROP] Converted position: x=${newPosition.x}, y=${newPosition.y}, z=${newPosition.z}")
                
                // Persist the position change
                moveObject(draggingObjectId!!, newPosition)
                
                // Reset dragging state
                isDragging = false
                val objectId = draggingObjectId
                draggingObjectId = null
                dragStartPosition = null
                dragClickOffset = null
                
                // Reset cursor to grab if we're still hovering over the object
                if (hoveredObjectId == objectId) {
                    document.body?.style?.cursor = "grab"
                } else {
                    document.body?.style?.cursor = "default"
                }
                
                console.log("Ended dragging object: $objectId")
                return true
            }
            return false
        } catch (error: Throwable) {
            console.error("Error ending drag", error)
            
            // Reset dragging state even if there was an error
            isDragging = false
            draggingObjectId = null
            dragStartPosition = null
            
            // Reset cursor
            document.body?.style?.cursor = "default"
            
            return false
        }
    }
    
    // Store world meshes for collision detection
    private var skybox: AbstractMesh? = null
    private var worldMeshes: Array<AbstractMesh> = emptyArray()

    /**
     * Sets the world meshes for collision detection
     * 
     * @param worldMeshes The array of world meshes
     */
    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        @Suppress("UNCHECKED_CAST")
        this.worldMeshes = meshes
        this.skybox = meshes.find { it.isSkybox() }
        
        // SelectionOutlineLayer does not support exclusions as it only renders what is explicitly selected.
        if (false) {
            meshes.forEach { mesh ->
                try {
                    // selectionOutlineLayer.addExcludedMesh(mesh)
                } catch (e: Throwable) {
                    console.warn("Failed to exclude mesh from highlighting: ${mesh.name}")
                }
            }
        }
        
        console.log("Set ${meshes.size} world meshes for collision detection and excluded them from highlighting")
    }
    
    /**
     * Gets the world meshes for collision detection
     * 
     * @return The array of world meshes
     */
    fun getWorldMeshes(): Array<AbstractMesh> {
        return worldMeshes
    }

    fun getSkybox(): AbstractMesh? = skybox

    /**
     * Excludes a mesh from the highlight layer to avoid unwanted outline effects.
     */
    fun excludeMeshFromHighlight(mesh: AbstractMesh) {
        // SelectionOutlineLayer does not support exclusions as it only renders what is explicitly selected.
    }

    /**
     * Sets up WebSocket listeners for object updates and loads existing objects
     */
    fun setupWebSocketListeners(currentWorldId: String) {
        // Load existing objects for this world
        loadExistingObjects(currentWorldId)
        
        // Set up WebSocket listeners for real-time updates
        WebSocketApi.addListener(WebSocketAction.ADD_OBJECT) { message ->
            try {
                // Parse the object data from the message
                val objData = message.data
                val obj = json.decodeFromString(Object.serializer(), objData)
                
                console.log("Received object update via WebSocket: ${obj.key}")
                
                // Check if we already have this object
                val existingNode = objectMeshes[obj.key]
                if (existingNode != null) {
                    val prevKind = objectKinds[obj.key]
                    val kindChanged = prevKind != obj.kind
                    val prev = objectModelSources[obj.key]
                    val incoming = Pair(obj.file, obj.collection)
                    val modelChanged = prev == null || prev.first != incoming.first || prev.second != incoming.second
                    if (kindChanged || modelChanged) {
                        console.log("Kind/model changed for ${obj.key}: prevKind=$prevKind newKind=${obj.kind} prevModel=$prev newModel=$incoming, recreating")
                        removeObject(obj.key)
                        createObjectPreferInventory(obj)
                    } else {
                        // Update existing object transform
                        existingNode.position = Vector3(obj.position.x, obj.position.y, obj.position.z)
                        existingNode.rotation = Vector3(obj.rotation.x, obj.rotation.y, obj.rotation.z)
                        existingNode.scaling = Vector3(obj.scale.x, obj.scale.y, obj.scale.z)
                        // Update status text as well
                        handleObjectStatusText(obj.key, obj.status, existingNode)
                        // Update kind and vertical offset tracking
                        objectKinds[obj.key] = obj.kind
                        objectVerticalOffsets[obj.key] = obj.verticalOffset
                        console.log("Updated existing object: ${obj.key}")
                    }
                } else {
                    // Create new object (prefer inventory-based rendering)
                    createObjectPreferInventory(obj)
                }
            } catch (error: Throwable) {
                console.error("Error processing object update", error)
            }
        }
        
        WebSocketApi.addListener(WebSocketAction.REMOVE_OBJECT) { message ->
            try {
                // The data may be a JSON-encoded string ("id") or a raw string (id)
                val objectId = try {
                    json.decodeFromString<String>(message.data)
                } catch (_: Throwable) {
                    // Fallback to raw data if not JSON encoded
                    message.data
                }

                console.log("Received object removal via WebSocket: $objectId")

                // Use the removeObject method to clean up the object and its status text
                removeObject(objectId)
            } catch (error: Throwable) {
                console.error("Error processing object removal", error)
            }
        }
        
        // Listen for object position updates
        WebSocketApi.addListener(WebSocketAction.OBJECT_UPDATE) { message ->
            try {
                // Parse the object data from the message
                val objData = message.data
                val obj = json.decodeFromString(Object.serializer(), objData)
                
                console.log("Received object position update via WebSocket: ${obj.key}")
                
                // Find the object node
                val node = objectMeshes[obj.key]
                if (node != null) {
                    val prevKind = objectKinds[obj.key]
                    val kindChanged = prevKind != obj.kind
                    val prev = objectModelSources[obj.key]
                    val incoming = Pair(obj.file, obj.collection)
                    val modelChanged = prev == null || prev.first != incoming.first || prev.second != incoming.second
                    if (kindChanged || modelChanged) {
                        console.log("Kind/model changed for ${obj.key} via OBJECT_UPDATE: prevKind=$prevKind newKind=${obj.kind} prevModel=$prev newModel=$incoming, recreating")
                        removeObject(obj.key)
                        createObjectPreferInventory(obj)
                    } else {
                        // Update existing object transform and status in-place
                        node.position = Vector3(obj.position.x, obj.position.y, obj.position.z)
                        node.rotation = Vector3(obj.rotation.x, obj.rotation.y, obj.rotation.z)
                        node.scaling = Vector3(obj.scale.x, obj.scale.y, obj.scale.z)
                        // Update status text as well
                        handleObjectStatusText(obj.key, obj.status, node)
                        // Update kind and vertical offset tracking
                        objectKinds[obj.key] = obj.kind
                        objectVerticalOffsets[obj.key] = obj.verticalOffset
                        console.log("OBJECT_UPDATE applied in-place for ${obj.key}")
                    }
                } else {
                    // Object doesn't exist in scene, prefer inventory-based rendering
                    console.log("Object not found in scene, creating it: ${obj.key}")
                    createObjectPreferInventory(obj)
                }
            } catch (error: Throwable) {
                console.error("Error processing object position update", error)
            }
        }

        // Typing indicator (server → clients only)
        fun setObjectTypingLocal(objectId: String, isTyping: Boolean) {
            if (!isTyping) {
                objectTypingTimeouts.remove(objectId)?.let { window.clearTimeout(it) }
                objectTypingHandles.remove(objectId)?.remove()
                // Restore status text if we have one cached
                val node = objectMeshes[objectId]
                if (node != null) {
                    val status = objectStatusTexts[objectId]?.trim().orEmpty()
                    if (status.isNotBlank()) {
                        handleObjectStatusText(objectId, status, node)
                    }
                }
                return
            }
            // While typing is active, hide any existing status text immediately
            objectStatusTextHandles.remove(objectId)?.remove()
            objectStatusOffsets.remove(objectId)
            val node = objectMeshes[objectId]
            if (node == null) {
                // Retry shortly if the object/node is not ready yet
                window.setTimeout({ setObjectTypingLocal(objectId, isTyping) }, 50)
                return
            }
            // Find the highest Y among child meshes to place bubble above the object
            val childMeshes = node.getChildMeshes(false).filter { !DepthPicking.isStatusBillboardMesh(it) }
            var highestY = node.position.y
            if (childMeshes.isNotEmpty()) {
                try {
                    if (node is TransformNode) {
                        node.computeWorldMatrix(true)
                    }

                    var maxY = -1e9f
                    var found = false

                    for (mesh in childMeshes) {
                        mesh.computeWorldMatrix(true)
                        val meshMaxY = mesh.getBoundingInfo().boundingBox.maximumWorld.y
                        if (meshMaxY > maxY) {
                            maxY = meshMaxY
                            found = true
                        }
                    }
                    if (found) highestY = maxY
                } catch (_: Throwable) {
                    highestY = node.position.y
                }
            }
            val offsetY = (highestY + 0.5f) - node.position.y

            val existing = objectTypingHandles[objectId]
            if (existing != null) {
                // Parent if not already
                try { if (existing.node.parent != node) existing.node.parent = node } catch (_: Throwable) {}
                existing.position = Vector3(0f, offsetY, 0f)
            } else {
                val handle = worldObjectManager.createTextBillboard(
                    text = "\uD83D\uDCAC",
                    position = Vector3(node.position.x, node.position.y + offsetY, node.position.z),
                    textColor = "white",
                    backgroundColor = "#000000F0",
                )
                if (handle != null) {
                    // Parent to object so it moves with it automatically
                    try { handle.node.parent = node } catch (_: Throwable) {}
                    handle.position = Vector3(0f, offsetY, 0f)
                    objectTypingHandles[objectId] = handle
                }
            }

            // Safety timeout to auto-clear after 30s
            objectTypingTimeouts[objectId]?.let { window.clearTimeout(it) }
            objectTypingTimeouts[objectId] = window.setTimeout({
                objectTypingHandles.remove(objectId)?.remove()
                objectTypingTimeouts.remove(objectId)
            }, 30000)
        }

        WebSocketApi.addListener(WebSocketAction.OBJECT_TYPING) { message ->
            try {
                val data = json.decodeFromString(ObjectTypingData.serializer(), message.data)
                setObjectTypingLocal(data.objectId, data.isTyping)
            } catch (e: Throwable) {
                console.error("Error processing OBJECT_TYPING", e)
            }
        }
    }
    
    /**
     * Loads existing objects for a world
     * 
     * @param worldId The ID of the world to load objects for
     */
    private fun loadExistingObjects(worldId: String) {
        coroutineScope.launch {
            try {
                console.log("[DEBUG_LOAD] Loading existing objects for world: $worldId")
                
                // Fetch objects from the API
                val objects = ObjectApi.getObjectsByWorldId(worldId)
                
                console.log("[DEBUG_LOAD] Loaded ${objects.size} objects from API")
                
                if (objects.isEmpty()) {
                    console.log("[DEBUG_LOAD] No objects found for world: $worldId")
                } else {
                    // Create 3D representations for each object
                    console.log("[DEBUG_LOAD] Creating 3D representations for ${objects.size} objects")
                    objects.forEachIndexed { index, obj ->
                        console.log("[DEBUG_LOAD] Creating object ${index + 1}/${objects.size}: ${obj.key}")
                        createObjectPreferInventory(obj)
                    }
                }
                
                // Verify the number of objects created
                console.log("[DEBUG_LOAD] Finished creating 3D objects. Total objects in map: ${objectMeshes.size}")
                
                // List all object IDs for debugging
                val objectIds = objectMeshes.keys.joinToString(", ")
                console.log("[DEBUG_LOAD] Object IDs in map: $objectIds")
            } catch (error: Throwable) {
                console.error("[DEBUG_LOAD] Error loading existing objects", error)
                onError?.invoke("Failed to load objects: ${error.message ?: "Unknown error"}")
            }
        }
    }
    
    /**
     * Gets the currently selected object ID
     * 
     * @return The ID of the currently selected object, or null if no object is selected
     */
    fun getSelectedObject(): String? {
        return selectedObject
    }
    
    /**
     * Gets all object meshes for intersection testing
     * 
     * @return Map of object IDs to all meshes for that object
     */
    fun getObjectMeshes(): Map<String, List<AbstractMesh>> {
        val result = mutableMapOf<String, List<AbstractMesh>>()
        
        // For each object node, get all its child meshes
        objectMeshes.forEach { (objectId, node) ->
            try {
                // Get all child meshes from the node
                val childMeshes = node.getChildMeshes(false)
                
                if (childMeshes.size > 0) {
                    // Store all meshes for this object
                    result[objectId] = childMeshes.toList()
                    console.log("[DEBUG_MESHES] Found ${childMeshes.size} meshes for object: $objectId")
                } else {
                    // If no child meshes, the node itself might be a mesh (fallback sphere case)
                    if (node is AbstractMesh) {
                        result[objectId] = listOf(node)
                        console.log("[DEBUG_MESHES] Using node itself as mesh for object: $objectId")
                    } else {
                        console.log("[DEBUG_MESHES] No meshes found for object: $objectId")
                    }
                }
            } catch (e: Throwable) {
                console.error("[DEBUG_MESHES] Error getting meshes for object: $objectId", e)
            }
        }
        
        return result
    }

    /** Returns a snapshot of root nodes for all backend objects, keyed by backend object ID. */
    fun getObjectNodes(): Map<String, Node> = objectMeshes.toMap()

    /** Returns IDs of objects within the specified radius of the position. */
    fun getNearbyObjectIds(position: Vector3, radius: Float): Set<String> {
        return spatialHash.getNearby(position, radius)
    }

    fun isInventoryObject(objectId: String): Boolean {
        return objectKinds[objectId] == ObjectKind.Inventory
    }
    
    /**
     * Disposes resources when the scene is destroyed
     */
    fun update(deltaTime: Float) {
        if (objectStatusTextHandles.isEmpty() && objectTypingHandles.isEmpty()) return

        // Force quest indicators to be visible
        activeQuestIndicators.keys.forEach { objectId ->
            animateStatusText(objectId, true)
        }

        val anyAnimationGroupsPlaying = try {
            scene.animationGroups.any { it.isPlaying }
        } catch (_: Throwable) {
            false
        }

        // Update status text positions
        objectStatusTextHandles.forEach { (objectId, handle) ->
            updateObjectFloatingUiPosition(
                objectId,
                handle,
                anyAnimationGroupsPlaying,
                isStatusText = true
            )
        }

        // Update typing indicator positions
        objectTypingHandles.forEach { (objectId, handle) ->
            updateObjectFloatingUiPosition(
                objectId,
                handle,
                anyAnimationGroupsPlaying,
                isStatusText = false
            )
        }
    }

    /**
     * Updates the position of a floating UI element (status text or typing indicator) 
     * based on the object's current bounding box.
     */
    private fun updateObjectFloatingUiPosition(
        objectId: String,
        handle: WorldObjectHandle,
        anyAnimationGroupsPlaying: Boolean,
        isStatusText: Boolean
    ) {
        val node = objectMeshes[objectId] ?: return
        
        // Task 3: Mesh Caching
        val childMeshes = cachedMeshes.getOrPut(objectId) {
            node.getChildMeshes(false).filter { !DepthPicking.isStatusBillboardMesh(it) }
        }
        
        // Task 1: Floating UI Caching
        // Check if the object has active animations or if it's the first time
        val isFirstTime = !cachedHighestY.containsKey(objectId)
        val hasAnimations = node.animations.isNotEmpty() ||
                            childMeshes.any { it.animations.isNotEmpty() } ||
                            anyAnimationGroupsPlaying

        if (hasAnimations || isFirstTime) {
            if (childMeshes.isEmpty()) return

            var maxY = -1e9f
            var found = false

            if (node is TransformNode) {
                node.computeWorldMatrix(true)
            }

            for (mesh in childMeshes) {
                try {
                    mesh.computeWorldMatrix(true)
                    val boundingInfo = mesh.getBoundingInfo()
                    val meshMaxY = boundingInfo.boundingBox.maximumWorld.y
                    if (meshMaxY > maxY) {
                        maxY = meshMaxY
                        found = true
                    }
                } catch (_: Throwable) {
                    // Skip meshes with invalid bounding info
                }
            }

            if (found) {
                cachedHighestY[objectId] = maxY
                val offsetY = (maxY + 0.5f) - node.position.y
                
                // Only update if the change is significant to avoid unnecessary work
                val currentOffsetY = if (isStatusText) {
                    objectStatusOffsets[objectId] ?: 0f
                } else {
                    // For typing indicator, we don't have a separate offset map, but we use handle.position.y relative to node.position.y
                    handle.position.y
                }

                if (abs(offsetY - currentOffsetY) > 0.001f) {
                    if (isStatusText) {
                        objectStatusOffsets[objectId] = offsetY
                    }
                    handle.position = Vector3(0f, offsetY, 0f)
                }
            }
        }
    }

    fun dispose() {
        // Dispose the selection outline layer
        selectionOutlineLayer.dispose()
        
        // Dispose the asset manager
        assetManager.dispose()
        
        // Reset cursor
        document.body?.style?.cursor = "default"
        
        // Clear maps
        objectMeshes.clear()
        cachedMeshes.clear()
        cachedHighestY.clear()
        objectStatusTextHandles.clear()
        objectStatusOffsets.clear()
        objectStatusTexts.clear()
        objectDisplayedStatusTexts.clear()
        objectVerticalOffsets.clear()
        objectTypingHandles.clear()
        objectTypingTimeouts.values.forEach { window.clearTimeout(it) }
        objectTypingTimeouts.clear()
        objectModelSources.clear()
        objectKinds.clear()
        activeQuestIndicators.clear()
        nearbyObjectIds.clear()
        interactableObjectId = null
        
        // Reset state
        hoveredObjectId = null
        selectedObject = null
        draggingObjectId = null
        isDragging = false
        dragStartPosition = null
        dragClickOffset = null
        
        spatialHash.clear()
        console.log("Disposing SceneObjectManager resources")
    }

    fun onMouseLeave() {
        document.body?.style?.cursor = "default"
    }

    /**
     * Starts all animations for an object and ensures all scene animation groups are playing
     * 
     * @param node The root node of the object
     */
    private fun startAnimations(node: Node) {
        try {
            console.log("[DEBUG_ANIMATION] Starting animations for node: ${node.name}")
            
            // Start simple animations on the node itself
            if (node.animations.isNotEmpty()) {
                scene.beginAnimation(node, 0f, 1000f, true)
            }
            
            // Start simple animations on all child meshes
            node.getChildMeshes(false).forEach { mesh ->
                if (mesh.animations.isNotEmpty()) {
                    scene.beginAnimation(mesh, 0f, 1000f, true)
                }
            }
            
            // Also start all animation groups in the scene
            // This ensures GLB-based animations are playing
            scene.animationGroups.forEach { group ->
                if (!group.isPlaying) {
                    group.play(true)
                }
            }
        } catch (e: Throwable) {
            console.warn("[DEBUG_ANIMATION] Error starting animations: ${e.message}")
        }
    }
    
    /**
     * Animates the status text for an object (slide up + zoom in + bounce)
     * 
     * @param objectId The ID of the object
     * @param show Whether to show or hide the status text
     */
    private fun animateStatusText(objectId: String, show: Boolean) {
        val handle = objectStatusTextHandles[objectId] ?: return
        val offsetY = objectStatusOffsets[objectId] ?: handle.position.y
        ObjectStatusAnimator.animateStatusText(objectId, handle, show, Vector3(0f, offsetY, 0f), scene)
    }

    /**
     * Helper method to handle object status text
     * 
     * This method handles the creation, updating, and removal of status text for an object.
     * It ensures that:
     * - Status text is created when an object has a non-blank status
     * - Status text is removed when an object's status becomes blank
     * - Status text is moved when an object moves, rather than being recreated
     * 
     * @param objectId The ID of the object
     * @param status The status text
     * @param node The node representing the object in the scene
     */
    /**
     * Updates active quest steps for objects in the scene.
     * 
     * @param steps Map of objectId to QuestStep
     */
    fun updateActiveQuests(steps: Map<String, QuestStep>) {
        activeQuestIndicators.clear()
        activeQuestIndicators.putAll(steps)
        
        // Refresh all object statuses that might have changed
        objectMeshes.forEach { (objectId, node) ->
            val status = objectStatusTexts[objectId] ?: ""
            handleObjectStatusText(objectId, status, node)
        }
    }

    private fun handleObjectStatusText(objectId: String, status: String, node: Node) {
        try {
            console.log("[DEBUG_STATUS] Handling status text for object: $objectId, status: '$status'")
            
            // Check if the object already has a status text handle
            val existingHandle = objectStatusTextHandles[objectId]
            console.log("[DEBUG_STATUS] Existing handle: ${existingHandle != null}")

            // Read previous status and cache latest for restoration after typing
            val previousStatus = objectStatusTexts[objectId] ?: ""
            objectStatusTexts[objectId] = status

            // Apply quest overrides
            val questStep = activeQuestIndicators[objectId]
            var finalStatus = if (questStep?.statusOverride != null) {
                questStep.statusOverride!!
            } else {
                status
            }
            
            if (!questStep?.indicatorEmoji.isNullOrBlank()) {
                finalStatus = "${questStep!!.indicatorEmoji} $finalStatus"
            } else if (questStep != null && finalStatus.isBlank()) {
                // Default quest indicator if nothing else is specified
                finalStatus = "❓"
            }

            // Prepend "🆇 " if it's the interactable object
            val isInteractable = objectId == interactableObjectId
            val displayedStatus = if (isInteractable) {
                if (finalStatus.isBlank()) "🆇" else "🆇 $finalStatus"
            } else {
                finalStatus
            }

            // If typing indicator is active, hide status text (typing has precedence)
            if (objectTypingHandles.containsKey(objectId)) {
                existingHandle?.remove()
                objectStatusTextHandles.remove(objectId)
                objectStatusOffsets.remove(objectId)
                objectDisplayedStatusTexts.remove(objectId)
                return
            }
            
            if (displayedStatus.isBlank()) {
                // If status is blank, remove any existing status text
                if (existingHandle != null) {
                    console.log("[DEBUG_STATUS] Removing status text for object: $objectId")
                    existingHandle.remove()
                    objectStatusTextHandles.remove(objectId)
                    objectStatusOffsets.remove(objectId)
                    objectDisplayedStatusTexts.remove(objectId)
                    console.log("[DEBUG_STATUS] Successfully removed status text for object: $objectId")
                } else {
                    console.log("[DEBUG_STATUS] No status text to remove for object: $objectId")
                }
            } else {
                // ... (rest of the logic)
                // Create a WorldObjectHandle for the node
                val objectHandle = object : WorldObjectHandle {
                    override val id: String = objectId
                    override var node: Node = node
                    override fun remove() {
                        // No-op for now
                    }
                    override fun updateDirection(direction: CharacterDirection) {
                        // No-op for now
                    }
                    override fun updateActivity(activity: PlayerActivity?) {
                        // No-op for now
                    }
                    override fun getMeshes(): Array<AbstractMesh> {
                        return node.getChildMeshes(false)
                    }
                }

                // Get all child meshes of the node
                val childMeshes = node.getChildMeshes(false).filter { !DepthPicking.isStatusBillboardMesh(it) }
                
                // Find the highest Y point of all bounding boxes
                var highestY = node.position.y
                
                if (childMeshes.isNotEmpty()) {
                    try {
                        if (node is TransformNode) {
                            node.computeWorldMatrix(true)
                        }
                        
                        var maxY = -1e9f
                        var found = false
                        
                        // Check all meshes
                        for (mesh in childMeshes) {
                            mesh.computeWorldMatrix(true)
                            val boundingInfo = mesh.getBoundingInfo()
                            val meshMaxY = boundingInfo.boundingBox.maximumWorld.y
                            
                            if (meshMaxY > maxY) {
                                maxY = meshMaxY
                                found = true
                            }
                        }
                        
                        // Use the highest point found
                        if (found) {
                            highestY = maxY
                            console.log("[DEBUG_STATUS] Highest Y point of bounding boxes: $highestY")
                        }
                    } catch (e: Throwable) {
                        console.error("[DEBUG_STATUS] Error finding highest Y point: ${e.message}")
                        // Fall back to node position if there's an error
                        highestY = node.position.y
                    }
                } else {
                    console.log("[DEBUG_STATUS] No child meshes found, using node position")
                }
                
                // Position the status text 0.5 units above the highest point (local offset so it follows during drag)
                val offsetY = (highestY + 0.5f) - node.position.y
                objectStatusOffsets[objectId] = offsetY

                if (existingHandle != null) {
                    // If there's an existing handle, update its text (if changed)
                    val prevDisplayed = objectDisplayedStatusTexts[objectId] ?: ""
                    if (prevDisplayed != displayedStatus) {
                        console.log("[DEBUG_STATUS] Updating status text content for object: $objectId from '$prevDisplayed' to '$displayedStatus'")
                        try {
                            existingHandle.updateText(displayedStatus)
                            objectDisplayedStatusTexts[objectId] = displayedStatus
                            console.log("[DEBUG_STATUS] Successfully updated status text content for object: $objectId")
                        } catch (e: Throwable) {
                            console.error("[DEBUG_STATUS] Error updating status text content for object: $objectId", e)
                        }
                    }

                    // Parent to object so it moves with it during drag and transforms
                    if (existingHandle.node.parent != node) {
                        existingHandle.node.parent = node
                    }

                    console.log("[DEBUG_STATUS] Updating status text local offset for object: $objectId (offsetY=$offsetY)")
                    
                    // Ensure visibility is consistent with hover, proximity, or interactable state
                    if (objectId == hoveredObjectId || nearbyObjectIds.contains(objectId) || objectId == interactableObjectId || activeQuestIndicators.containsKey(objectId)) {
                        animateStatusText(objectId, true)
                    } else {
                        // Ensure it's hidden if neither
                        if (existingHandle.isVisible) {
                            animateStatusText(objectId, false)
                        } else {
                            // Even if not visible, update position for when it becomes visible
                            existingHandle.position = Vector3(0f, offsetY, 0f)
                        }
                    }
                    
                    console.log("[DEBUG_STATUS] Successfully updated status text position for object: $objectId")
                } else {
                    // Create new status text
                    console.log("[DEBUG_STATUS] Creating new status text for object: $objectId with status: '$displayedStatus'")
                    val statusTextHandle = worldObjectManager.createObjectStatusText(objectHandle, displayedStatus)

                    if (statusTextHandle != null) {
                        // Parent to object so it moves with it automatically
                        try { statusTextHandle.node.parent = node } catch (_: Throwable) {}
                        // Store the handle for future updates
                        objectStatusTextHandles[objectId] = statusTextHandle
                        objectDisplayedStatusTexts[objectId] = displayedStatus
                        
                        // Set initial state to hidden/zero-scaled to ensure animation triggers
                        statusTextHandle.isVisible = false
                        statusTextHandle.node.scaling = Vector3(0f, 0f, 0f)
                        statusTextHandle.position = Vector3(0f, offsetY - 0.2f, 0f)
                        
                        // Set initial visibility based on hover, proximity, or interactable state
                        if (objectId == hoveredObjectId || nearbyObjectIds.contains(objectId) || objectId == interactableObjectId || activeQuestIndicators.containsKey(objectId)) {
                            animateStatusText(objectId, true)
                        } else {
                            // Already set to isVisible = false above
                        }
                        
                        console.log("[DEBUG_STATUS] Successfully created and stored status text for object: $objectId")
                        console.log("[DEBUG_STATUS] Total status text handles: ${objectStatusTextHandles.size}")
                    } else {
                        console.log("[DEBUG_STATUS] Failed to create status text for object: $objectId")
                    }
                }
            }
        } catch (e: Throwable) {
            console.error("[DEBUG_STATUS] Error handling status text for object: $objectId", e)
        }
    }
}
