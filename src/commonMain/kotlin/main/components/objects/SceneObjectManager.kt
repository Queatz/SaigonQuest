package main.components.objects

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Color3
import lib.CreateSphereOptions
import lib.HighlightLayer
import lib.MeshBuilder
import lib.Node
import lib.PBRMaterial
import lib.Scene
import lib.TransformNode
import lib.Vector3
import main.api.ObjectApi
import main.api.WebSocketApi
import main.api.addObject
import main.api.updateObject
import main.components.camera.CameraController
import main.components.shadow.ShadowManager
import main.components.world.WorldObjectManager
import models.Object
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
    private val objectStatusTextHandles = mutableMapOf<String, main.components.world.WorldObjectHandle>()
    
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
        highlightLayer.blurHorizontalSize = 2
        highlightLayer.blurVerticalSize = 2

        // Set additional properties if available
        try {
            // Set the outer glow color to a bright, high-contrast color
            highlightLayer.outerGlow = true
            
            // Set the inner glow color to false to create only an outline
            highlightLayer.innerGlow = false
            
            console.log("[DEBUG_HIGHLIGHT] Set additional properties: outerGlow=true, innerGlow=false")
        } catch (e: Exception) {
            console.log("[DEBUG_HIGHLIGHT] Could not set additional properties: ${e.message}")
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
            } catch (error: Exception) {
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
                } catch (error: Exception) {
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
            } catch (error: Exception) {
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
            } catch (error: Exception) {
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
        } catch (e: Exception) {
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
                
                // Send WebSocket notification to update other clients
                WebSocketApi.updateObject(savedObj)
                
                console.log("Object position updated and persisted: $objectId")
                
                // Provide success feedback
                onSuccess?.invoke("Object '${savedObj.title}' moved successfully")
            } catch (error: Exception) {
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
     * Handles dropping an object into the scene
     * 
     * @param x The x coordinate of the drop
     * @param y The y coordinate of the drop
     * @param data The serialized object data
     * @param worldId The ID of the current world
     */
    fun handleObjectDrop(x: Int, y: Int, data: String, worldId: String) {
        console.log("Object dropped at ($x, $y) in world $worldId")
        
        try {
            // Parse the object data
            val obj = json.decodeFromString(Object.serializer(), data)
            
            // Calculate the 3D position from the 2D screen coordinates
            val position = calculatePositionFromScreenCoordinates(x, y)
            
            // Update the object with the new position and world ID
            val updatedObj = obj.copy(
                position = position,
                worldId = worldId
            )
            
            // Persist the object placement
            coroutineScope.launch {
                try {
                    // If the object has a key, update it; otherwise create a new one
                    val savedObj = if (obj.key.isNotEmpty()) {
                        ObjectApi.updateObject(obj.key, updatedObj)
                    } else {
                        ObjectApi.createObject(updatedObj)
                    }
                    
                    // Send the add object event over WebSocket for real-time updates
                    WebSocketApi.addObject(savedObj)
                    
                    // Create a 3D representation of the object in the scene
                    createObjectInScene(savedObj)
                    
                    console.log("Object successfully placed and persisted: ${savedObj.key}")
                    
                    // Provide success feedback
                    onSuccess?.invoke("Object '${savedObj.title}' placed successfully")
                } catch (error: Exception) {
                    console.error("Error persisting object placement", error)
                    
                    // Provide error feedback
                    onError?.invoke("Failed to place object: ${error.message ?: "Unknown error"}")
                }
            }
        } catch (error: Exception) {
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
    private fun calculatePositionFromScreenCoordinates(x: Int, y: Int): Position {
        // In a real implementation, we would use raycasting to find the intersection with the ground
        // For now, we'll just create a simple position
        return Position(x.toFloat() / 100, 0f, y.toFloat() / 100)
    }
    
    /**
     * Creates a 3D representation of an object in the scene
     * 
     * @param obj The object to create
     */
    private fun createObjectInScene(obj: Object) {
        console.log("[DEBUG_OBJECT] Creating 3D representation for object: ${obj.key}")
        
        try {
            // Create a position, rotation, and scale vectors from the object properties
            val position = Vector3(obj.position.x, obj.position.y, obj.position.z)
            val rotation = Vector3(obj.rotation.x, obj.rotation.y, obj.rotation.z)
            val scale = Vector3(obj.scale.x, obj.scale.y, obj.scale.z)
            
            // Check if the object has a file and collection specified
            if (obj.file.isNotEmpty() && obj.collection.isNotEmpty()) {
                console.log("[DEBUG_OBJECT] Loading 3D model from file: ${obj.file}, collection: ${obj.collection}")
                
                // Create a fallback sphere first so the object appears instantly
                val fallbackSphere = createFallbackSphere(obj.key, position, rotation, scale)
                
                // Store the fallback sphere in our map for future reference
                console.log("[DEBUG_OBJECT] Storing fallback sphere in objectMeshes map with key: ${obj.key}")
                objectMeshes[obj.key] = fallbackSphere
                
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
                        }.forEach {
                            it.isPickable = true
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
                        
                        // Remove the fallback sphere (now it's a TransformNode)
                        (fallbackSphere as lib.TransformNode).dispose()
                        
                        // Store the mesh in our map for future reference
                        console.log("[DEBUG_OBJECT] Storing mesh in objectMeshes map with key: ${obj.key}")
                        objectMeshes[obj.key] = node
                        
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
                    }
                )
            } else {
                console.log("[DEBUG_OBJECT] No file or collection specified, creating fallback sphere")
                
                // Create a permanent fallback sphere for objects without file and collection
                val fallbackSphere = createFallbackSphere(obj.key, position, rotation, scale)
                
                // Store the fallback sphere in our map for future reference
                console.log("[DEBUG_OBJECT] Storing fallback sphere in objectMeshes map with key: ${obj.key}")
                objectMeshes[obj.key] = fallbackSphere
                
                // Handle status text
                handleObjectStatusText(obj.key, obj.status, fallbackSphere)
            }
        } catch (error: Exception) {
            console.error("[DEBUG_OBJECT] Error creating 3D object", error)
            onError?.invoke("Failed to create 3D object: ${error.message ?: "Unknown error"}")
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
        val container = lib.TransformNode("container_$name", scene)
        
        // Set the container's position, rotation, and scale
        container.position = position
        container.rotation = rotation
        container.scaling = scale
        
        // Create a sphere mesh
        val sphere = MeshBuilder.CreateSphere(
            name,
            object : CreateSphereOptions {
                override val diameter = 1.0f
                override val segments = 16
            },
            scene
        )
        
        // Create a white material
        val material = PBRMaterial("material_$name", scene)
        material.albedoColor = Color3(1f, 1f, 1f)  // White color
        material.metallic = 0.0f
        material.roughness = 0.4f
        
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
            } catch (error: Exception) {
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
                console.warn("Object node not found for dragging: ${draggingObjectId}")
            }
            return false
        } catch (error: Exception) {
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
        } catch (error: Exception) {
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
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    /**
     * Sets the world meshes for collision detection
     * 
     * @param worldMeshes The array of world meshes
     */
    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        @Suppress("UNCHECKED_CAST")
        this.worldMeshes = meshes
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
                    // Update existing object position
                    existingNode.position = Vector3(obj.position.x.toFloat(), obj.position.y.toFloat(), obj.position.z.toFloat())
                    console.log("Updated existing object: ${obj.key}")
                } else {
                    // Create new object
                    createObjectInScene(obj)
                }
            } catch (error: Exception) {
                console.error("Error processing object update", error)
            }
        }
        
        WebSocketApi.addListener(WebSocketAction.REMOVE_OBJECT) { message ->
            try {
                // The data is just the object ID as a string
                val objectId = message.data
                
                console.log("Received object removal via WebSocket: $objectId")
                
                // Use the removeObject method to clean up the object and its status text
                removeObject(objectId)
            } catch (error: Exception) {
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
                    // Update the object position
                    node.position = Vector3(obj.position.x, obj.position.y, obj.position.z)
                    console.log("Updated object position: ${obj.key} to (${obj.position.x}, ${obj.position.y}, ${obj.position.z})")
                    
                    // Handle status text
                    handleObjectStatusText(obj.key, obj.status, node)
                } else {
                    // Object doesn't exist in our scene yet, create it
                    console.log("Object not found in scene, creating it: ${obj.key}")
                    createObjectInScene(obj)
                }
            } catch (error: Exception) {
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
                        createObjectInScene(obj)
                    }
                }
                
                // Verify the number of objects created
                console.log("[DEBUG_LOAD] Finished creating 3D objects. Total objects in map: ${objectMeshes.size}")
                
                // List all object IDs for debugging
                val objectIds = objectMeshes.keys.joinToString(", ")
                console.log("[DEBUG_LOAD] Object IDs in map: $objectIds")
            } catch (error: Exception) {
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
            } catch (e: Exception) {
                console.error("[DEBUG_MESHES] Error getting meshes for object: $objectId", e)
            }
        }
        
        return result
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
                val objectHandle = object : main.components.world.WorldObjectHandle {
                    override val id: String = objectId
                    override var node: Node = node
                    override fun remove() {
                        // No-op for now
                    }
                    override fun updateDirection(direction: main.components.world.CharacterDirection) {
                        // No-op for now
                    }
                    override fun updateActivity(activity: main.components.world.PlayerActivity?) {
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
                    } catch (e: Exception) {
                        console.error("[DEBUG_STATUS] Error finding highest Y point: ${e.message}")
                        // Fall back to node position if there's an error
                        highestY = node.position.y
                    }
                } else {
                    console.log("[DEBUG_STATUS] No child meshes found, using node position")
                }
                
                // Position the status text 0.5 units above the highest point
                val newPosition = Vector3(
                    node.position.x,
                    highestY + 0.5f,
                    node.position.z
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
        } catch (e: Exception) {
            console.error("[DEBUG_STATUS] Error handling status text for object: $objectId", e)
        }
    }
}
