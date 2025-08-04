package main.components.world

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import lib.AbstractMesh
import lib.Node
import lib.Scene
import lib.Vector3
import main.api.ApiClient
import main.api.WebSocketApi
import models.WebSocketAction

/**
 * Manages world objects in a scene
 */
class WorldObjectManager(private val scene: Scene) {
    private val objects = mutableMapOf<String, WorldObject>()

    /**
     * Adds an object to the scene
     * 
     * @param obj The object to add
     * @return A handle to the added object, or null if adding failed
     */
    fun addObject(obj: WorldObject): WorldObjectHandle? {
        // Generate a unique ID if not already set
        if (obj.id.isEmpty()) {
            obj.id = generateUniqueId()
        }
        
        // Render the object in the scene
        val node = obj.render(scene) ?: return null
        
        // Store the object
        objects[obj.id] = obj
        
        // Return a handle to the object
        return createObjectHandle(obj, node)
    }
    
    /**
     * Removes an object from the scene
     * 
     * @param objectId The ID of the object to remove
     */
    fun removeObject(objectId: String) {
        val obj = objects[objectId] ?: return
        
        // Dispose the object's resources
        obj.dispose()
        
        // Remove from our tracking
        objects.remove(objectId)
    }
    
    /**
     * Removes all objects from the scene
     */
    fun removeAll() {
        // Dispose all objects
        objects.values.forEach { it.dispose() }
        
        // Clear the map
        objects.clear()
    }
    
    /**
     * Updates all objects
     * 
     * @param deltaTime Time since last update in seconds
     */
    fun update(deltaTime: Float) {
        objects.values.forEach { it.update(deltaTime) }
    }
    
    /**
     * Creates a character object
     * 
     * @param spriteSheet The sprite sheet to use
     * @param direction Initial direction
     * @param position Initial position
     * @return A handle to the created character
     */
    fun createCharacter(
        spriteSheet: SpriteSheet,
        direction: CharacterDirection,
        position: Vector3 = Vector3(0f, 0f, 0f)
    ): WorldObjectHandle? {
        val character = Character(spriteSheet, direction)
        character.position = position
        
        return addObject(character)
    }
    
    /**
     * Creates a model object
     * 
     * @param mesh The mesh to use
     * @param position Initial position
     * @return A handle to the created model
     */
    fun createModel(
        mesh: AbstractMesh,
        position: Vector3 = Vector3(0f, 0f, 0f)
    ): WorldObjectHandle? {
        val model = Model(mesh)
        model.position = position
        
        return addObject(model)
    }
    
    /**
     * Creates a text billboard that always faces the camera
     * 
     * This method creates a TextBillboard object that displays 2D text in the 3D scene.
     * The text is rendered on a plane that always faces the camera from any angle using
     * BabylonJS's built-in billboard mode.
     * 
     * Example usage:
     * ```
     * // Create a text billboard above an object
     * val textHandle = worldObjectManager.createTextBillboard(
     *     text = "Object Name",
     *     position = Vector3(x, y + 1.2f, z),
     *     fontSize = 18,
     *     textColor = "white",
     *     backgroundColor = "#00000080"  // Semi-transparent black background
     * )
     * 
     * // Later, you can update the position
     * textHandle?.position = newPosition
     * 
     * // And remove it when no longer needed
     * textHandle?.remove()
     * ```
     * 
     * @param text The text to display
     * @param position The position in 3D space
     * @param fontSize The font size (default: 24)
     * @param textColor The text color (default: white)
     * @param backgroundColor The background color (default: null, which means transparent). When a color is supplied, a rounded rectangle with 6px corner radius is rendered behind the text.
     * @return A handle to the created text billboard
     */
    fun createTextBillboard(
        text: String,
        position: Vector3,
        fontSize: Int = 24,
        textColor: String = "white",
        backgroundColor: String? = null
    ): WorldObjectHandle? {
        val textBillboard = TextBillboard(
            scene = scene,
            text = text,
            fontSize = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor ?: "transparent"
        )
        textBillboard.position = position
        
        return addObject(textBillboard)
    }
    
    /**
     * Sets up WebSocket listeners for object updates
     */
    fun setupWebSocketListeners() {
        // Listen for object updates from the server
        WebSocketApi.addListener(WebSocketAction.ADD_OBJECT) { message ->
            // Parse message and update objects as needed
            // This is just a placeholder - actual implementation would depend on your WebSocket API
            console.log("Received ADD_OBJECT message: ${message.data}")
        }
        
        WebSocketApi.addListener(WebSocketAction.REMOVE_OBJECT) { message ->
            // Parse message and remove object
            console.log("Received REMOVE_OBJECT message: ${message.data}")
            try {
                val objectId = ApiClient.json.decodeFromString<String>(message.data)
                removeObject(objectId)
            } catch (e: Exception) {
                console.error("Error parsing REMOVE_OBJECT message", e)
            }
        }
    }
    
    /**
     * Disposes all resources used by the manager
     */
    fun dispose() {
        removeAll()
    }
    
    private fun generateUniqueId(): String {
        // Use current time and a counter to generate a unique ID
        val timestamp = window.performance.now().toInt()
        val counter = (idCounter++).toString().padStart(4, '0')
        return "obj_${timestamp}_$counter"
    }
    
    // Counter for generating unique IDs
    private var idCounter = 0
    
    private fun createObjectHandle(obj: WorldObject, node: Node): WorldObjectHandle {
        return object : WorldObjectHandle {
            override val id: String = obj.id

            override var node: Node = node
            
            override fun remove() {
                removeObject(id)
            }
            
            override fun updateDirection(direction: CharacterDirection) {
                // Only update direction if this is a Character object
                if (obj is Character) {
                    obj.direction = direction
                }
            }
            
            override fun getMeshes(): Array<AbstractMesh> {
                return node.getChildMeshes(false)
            }
        }
    }
}
