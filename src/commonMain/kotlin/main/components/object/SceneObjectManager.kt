package main.components.`object`

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import utils.json
import lib.Color3
import lib.CreatePlaneOptions
import lib.HighlightLayer
import lib.MeshBuilder
import lib.Node
import lib.Scene
import lib.StandardMaterial
import lib.Vector3
import main.api.ObjectApi
import main.api.WebSocketApi
import main.components.camera.CameraController
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
    private val cameraController: CameraController
) {
    
    // Currently selected object
    private var selectedObject: String? = null
    
    // Map of object IDs to their meshes
    private val objectMeshes = mutableMapOf<String, dynamic>()
    
    // Currently dragging object
    private var draggingObjectId: String? = null
    
    // Drag state
    private var isDragging = false
    private var dragStartPosition: Vector3? = null
    
    // Track hovered object
    private var hoveredObjectId: String? = null
    
    // Add a highlight layer for the outline effect
    private val highlightLayer = HighlightLayer("objectHighlightLayer", scene)
    
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
                // Get all child meshes from the node
                val childMeshes = node.getChildMeshes(false)
                console.log("[DEBUG_SELECT] Found ${childMeshes.size} child meshes to select")
                
                // Process each child mesh
                childMeshes.forEach { childMesh ->
                    try {
                        // Store the original color to restore it later
                        val material = childMesh.material
                        if (material != null && material.diffuseColor != null) {
                            // Store the original color in the mesh's metadata
                            if (childMesh.metadata == null) {
                                childMesh.metadata = js("{}")
                            }
                            childMesh.metadata.originalMaterial = material
                            
                            // Change the color to highlight the object
                            material.diffuseColor = Color3(1f, 0.6f, 1f)
                            console.log("[DEBUG_SELECT] Changed color for mesh: ${childMesh.name}")
                        }
                    } catch (e: Exception) {
                        console.error("[DEBUG_SELECT] Error processing child mesh: ${childMesh.name}", e)
                    }
                }
                
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
    private fun deselectCurrentObject() {
        selectedObject?.let { objectId ->
            val node = objectMeshes[objectId]
            if (node != null) {
                try {
                    // Get all child meshes from the node
                    val childMeshes = node.getChildMeshes(false)
                    console.log("[DEBUG_DESELECT] Found ${childMeshes.size} child meshes to deselect")
                    
                    // Process each child mesh
                    childMeshes.forEach { childMesh ->
                        try {
                            // Restore the original material if it exists
                            if (childMesh.metadata != null && childMesh.metadata.originalMaterial != null) {
                                val originalMaterial = childMesh.metadata.originalMaterial
                                childMesh.material = originalMaterial
                                console.log("[DEBUG_DESELECT] Restored original material for mesh: ${childMesh.name}")
                            }
                        } catch (e: Exception) {
                            console.error("[DEBUG_DESELECT] Error processing child mesh: ${childMesh.name}", e)
                        }
                    }
                } catch (error: Exception) {
                    console.error("[DEBUG_DESELECT] Error deselecting object", error)
                }
            }
            selectedObject = null
        }
    }
    
    /**
     * Deselects the currently selected object if any
     * 
     * @return true if an object was deselected, false if no object was selected
     */
    fun deselectObject(): Boolean {
        val wasObjectSelected = selectedObject != null
        deselectCurrentObject()
        
        if (wasObjectSelected) {
            console.log("Object deselected")
            onSuccess?.invoke("Object deselected")
        }
        
        return wasObjectSelected
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
        // Find and remove the object from the scene
        // In a real implementation, we would find the mesh by ID and remove it
        console.log("Removing object with ID: $objectId from scene")
        
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

                        node.getChildMeshes().forEach {
                            it.isPickable = true
                        }
                        
                        // Ensure the node has no parent (position is global to scene)
                        node.parent = null
                        console.log("[DEBUG_OBJECT] Set node parent to null to ensure global positioning")
                        
                        // Set the position, rotation, and scale
                        val position = Vector3(obj.position.x.toFloat(), obj.position.y.toFloat(), obj.position.z.toFloat())
                        console.log("[DEBUG_OBJECT] Setting position to: (${position.x}, ${position.y}, ${position.z})")
                        node.position = position
                        
                        // Set rotation (convert from radians if needed)
                        val rotation = Vector3(obj.rotation.x.toFloat(), obj.rotation.y.toFloat(), obj.rotation.z.toFloat())
                        console.log("[DEBUG_OBJECT] Setting rotation to: (${rotation.x}, ${rotation.y}, ${rotation.z})")
                        node.rotation = rotation
                        
                        // Set scale
                        val scale = Vector3(obj.scale.x.toFloat(), obj.scale.y.toFloat(), obj.scale.z.toFloat())
                        console.log("[DEBUG_OBJECT] Setting scale to: (${scale.x}, ${scale.y}, ${scale.z})")
                        node.scaling = scale
                        
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
                    },
                    onError = { errorMessage ->
                        console.error("[DEBUG_OBJECT] Error loading 3D model: $errorMessage")
                    }
                )
            } else {
                console.log("[DEBUG_OBJECT] No file or collection specified, creating fallback sphere")
            }
        } catch (error: Exception) {
            console.error("[DEBUG_OBJECT] Error creating 3D object", error)
            onError?.invoke("Failed to create 3D object: ${error.message ?: "Unknown error"}")
        }
    }

    /**
     * Starts dragging an object
     * 
     * @param objectId The ID of the object to drag
     * @return true if dragging started successfully, false otherwise
     */
    fun startDragging(objectId: String): Boolean {
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
     * @param position The new position for the object
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
                
                // Update the node position
                console.log("Updating object ${draggingObjectId} position to (${position.x}, ${position.y}, ${position.z})")
                node.position = position
                
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
    fun setupWebSocketListeners() {
        console.log("Setting up WebSocket listeners for object updates")
        
        // Get the current world ID from the URL path
        val currentWorldId = window.location.pathname.substring(1)
        
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
                
                // Find and remove the object node
                val node = objectMeshes[objectId]
                if (node != null) {
                    // Remove from scene
                    node.dispose()
                    // Remove from our map
                    objectMeshes.remove(objectId)
                    console.log("Removed object from scene: $objectId")
                }
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
}
