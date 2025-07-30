package main.components.`object`

import kotlinx.serialization.json.Json
import lib.AbstractMesh
import lib.Color3
import lib.Matrix
import lib.Mesh
import lib.MeshBuilder
import lib.Ray
import lib.Scene
import lib.StandardMaterial
import lib.Vector3
import main.api.WebSocketApi
import main.components.camera.CameraController
import models.Object
import models.Position
import models.Rotation
import models.Scale

/**
 * Manages objects in the 3D scene.
 * 
 * This class encapsulates all object-related functionality including:
 * - Object creation and initialization
 * - Object positioning
 * - Object interaction
 * - WebSocket communication for object updates
 */
class ObjectManager(
    private val scene: Scene,
    private val cameraController: CameraController
) {
    
    // Object tracking
    private val sceneObjects = mutableMapOf<String, Object>()
    private val objectMeshes = mutableMapOf<String, Mesh>()
    
    // World meshes for collision detection
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    // Player and move circle meshes to exclude from collision detection
    private var playerMesh: Mesh? = null
    private var moveCircleMesh: Mesh? = null
    
    /**
     * Sets the world meshes for collision detection.
     * 
     * @param meshes The array of world meshes
     */
    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        worldMeshes = meshes
    }
    
    /**
     * Sets the player mesh to exclude from collision detection.
     * 
     * @param mesh The player mesh
     */
    fun setPlayerMesh(mesh: Mesh?) {
        playerMesh = mesh
    }
    
    /**
     * Sets the move circle mesh to exclude from collision detection.
     * 
     * @param mesh The move circle mesh
     */
    fun setMoveCircleMesh(mesh: Mesh?) {
        moveCircleMesh = mesh
    }
    
    /**
     * Creates a cube for an object.
     * 
     * @param obj The object data
     */
    fun createObjectCube(obj: Object) {
        // Store the object in our map
        sceneObjects[obj.key] = obj
        
        // Remove existing mesh if any
        objectMeshes[obj.key]?.dispose()
        
        // Create a box mesh for the object
        val cube = MeshBuilder.CreateBox("object-${obj.key}", 
            object {
                val width = 1.0f
                val height = 1.0f
                val depth = 1.0f
            }, scene)
        
        // Position the cube
        cube.position = Vector3(
            obj.position.x,
            obj.position.y,
            obj.position.z
        )
        
        // Apply rotation if specified
        if (obj.rotation != null) {
            cube.rotation = Vector3(
                obj.rotation.x,
                obj.rotation.y,
                obj.rotation.z
            )
        }
        
        // Apply scaling if specified
        if (obj.scale != null) {
            cube.scaling = Vector3(
                obj.scale.x,
                obj.scale.y,
                obj.scale.z
            )
        }
        
        // Create a material for the cube
        val material = StandardMaterial("material-${obj.key}", scene)
        material.diffuseColor = Color3(0.2f, 0.6f, 1.0f) // Blue color
        
        // Apply the material to the cube
        cube.material = material
        
        // Store the mesh in our map
        objectMeshes[obj.key] = cube
    }
    
    /**
     * Handles an object drop event.
     * 
     * @param x The x coordinate of the drop point
     * @param y The y coordinate of the drop point
     * @param data The serialized object data
     * @param worldId The ID of the current world
     */
    fun handleObjectDrop(x: Int, y: Int, data: String, worldId: String) {
        try {
            // Parse the dropped object data
            val json = Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            }
            
            // Safely deserialize the object, handling potential type mismatches
            val droppedObject = try {
                json.decodeFromString<Object>(data)
            } catch (e: Exception) {
                console.error("Error deserializing dropped object", e)
                // Create a default object if deserialization fails
                Object(
                    title = "Error Object",
                    categories = emptyList(),
                    data = "{}",
                    worldId = worldId,
                    position = Position(0f, 0f, 0f),
                    rotation = Rotation(0f, 0f, 0f),
                    scale = Scale(1f, 1f, 1f)
                )
            }
            
            // Create a ray from the camera through the drop point
            val ray = scene.createPickingRay(
                x,
                y,
                Matrix.Identity(),
                cameraController.getCamera()
            )
            
            // Check for intersection with world meshes
            var closestDistance = Float.MAX_VALUE
            var closestPoint: Vector3? = null
            
            for (mesh in worldMeshes) {
                // Skip player and move circle meshes
                if (mesh === playerMesh || mesh === moveCircleMesh) continue
                
                val hit = ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    val distance = Vector3.Distance(cameraController.getCamera().position, hit.pickedPoint!!)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestPoint = hit.pickedPoint
                    }
                }
            }
            
            // If we found a valid point, create the object there
            if (closestPoint != null) {
                // Create a new object with the position from the drop point
                val newObject = droppedObject.copy(
                    position = Position(
                        x = closestPoint.x,
                        y = closestPoint.y + 0.5f, // Offset to place on top of the ground
                        z = closestPoint.z
                    )
                )
                
                // Send the object creation event over WebSocket
                WebSocketApi.addObject(newObject)
                
                // Create a cube to represent the object
                createObjectCube(newObject)
            }
        } catch (e: Exception) {
            console.error("Error handling object drop", e)
        }
    }
    
    /**
     * Removes an object from the scene.
     * 
     * @param objectId The ID of the object to remove
     */
    fun removeObject(objectId: String) {
        // Remove the object from our maps
        sceneObjects.remove(objectId)
        
        // Remove and dispose the mesh
        objectMeshes[objectId]?.dispose()
        objectMeshes.remove(objectId)
    }
    
    /**
     * Sets up WebSocket event listeners for object events.
     */
    fun setupWebSocketListeners() {
        // Add WebSocket event listener for adding objects
        WebSocketApi.addListener("addObject") { message ->
            try {
                val json = Json { 
                    ignoreUnknownKeys = true 
                    isLenient = true
                }
                
                // Safely deserialize the object, handling potential type mismatches
                try {
                    val obj = json.decodeFromString<Object>(message.data)
                    createObjectCube(obj)
                } catch (e: Exception) {
                    console.error("Error deserializing object from WebSocket", e)
                }
            } catch (e: Exception) {
                console.error("Error handling addObject event", e)
            }
        }
        
        // Add WebSocket event listener for removing objects
        WebSocketApi.addListener("removeObject") { message ->
            try {
                val objectId = message.data.trim('"') // Remove quotes if present
                removeObject(objectId)
            } catch (e: Exception) {
                console.error("Error handling removeObject event", e)
            }
        }
    }
    
    /**
     * Gets all scene objects.
     * 
     * @return The map of scene objects
     */
    fun getSceneObjects(): Map<String, Object> {
        return sceneObjects
    }
    
    /**
     * Gets all object meshes.
     * 
     * @return The map of object meshes
     */
    fun getObjectMeshes(): Map<String, Mesh> {
        return objectMeshes
    }
    
    /**
     * Disposes of the object manager resources.
     */
    fun dispose() {
        // Remove WebSocket listeners
        WebSocketApi.removeAllListeners("addObject")
        WebSocketApi.removeAllListeners("removeObject")
        
        // Dispose all object meshes
        objectMeshes.values.forEach { it.dispose() }
        objectMeshes.clear()
        sceneObjects.clear()
    }
}
