package main.components.objects

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Color3
import lib.CreateSphereOptions
import lib.HighlightLayer
import lib.Mesh
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
import main.components.world.isBillboard
import main.components.world.isSkybox
import models.Item
import models.Object
import models.ObjectKind
import models.Position
import models.WebSocketAction

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
    
    // Tracks last known (file, collection) used to render each object, to detect when re-creation is needed
    private val objectModelSources = mutableMapOf<String, Pair<String, String>>()

    // Tracks object kinds for quick lookup (World vs Inventory)
    private val objectKinds = mutableMapOf<String, ObjectKind>()
    
    // Currently dragging object
    private var draggingObjectId: String? = null
    
    // Drag state
    private var isDragging = false
    private var dragStartPosition: Vector3? = null
    private var dragClickOffset: Vector3? = null
    
    // Track hovered object
    private var hoveredObjectId: String? = null
    
    // Add a highlight layer for the outline effect
    private val highlightLayer = HighlightLayer("objectHighlightLayer", scene)

    private var shadowManager: ShadowManager? = null
    
    // Asset manager for 3D models
    private val assetManager = ObjectAssetManager(scene)
    
    init {
        // Configure the highlight layer for white outline
        highlightLayer.intensity = 1.5f  // Increased intensity for more visible effect
        if (GraphicsSettings.isLowGraphicsModeEnabled()) {
            highlightLayer.blurHorizontalSize = 1f / 3f
            highlightLayer.blurVerticalSize = 1f / 3f
        } else {
            highlightLayer.blurHorizontalSize = 1f
            highlightLayer.blurVerticalSize = 1f
        }

        // Set additional properties if available
        try {
            // Set the outer glow color to a bright, high-contrast color
            highlightLayer.outerGlow = true
            
            // Set the inner glow color to false to create only an outline
            highlightLayer.innerGlow = false
            
            console.log("[DEBUG_HIGHLIGHT] Set additional properties: outerGlow=true, innerGlow=false")
        } catch (e: Throwable) {
            console.log("[DEBUG_HIGHLIGHT] Could not set additional properties: ${e.message}")
        }

        GraphicsSettings.addObserver {
            if (GraphicsSettings.isLowGraphicsModeEnabled()) {
                highlightLayer.blurHorizontalSize = 1f / 3f
                highlightLayer.blurVerticalSize = 1f / 3f
            } else {
                highlightLayer.blurHorizontalSize = 1f
                highlightLayer.blurVerticalSize = 1f
            }
        }

        console.log("[DEBUG_HIGHLIGHT] HighlightLayer initialized with intensity: ${highlightLayer.intensity}, blurHorizontalSize: ${highlightLayer.blurHorizontalSize}, blurVerticalSize: ${highlightLayer.blurVerticalSize}")
    }

    fun setShadowManager(shadowManager: ShadowManager) {
        this.shadowManager = shadowManager
    }
    
    /**
     * Highlights an object with a white outline when hovered
     * 
     * @param objectId The ID of the object to highlight
     * @return true if the object was highlighted successfully, false otherwise
     */
    fun highlightObject(objectId: String): Boolean {
        console.log("[DEBUG_HIGHLIGHT] Attempting to highlight object: $objectId")
        
        // Remove highlight from previously hovered object
        unhighlightObject()
        
        // Find the object node
        val node = objectMeshes[objectId]
        if (node != null) {
            console.log("[DEBUG_HIGHLIGHT] Found node for object: $objectId")
            try {
                // Get all child meshes from the node
                val childMeshes = node.getChildMeshes(false)
                console.log("[DEBUG_HIGHLIGHT] Found ${childMeshes.size} child meshes to highlight")
                
                // Add each child mesh to the highlight layer
                childMeshes.forEach { childMesh ->
                    console.log("[DEBUG_HIGHLIGHT] Adding mesh to highlight layer: ${childMesh.name}")
                    highlightLayer.addMesh(childMesh, Color3(1f, 1f, 0f), false)
                }
                
                // Store the hovered object ID
                hoveredObjectId = objectId
                
                // Change cursor to grab
                document.body?.style?.cursor = "grab"
                console.log("[DEBUG_HIGHLIGHT] Changed cursor to grab")
                
                console.log("[DEBUG_HIGHLIGHT] Successfully highlighted object: $objectId")
                return true
            } catch (error: Throwable) {
                console.error("[DEBUG_HIGHLIGHT] Error highlighting object", error)
                return false
            }
        } else {
            console.log("[DEBUG_HIGHLIGHT] No node found for object: $objectId")
        }
        return false
    }
    
    /**
     * Removes highlight from the currently hovered object
     */
    fun unhighlightObject() {
        console.log("[DEBUG_HIGHLIGHT] Attempting to unhighlight object")
        
        hoveredObjectId?.let { objectId ->
            console.log("[DEBUG_HIGHLIGHT] Current hovered object: $objectId")
            
            val node = objectMeshes[objectId]
            if (node != null) {
                console.log("[DEBUG_HIGHLIGHT] Found node for object: $objectId")
                try {
                    // Get all child meshes from the node
                    val childMeshes = node.getChildMeshes(false)
                    console.log("[DEBUG_HIGHLIGHT] Found ${childMeshes.size} child meshes to unhighlight")
                    
                    // Remove each child mesh from the highlight layer
                    childMeshes.forEach { childMesh ->
                        console.log("[DEBUG_HIGHLIGHT] Removing mesh from highlight layer: ${childMesh.name}")
                        highlightLayer.removeMesh(childMesh)
                    }
                    
                    // Reset cursor
                    document.body?.style?.cursor = "default"
                    console.log("[DEBUG_HIGHLIGHT] Reset cursor to default")
                    
                    console.log("[DEBUG_HIGHLIGHT] Successfully unhighlighted object: $objectId")
                } catch (error: Throwable) {
                    console.error("[DEBUG_HIGHLIGHT] Error unhighlighting object", error)
                }
            } else {
                console.log("[DEBUG_HIGHLIGHT] No node found for object: $objectId")
            }
            hoveredObjectId = null
        } ?: run {
            console.log("[DEBUG_HIGHLIGHT] No object currently highlighted")
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
                console.log("[DEBUG_REMOVE] Successfully removed status text for object: $objectId")
            }
            
            // Find and remove the object from the scene
            val node = objectMeshes[objectId]
            if (node != null) {
                console.log("[DEBUG_REMOVE] Removing node for object: $objectId")
                (node as? TransformNode)?.dispose()
                objectMeshes.remove(objectId)
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
     */
    private fun createInventoryItemInScene(obj: Object, item: Item) {
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
                // Store the object in our map for future reference
                objectMeshes[obj.key] = node
                // Track as inventory-based source to stabilize model-change checks
                objectModelSources[obj.key] = Pair("", "")
                // Track object kind
                objectKinds[obj.key] = ObjectKind.Inventory

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
                    position = position,
                    worldId = worldId
                )
                console.log("[DEBUG_DROP] Updated object with new position: (${updatedObj.position.x}, ${updatedObj.position.y}, ${updatedObj.position.z})")
                
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
            basePoint.y + 0.125f,
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
                    createInventoryItemInScene(obj, itemDef)
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

            // Track model source used for this object (may be blank strings)
            objectModelSources[obj.key] = Pair(obj.file, obj.collection)
            // Track kind as World for regular objects
            objectKinds[obj.key] = ObjectKind.World

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
                        
                        // Track model source used for this object
                        objectModelSources[obj.key] = Pair(obj.file, obj.collection)
                        // Track kind as World for regular objects
                        objectKinds[obj.key] = ObjectKind.World
                        
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
    private fun createFallbackSphere(name: String, position: Vector3, rotation: Vector3, scale: Vector3): Node {
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
                override val diameter = .5f
                override val segments = 8
            },
            scene
        )
        
        // Create a white material
        val material = PBRMaterial("material_$name", scene)
        material.albedoColor = Color3(1f, 1f, 1f)  // White color
        material.metallic = 0.8f
        material.roughness = 0.2f
        material.alpha = .8f
        material.emissiveColor = Color3.White().scale(.2f)
        
        // Apply the material to the sphere
        sphere.material = material
        
        // Reset the sphere's position to origin since it will inherit from parent
        sphere.position = Vector3(0f, 0f, 0f)
        sphere.rotation = Vector3(0f, 0f, 0f)
        sphere.scaling = Vector3(1f, 1f, 1f)

        // Make the sphere pickable
        sphere.isPickable = true
        
        // Make the sphere a child of the container
        sphere.parent = container
        
        console.log("[DEBUG_OBJECT] Created fallback sphere for object: $name in container")
        
        return container
    }

    /**
     * Starts dragging an object
     * 
     * @param objectId The ID of the object to drag
     * @param clickPoint The point where the user clicked (ray intersection point)
     * @return true if dragging started successfully, false otherwise
     */
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
                        node.position.y - clickPoint.y,
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
                val adjustedPosition = Vector3(
                    position.x + offset.x,
                    position.y + offset.y,
                    position.z + offset.z
                )
                
                // Update the node position with the adjusted position
                console.log("Updating object ${draggingObjectId} position to (${adjustedPosition.x}, ${adjustedPosition.y}, ${adjustedPosition.z})")
                console.log("Applied offset: (${offset.x}, ${offset.y}, ${offset.z})")
                node.position = adjustedPosition
                
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
        console.log("Set ${meshes.size} world meshes for collision detection")
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
     * Sets the player mesh to exclude from collision detection
     * 
     * @param playerMesh The player mesh
     */
    fun setPlayerMesh(playerMesh: dynamic) {
        // Implementation for setting player mesh
        console.log("Setting player mesh for collision exclusion")
    }
    
    /**
     * Sets the move circle mesh to exclude from collision detection
     * 
     * @param moveCircleMesh The move circle mesh
     */
    fun setMoveCircleMesh(moveCircleMesh: dynamic) {
        // Implementation for setting move circle mesh
        console.log("Setting move circle mesh for collision exclusion")
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
                        // Update kind tracking just in case
                        objectKinds[obj.key] = obj.kind
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
                        // Recreate to reflect potential inventory or status changes
                        removeObject(obj.key)
                        createObjectPreferInventory(obj)
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

    fun isInventoryObject(objectId: String): Boolean {
        return objectKinds[objectId] == ObjectKind.Inventory
    }
    
    /**
     * Disposes resources when the scene is destroyed
     */
    fun dispose() {
        // Dispose the highlight layer
        highlightLayer.dispose()
        
        // Dispose the asset manager
        assetManager.dispose()
        
        // Reset cursor
        document.body?.style?.cursor = "default"
        
        console.log("Disposing SceneObjectManager resources")
    }

    fun onMouseLeave() {
        document.body?.style?.cursor = "default"
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
    private fun handleObjectStatusText(objectId: String, status: String, node: Node) {
        try {
            console.log("[DEBUG_STATUS] Handling status text for object: $objectId, status: '$status'")
            
            // Check if the object already has a status text handle
            val existingHandle = objectStatusTextHandles[objectId]
            console.log("[DEBUG_STATUS] Existing handle: ${existingHandle != null}")
            
            if (status.isBlank()) {
                // If status is blank, remove any existing status text
                if (existingHandle != null) {
                    console.log("[DEBUG_STATUS] Removing status text for object: $objectId")
                    existingHandle.remove()
                    objectStatusTextHandles.remove(objectId)
                    console.log("[DEBUG_STATUS] Successfully removed status text for object: $objectId")
                } else {
                    console.log("[DEBUG_STATUS] No status text to remove for object: $objectId")
                }
            } else {
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
                val childMeshes = node.getChildMeshes(false)
                
                // Find the highest Y point of all bounding boxes
                var highestY = node.position.y
                
                if (childMeshes.isNotEmpty()) {
                    try {
                        // Initialize with the first mesh's maximum Y
                        var maxY = childMeshes[0].getBoundingInfo().maximum.y + childMeshes[0].position.y + node.position.y
                        
                        // Check all other meshes
                        for (i in 1 until childMeshes.size) {
                            val mesh = childMeshes[i]
                            val boundingInfo = mesh.getBoundingInfo()
                            val meshMaxY = boundingInfo.maximum.y + mesh.position.y + node.position.y
                            
                            if (meshMaxY > maxY) {
                                maxY = meshMaxY
                            }
                        }
                        
                        // Use the highest point found
                        highestY = maxY
                        console.log("[DEBUG_STATUS] Highest Y point of bounding boxes: $highestY")
                    } catch (e: Throwable) {
                        console.error("[DEBUG_STATUS] Error finding highest Y point: ${e.message}")
                        // Fall back to node position if there's an error
                        highestY = node.position.y
                    }
                } else {
                    console.log("[DEBUG_STATUS] No child meshes found, using node position")
                }
                
                // Position the status text 0.5 units above the highest point
                val newPosition = Vector3(
                    x = node.position.x,
                    y = highestY + 0.5f,
                    z = node.position.z
                )
                
                if (existingHandle != null) {
                    // If there's an existing handle, update its position

                    console.log("[DEBUG_STATUS] Updating status text position for object: $objectId")
                    console.log("[DEBUG_STATUS] Old position: (${existingHandle.position.x}, ${existingHandle.position.y}, ${existingHandle.position.z})")
                    console.log("[DEBUG_STATUS] New position: (${newPosition.x}, ${newPosition.y}, ${newPosition.z})")
                    
                    existingHandle.position = newPosition
                    console.log("[DEBUG_STATUS] Successfully updated status text position for object: $objectId")
                } else {
                    // Create new status text
                    console.log("[DEBUG_STATUS] Creating new status text for object: $objectId with status: '$status'")
                    val statusTextHandle = worldObjectManager.createObjectStatusText(objectHandle, status)
                    
                    if (statusTextHandle != null) {
                        // Store the handle for future updates
                        objectStatusTextHandles[objectId] = statusTextHandle
                        statusTextHandle.position = newPosition
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
