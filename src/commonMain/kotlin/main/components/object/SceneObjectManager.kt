package main.components.`object`

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import lib.AbstractMesh
import lib.Color3
import lib.Color4
import lib.CreateSphereOptions
import lib.GlowLayer
import lib.MeshBuilder
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
) : ObjectManager {
    
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
    
    // Add a glow layer for the outline effect
    private val glowLayer = GlowLayer("objectGlowLayer", scene)
    
    init {
        // Configure the glow layer for white outline
        // Increase intensity to make the glow more visible
        glowLayer.intensity = 1.0f
        glowLayer.blurKernelSize = 32f
        
        console.log("[DEBUG_GLOW] GlowLayer initialized with intensity: ${glowLayer.intensity}, blurKernelSize: ${glowLayer.blurKernelSize}")
    }
    
    /**
     * Highlights an object with a white outline when hovered
     * 
     * @param objectId The ID of the object to highlight
     * @return true if the object was highlighted successfully, false otherwise
     */
    fun highlightObject(objectId: String): Boolean {
        console.log("[DEBUG_GLOW] Attempting to highlight object: $objectId")
        
        // Remove highlight from previously hovered object
        unhighlightObject()
        
        // Find the object mesh
        val mesh = objectMeshes[objectId]
        if (mesh != null) {
            console.log("[DEBUG_GLOW] Found mesh for object: $objectId")
            try {
                // Add the mesh to the glow layer
                console.log("[DEBUG_GLOW] Adding mesh to glow layer")
                glowLayer.addIncludedOnlyMesh(mesh)
                
                // Store the hovered object ID
                hoveredObjectId = objectId
                
                // Change cursor to grab
                document.body?.style?.cursor = "grab"
                console.log("[DEBUG_GLOW] Changed cursor to grab")
                
                console.log("[DEBUG_GLOW] Successfully highlighted object: $objectId")
                return true
            } catch (error: Exception) {
                console.error("[DEBUG_GLOW] Error highlighting object", error)
                return false
            }
        } else {
            console.log("[DEBUG_GLOW] No mesh found for object: $objectId")
        }
        return false
    }
    
    /**
     * Removes highlight from the currently hovered object
     */
    fun unhighlightObject() {
        console.log("[DEBUG_GLOW] Attempting to unhighlight object")
        
        hoveredObjectId?.let { objectId ->
            console.log("[DEBUG_GLOW] Current hovered object: $objectId")
            
            val mesh = objectMeshes[objectId]
            if (mesh != null) {
                console.log("[DEBUG_GLOW] Found mesh for object: $objectId")
                try {
                    // Remove the mesh from the glow layer
                    console.log("[DEBUG_GLOW] Removing mesh from glow layer")
                    glowLayer.removeIncludedOnlyMesh(mesh)
                    
                    // Reset cursor
                    document.body?.style?.cursor = "default"
                    console.log("[DEBUG_GLOW] Reset cursor to default")
                    
                    console.log("[DEBUG_GLOW] Successfully unhighlighted object: $objectId")
                } catch (error: Exception) {
                    console.error("[DEBUG_GLOW] Error unhighlighting object", error)
                }
            } else {
                console.log("[DEBUG_GLOW] No mesh found for object: $objectId")
            }
            hoveredObjectId = null
        } ?: run {
            console.log("[DEBUG_GLOW] No object currently highlighted")
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
        
        // Find the object mesh
        val mesh = objectMeshes[objectId]
        if (mesh != null) {
            // Highlight the object (e.g., change its color or add a glow effect)
            try {
                // Store the original color to restore it later
                val originalColor = mesh.material?.diffuseColor
                if (originalColor != null) {
                    mesh.originalColor = originalColor
                }
                
                // Change the color to highlight the object
                mesh.material.diffuseColor = Color3(1f, 0.6f, 1f)
                
                // Store the selected object ID
                selectedObject = objectId
                
                console.log("Object selected: $objectId")
                onSuccess?.invoke("Object selected")
                return true
            } catch (error: Exception) {
                console.error("Error selecting object", error)
                onError?.invoke("Failed to select object: ${error.message ?: "Unknown error"}")
                return false
            }
        } else {
            console.warn("Object mesh not found: $objectId")
            onError?.invoke("Object not found in scene")
            return false
        }
    }
    
    /**
     * Deselects the currently selected object
     */
    private fun deselectCurrentObject() {
        selectedObject?.let { objectId ->
            val mesh = objectMeshes[objectId]
            if (mesh != null) {
                try {
                    // Restore the original color
                    val originalColor = mesh.originalColor
                    if (originalColor != null) {
                        mesh.material.diffuseColor = originalColor
                    }
                } catch (error: Exception) {
                    console.error("Error deselecting object", error)
                }
            }
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
        // Find the object mesh
        val mesh = objectMeshes[objectId]
        if (mesh != null) {
            try {
                // Select the object to highlight it
                selectObject(objectId)
                
                // Move the camera to focus on the object
                cameraController.focusOnPosition(mesh.position)
                
                console.log("Camera focused on object: $objectId")
                onSuccess?.invoke("Located object")
                return true
            } catch (error: Exception) {
                console.error("Error locating object", error)
                onError?.invoke("Failed to locate object: ${error.message ?: "Unknown error"}")
                return false
            }
        } else {
            console.warn("Object mesh not found: $objectId")
            onError?.invoke("Object not found in scene")
            return false
        }
    }
    
    /**
     * Removes an object from the scene
     * 
     * @param objectId The ID of the object to remove
     */
    override fun removeObject(objectId: String) {
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
                
                // Send the update over WebSocket for real-time updates
                WebSocketApi.addObject(savedObj)
                
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
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
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
            // Check if this is a test object
            val isTestObject = obj.key.startsWith("test_object_")
            
            // Create a sphere to represent the object
            val options: dynamic = object {}
            
            // Make test objects larger for easier targeting
            if (isTestObject) {
                options.diameter = 2.0
                console.log("[DEBUG_OBJECT] Creating test sphere with diameter: 2.0")
            } else {
                options.diameter = 1.0
                console.log("[DEBUG_OBJECT] Creating sphere with diameter: 1.0")
            }
            
            val sphere = MeshBuilder.CreateSphere(obj.key, options, scene)
            
            // Set the position
            val position = Vector3(obj.position.x.toFloat(), obj.position.y.toFloat(), obj.position.z.toFloat())
            console.log("[DEBUG_OBJECT] Setting position to: (${position.x}, ${position.y}, ${position.z})")
            sphere.position = position
            
            // Create a material with appropriate color
            console.log("[DEBUG_OBJECT] Creating material for object: ${obj.key}")
            val material = StandardMaterial("material_${obj.key}", scene)
            
            // Use bright red for test objects, purple for regular objects
            if (isTestObject) {
                material.diffuseColor = Color3(1.0f, 0f, 0f) // Bright red color for test objects
                console.log("[DEBUG_OBJECT] Using bright red color for test object")
            } else {
                material.diffuseColor = Color3(0.5f, 0f, 0.5f) // Purple color for regular objects
            }
            
            material.specularColor = Color3(0.2f, 0.2f, 0.2f)
            
            // Apply the material to the sphere
            console.log("[DEBUG_OBJECT] Applying material to sphere")
            sphere.material = material
            
            // Store the mesh in our map for future reference
            console.log("[DEBUG_OBJECT] Storing mesh in objectMeshes map with key: ${obj.key}")
            objectMeshes[obj.key] = sphere
            
            // Verify the mesh was added to the map
            val storedMesh = objectMeshes[obj.key]
            if (storedMesh != null) {
                console.log("[DEBUG_OBJECT] Successfully stored mesh in map. Current map size: ${objectMeshes.size}")
            } else {
                console.error("[DEBUG_OBJECT] Failed to store mesh in map!")
            }
            
            console.log("[DEBUG_OBJECT] Successfully created 3D object: ${obj.key}")
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
        // Find the object mesh
        val mesh = objectMeshes[objectId]
        if (mesh != null) {
            try {
                // Select the object to highlight it
                selectObject(objectId)
                
                // Store the dragging state
                draggingObjectId = objectId
                isDragging = true
                dragStartPosition = Vector3(mesh.position.x, mesh.position.y, mesh.position.z)
                
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
            console.warn("Object mesh not found: $objectId")
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
            // Find the object mesh
            val mesh = objectMeshes[draggingObjectId!!]
            if (mesh != null) {
                // Update the mesh position
                console.log("Updating object ${draggingObjectId} position to (${position.x}, ${position.y}, ${position.z})")
                mesh.position = position
                
                return true
            } else {
                console.warn("Object mesh not found for dragging: ${draggingObjectId}")
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
            // Find the object mesh
            val mesh = objectMeshes[draggingObjectId!!]
            if (mesh != null) {
                // Convert the BabylonJS Vector3 to our Position model
                val newPosition = Position(
                    x = mesh.position.x.toFloat(),
                    y = mesh.position.y.toFloat(),
                    z = mesh.position.z.toFloat()
                )
                
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
    fun setWorldMeshes(meshes: Array<dynamic>) {
        @Suppress("UNCHECKED_CAST")
        this.worldMeshes = meshes as Array<AbstractMesh>
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
                val existingMesh = objectMeshes[obj.key]
                if (existingMesh != null) {
                    // Update existing object position
                    existingMesh.position = Vector3(obj.position.x.toFloat(), obj.position.y.toFloat(), obj.position.z.toFloat())
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
                
                // Find and remove the object mesh
                val mesh = objectMeshes[objectId]
                if (mesh != null) {
                    // Remove from scene
                    mesh.dispose()
                    // Remove from our map
                    objectMeshes.remove(objectId)
                    console.log("Removed object from scene: $objectId")
                }
            } catch (error: Exception) {
                console.error("Error processing object removal", error)
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
                    
                    // Create a test object for debugging
                    console.log("[DEBUG_LOAD] Creating a test object for debugging")
                    val testObject = Object(
                        title = "Test Object",
                        worldId = worldId,
                        position = models.Position(0f, 0.5f, 0f),  // Position at origin, slightly above ground
                        rotation = models.Rotation(0f, 0f, 0f),
                        scale = models.Scale(1f, 1f, 1f)
                    ).apply {
                        key = "test_object_${window.performance.now().toInt()}"
                    }
                    
                    // Create the test object in the scene
                    createObjectInScene(testObject)
                    console.log("[DEBUG_LOAD] Created test object with ID: ${testObject.key}")
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
     * @return Map of object IDs to their meshes
     */
    fun getObjectMeshes(): Map<String, AbstractMesh> {
        return objectMeshes
    }
    
    /**
     * Disposes resources when the scene is destroyed
     */
    fun dispose() {
        // Dispose the glow layer
        glowLayer.dispose()
        
        // Reset cursor
        document.body?.style?.cursor = "default"
        
        console.log("Disposing SceneObjectManager resources")
    }
}
