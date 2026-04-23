package main.components.world

import kotlinx.browser.window
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import lib.AbstractMesh
import lib.Node
import lib.Scene
import lib.TransformNode
import lib.Vector3
import main.api.ApiClient
import main.api.UserApi
import main.api.WebSocketApi
import main.api.userNearby
import main.components.SceneManager
import main.components.objects.setObjectInteractable
import main.components.objects.setObjectNearby
import main.components.world.WorldLoader
import main.components.shadow.ShadowManager
import models.User
import models.WebSocketAction
import models.ObjectTypingData
import models.Sex

/**
 * Manages world objects in a scene
 */
class WorldObjectManager(private val scene: Scene) {
    private val objects = mutableMapOf<String, WorldObject>()
    private val objectNodes = mutableMapOf<String, Node>()
    private val typingHudHandles = mutableMapOf<String, WorldObjectHandle>()
    private val typingTimeouts = mutableMapOf<String, Int>()
    
    // Performance caches
    private val cachedMeshes = mutableMapOf<String, List<AbstractMesh>>()
    private val cachedHighestY = mutableMapOf<String, Float>()
    
    // Shadow manager reference for adding shadow support to objects
    private var shadowManager: ShadowManager? = null

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
        // Initialize object's visual state (UVs, direction, etc.) immediately so it's visible on first frame
        try { obj.update(0f) } catch (_: Throwable) {}
        
        // Store the object and node
        objects[obj.id] = obj
        objectNodes[obj.id] = node
        
        // Create a handle to the object
        val handle = createObjectHandle(obj, node)

        // Register with performance engine
        WorldLoader.getCurrentPerformanceEngine()?.let { engine ->
            val meshes = handle.getMeshes()
            meshes.forEach { engine.registerObjectMesh(it) }
        }

        // Add shadow support if shadow manager is available
        shadowManager?.let { manager ->
            // Get all meshes for this object (recursive to catch character layers)
            val meshes = handle.getMeshes()
            
            // Skip shadow casting for text billboards (they shouldn't cast shadows)
            if (obj !is TextBillboard && meshes.isNotEmpty()) {
                // Add all meshes as shadow casters
                manager.addShadowCasters(meshes)
                
                // Configure all meshes to receive shadows
                manager.configureShadowReceivers(meshes)
                
                println("[SHADOW_DEBUG] Added shadow support for object ${obj.id} with ${meshes.size} meshes")
            }
        } ?: run {
            println("[SHADOW_DEBUG] Shadow Manager is null")
        }
        
        // Return the handle
        return handle
    }
    
    /**
     * Removes an object from the scene
     * 
     * @param objectId The ID of the object to remove
     */
    fun removeObject(objectId: String) {
        val obj = objects[objectId] ?: return
        val node = objectNodes[objectId]
        
        // Remove from shadow manager if node exists
        node?.let { n ->
            val meshes = n.getChildMeshes(false)
            shadowManager?.let { manager ->
                meshes.forEach { manager.removeShadowCaster(it) }
            }
            // Remove from performance engine
            WorldLoader.getCurrentPerformanceEngine()?.let { engine ->
                meshes.forEach { engine.unregisterObjectMesh(it) }
            }
        }

        // Dispose the object's resources
        obj.dispose()
        
        // Remove typing HUD if present
        typingHudHandles.remove(objectId)?.remove()
        
        // Clear any timeouts
        typingTimeouts.remove(objectId)?.let { window.clearTimeout(it) }
        
        // Remove from our tracking
        objects.remove(objectId)
        objectNodes.remove(objectId)
        cachedMeshes.remove(objectId)
        cachedHighestY.remove(objectId)
    }
    
    /**
     * Removes all objects from the scene
     */
    fun removeAll() {
        // Dispose all objects
        objects.values.forEach { it.dispose() }
        
        // Remove typing HUDs
        typingHudHandles.values.forEach { it.remove() }
        typingHudHandles.clear()
        typingTimeouts.values.forEach { window.clearTimeout(it) }
        typingTimeouts.clear()
        
        // Clear the maps
        objects.clear()
        objectNodes.clear()
        cachedMeshes.clear()
        cachedHighestY.clear()
    }
    
    /**
     * Updates all objects
     * 
     * @param deltaTime Time since last update in seconds
     */
    // Proximity detection state
    private var lastProximityCheckMs: Double = 0.0
    private val enteredProximity = mutableSetOf<String>()
    private val proximityIntervalMs: Double = 500.0

    fun update(deltaTime: Float) {
        objects.values.forEach { it.update(deltaTime) }

        val anyAnimationsPlaying = try {
            scene.animationGroups.any { it.isPlaying }
        } catch (_: Throwable) {
            false
        }

        // Keep typing HUD billboards positioned above their objects
        typingHudHandles.forEach { (objectId, handle) ->
            val node = objectNodes[objectId] ?: return@forEach
            
            // Task 3: Mesh Caching
            val objectMeshes = cachedMeshes.getOrPut(objectId) {
                node.getChildMeshes(false).filter { !DepthPicking.isStatusBillboardMesh(it) }
            }
            
            // Task 1: Floating UI Caching
            // Only update based on bounding box if the object has active animations or it's the first time
            val isFirstTime = !cachedHighestY.containsKey(objectId)
            val hasAnimations = node.animations.isNotEmpty() ||
                                objectMeshes.any { it.animations.isNotEmpty() } ||
                                anyAnimationsPlaying

            val p = node.position
            var highestY = cachedHighestY[objectId] ?: p.y

            // If any animation is playing, we re-calculate, but we can throttle this 
            // or only do it if the object itself is likely to be moving.
            // For now, let's at least avoid the calculation if nothing is playing.
            if (hasAnimations || isFirstTime) {
                if (objectMeshes.isNotEmpty()) {
                    if (node is TransformNode) {
                        node.computeWorldMatrix(true)
                    }

                    var maxY = -1e9f
                    var found = false
                    objectMeshes.forEach { mesh ->
                        try {
                            mesh.computeWorldMatrix(true)
                            val meshMaxY = mesh.getBoundingInfo().boundingBox.maximumWorld.y
                            if (meshMaxY > maxY) {
                                maxY = meshMaxY
                                found = true
                            }
                        } catch (_: Throwable) {}
                    }
                    if (found) {
                        highestY = maxY
                        cachedHighestY[objectId] = highestY
                    }
                }
            }
            
            val expectedY = highestY + 0.5f
            if (abs(expectedY - handle.position.y) > 0.001f || 
                abs(p.x - handle.position.x) > 0.001f || 
                abs(p.z - handle.position.z) > 0.001f) {
                handle.position = Vector3(p.x, expectedY, p.z)
            }
        }

        // Periodic proximity checks (performance-sensitive)
        maybeCheckProximity()
    }

    private fun maybeCheckProximity() {
        val now = window.performance.now()
        if (now - lastProximityCheckMs < proximityIntervalMs) return
        lastProximityCheckMs = now

        val player = SceneManager.getPlayerController() ?: return
        val playerPos = player.getPlayerPosition() ?: return
        val isPlayer = try { UserApi.getToken() != null } catch (_: Throwable) { false }

        val objectManager = SceneManager.getObjectManager() ?: return
        
        // Use spatial hashing to get only nearby object IDs
        val maxRadius = max(SceneManager.PROXIMITY_RADIUS_SQ, SceneManager.INTERACTION_RADIUS_SQ).toDouble().let { sqrt(it).toFloat() }
        val nearbyCandidates = objectManager.getNearbyObjectIds(playerPos, maxRadius)
        
        var closestInteractableId: String? = null
        var minInteractableDistSq = SceneManager.INTERACTION_RADIUS_SQ

        // Proximity exit logic: objects that were inside but are no longer candidates
        val currentEntered = enteredProximity.toSet()
        currentEntered.forEach { backendObjectId ->
            if (!nearbyCandidates.contains(backendObjectId)) {
                enteredProximity.remove(backendObjectId)
                objectManager.setObjectNearby(backendObjectId, false)
            }
        }

        nearbyCandidates.forEach { backendObjectId ->
            val node = objectManager.getObjectNodes()[backendObjectId] ?: return@forEach
            val pos = node.position
            val dx = pos.x - playerPos.x
            val dy = pos.y - playerPos.y
            val dz = pos.z - playerPos.z
            val distSq = dx * dx + dy * dy + dz * dz

            val wasInside = enteredProximity.contains(backendObjectId)
            val isInside = distSq <= SceneManager.PROXIMITY_RADIUS_SQ

            if (isInside && !wasInside) {
                // Entered radius: mark and send once
                enteredProximity.add(backendObjectId)
                WebSocketApi.userNearby(objectId = backendObjectId, isPlayer = isPlayer)
                // Reveal status if it's nearby
                objectManager.setObjectNearby(backendObjectId, true)
            } else if (!isInside && wasInside) {
                // Exited radius: allow future re-entry to trigger again
                enteredProximity.remove(backendObjectId)
                // Hide status if it's no longer nearby
                objectManager.setObjectNearby(backendObjectId, false)
            }

            // Interaction check (range 1) - find the closest one
            if (distSq <= SceneManager.INTERACTION_RADIUS_SQ) {
                if (distSq < minInteractableDistSq) {
                    minInteractableDistSq = distSq
                    closestInteractableId = backendObjectId
                }
            }
        }

        // Notify object manager about the closest interactable object
        objectManager.setObjectInteractable(closestInteractableId)
    }
    
    /**
     * Creates a character object
     * 
     * @param spriteSheet The sprite sheet to use
     * @param direction Initial direction
     * @param position Initial position
     * @param activity Initial activity, or null for standing
     * @param additionalSpriteSheets Additional sprite sheets to render on top of the main sprite sheet
     * @param user The user whose worn items to include, or null to use the current user
     * @return A handle to the created character
     */
    fun createCharacter(
        spriteSheet: SpriteSheet,
        direction: CharacterDirection,
        position: Vector3 = Vector3(0f, 0f, 0f),
        activity: PlayerActivity? = null,
        additionalSpriteSheets: List<SpriteSheet> = emptyList(),
        user: User? = null,
        initialScale: Float = 1f
    ): WorldObjectHandle? {
        // Get worn item sprite sheets only if a user is explicitly provided
        // This prevents other players from using the current player's worn items
        val wornItemSpriteSheets = if (user != null) {
            main.components.player.WornItemsRenderer.getWornItemSpriteSheetsForUser(user, main.components.inventory.InventoryState.items)
        } else {
            // If no user is provided, assume no worn items (for other players)
            emptyList()
        }

        // Create hair sprite sheet based on user's hair style (if available)
        val hairSpriteSheet = if (user != null) {
            SpriteSheet.createHairSpriteSheet(user.sex ?: Sex.Boy, hairStyle = user.hairStyle)
        } else null
        // Create eyes sprite sheet based on user's eye color and sex (if available)
        val eyesSpriteSheet = if (user != null) {
            SpriteSheet.createEyesSpriteSheet(
                eyeColor = user.eyeColor,
                sex = user.sex
            )
        } else null

        // Combine hair, eyes, any explicitly provided additional sprite sheets, and worn item sprite sheets
        // Use sorting values to determine correct layering order (descending: front-to-back)
        val combinedSpriteSheets = (additionalSpriteSheets + wornItemSpriteSheets + listOfNotNull(hairSpriteSheet, eyesSpriteSheet))
            .sortedByDescending { it.sorting }
            .toMutableList()
        
        val character = Character(
            spriteSheet = spriteSheet,
            direction = direction,
            activity = activity,
            additionalSpriteSheets = combinedSpriteSheets
        )
        character.position = position
        // Set initial scale so initial render matches desired size (used for join animation)
        character.scale = Vector3(initialScale, initialScale, initialScale)
        // Initialize skin tone from user if present
        character.skinTone = user?.skinTone ?: 0.5f
        
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
     *     backgroundColor = "#000000F0"  // Semi-transparent black background
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
     * @param horizontalPadding The horizontal padding in pixels (default: 16)
     * @return A handle to the created text billboard
     */
    fun createTextBillboard(
        text: String,
        position: Vector3,
        fontSize: Int = 32,
        textColor: String = "white",
        backgroundColor: String? = null,
        horizontalPadding: Float = 16f,
        zOffset: Float = 0f,
    ): WorldObjectHandle? {
        val textBillboard = TextBillboard(
            scene = scene,
            text = text,
            fontSize = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor ?: "transparent",
            horizontalPadding = horizontalPadding,
            zOffset = zOffset,
        )
        textBillboard.position = position
        
        return addObject(textBillboard)
    }
    
    /**
     * Creates a status text billboard above an object
     *
     * This method creates a text billboard displaying the object's status
     * 0.5 units above the object's position.
     *
     * @param objectHandle The handle to the object
     * @param status The status text to display
     * @return A handle to the created text billboard, or null if creation failed
     */
    fun createObjectStatusText(
        objectHandle: WorldObjectHandle,
        status: String
    ): WorldObjectHandle? {
        // Don't create status text if status is blank
        if (status.isBlank()) {
            return null
        }
        
        // Get the object's position
        val position = objectHandle.node.position
        
        // Create position for status text (0.5 units above the object)
        val statusPosition = Vector3(position.x, position.y + 0.5f, position.z)
        
        // Create the text billboard
        return createTextBillboard(
            text = status,
            position = statusPosition,
            textColor = "white",
            backgroundColor = "#000000F0", // Semi-transparent black background
            zOffset = 5f, // Pull towards camera so it renders in front
        )
    }
    
    /**
     * Show or hide a typing indicator above an object
     *
     * This renders a small "💬" billboard 0.5 units above the object while typing is true.
     * A 30s safety timeout will auto-clear the indicator if a stop event is missed.
     */
    private fun setObjectTyping(objectId: String, isTyping: Boolean) {
        if (!isTyping) {
            typingHudHandles.remove(objectId)?.remove()
            typingTimeouts.remove(objectId)?.let { window.clearTimeout(it) }
            return
        }
        val node = objectNodes[objectId]
        if (node == null) {
            // Retry shortly if the object/node is not ready yet
            window.setTimeout({ setObjectTyping(objectId, isTyping) }, 50)
            return
        }
        
        // Task 3: Mesh Caching
        val objectMeshes = cachedMeshes.getOrPut(objectId) {
            node.getChildMeshes(false).filter { !DepthPicking.isStatusBillboardMesh(it) }
        }
        val p = node.position
        
        // Task 1: Floating UI Caching
        // Use cached value if available
        var highestY = cachedHighestY[objectId] ?: p.y
        
        if (!cachedHighestY.containsKey(objectId) && objectMeshes.isNotEmpty()) {
            try {
                if (node is TransformNode) {
                    node.computeWorldMatrix(true)
                }

                var maxY = -1e9f
                var found = false
                objectMeshes.forEach { mesh ->
                    try {
                        mesh.computeWorldMatrix(true)
                        val meshMaxY = mesh.getBoundingInfo().boundingBox.maximumWorld.y
                        if (meshMaxY > maxY) {
                            maxY = meshMaxY
                            found = true
                        }
                    } catch (_: Throwable) {}
                }
                if (found) {
                    highestY = maxY
                    cachedHighestY[objectId] = highestY
                }
            } catch (_: Throwable) {}
        }
        
        val targetY = highestY + 0.5f
        val existing = typingHudHandles[objectId]
        if (existing != null) {
            existing.position = Vector3(p.x, targetY, p.z)
        } else {
            val handle = createTextBillboard(
                text = "\uD83D\uDCAC",
                position = Vector3(p.x, targetY, p.z),
                textColor = "white",
                backgroundColor = "#000000F0",
            )
            if (handle != null) typingHudHandles[objectId] = handle
        }
        // Safety timeout to auto-clear after 30s
        typingTimeouts[objectId]?.let { window.clearTimeout(it) }
        typingTimeouts[objectId] = window.setTimeout({
            typingHudHandles.remove(objectId)?.remove()
            typingTimeouts.remove(objectId)
        }, 30000)
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
                // The data may be a JSON-encoded string ("id") or a raw string (id)
                val objectId = try {
                    ApiClient.json.decodeFromString<String>(message.data)
                } catch (_: Throwable) {
                    message.data
                }
                removeObject(objectId)
            } catch (e: Throwable) {
                console.error("Error parsing REMOVE_OBJECT message", e)
            }
        }
        
        // Listen for object typing status (server → clients only)
        WebSocketApi.addListener(WebSocketAction.OBJECT_TYPING) { message ->
            try {
                val data = ApiClient.json.decodeFromString(ObjectTypingData.serializer(), message.data)
                setObjectTyping(data.objectId, data.isTyping)
            } catch (e: Throwable) {
                console.error("Error parsing OBJECT_TYPING message", e)
            }
        }
    }
    
    /**
     * Sets the shadow manager for this world object manager
     * 
     * @param manager The shadow manager to use
     */
    fun setShadowManager(manager: ShadowManager?) {
        shadowManager = manager
        println("[SHADOW_DEBUG] Shadow manager set on WorldObjectManager")
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
    
    internal fun createObjectHandle(obj: WorldObject, node: Node): WorldObjectHandle {
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
            
            override fun updateActivity(activity: PlayerActivity?) {
                console.log("[DEBUG_LOG] WorldObjectHandle: updateActivity called for object ${id} with activity ${activity?.name ?: "null (Standing)"}")
                
                // Only update activity if this is a Character object
                if (obj is Character) {
                    console.log("[DEBUG_LOG] WorldObjectHandle: Updating Character activity to ${activity?.name ?: "Standing"}")
                    obj.activity = activity
                } else {
                    console.log("[DEBUG_LOG] WorldObjectHandle: Object is not a Character, ignoring activity update")
                }
            }

            override fun updateAppearance(additionalSpriteSheets: List<SpriteSheet>?, skinTone: Float?) {
                if (obj is Character) {
                    // Authoritative replacement: the provided list fully defines the additional layers.
                    // We clear any previously applied additional layers before applying the new set
                    // to ensure stale layers are not retained (e.g., after removing the last worn item).
                    if (additionalSpriteSheets != null) {
                        // 1) Clear existing additional sheets and dispose old meshes immediately
                        obj.updateSpriteSheets(emptyList())
                        try { obj.update(0f, null) } catch (_: Throwable) {}

                        // 2) Apply the new authoritative set
                        obj.updateSpriteSheets(additionalSpriteSheets)
                    }
                    // Update skin tone if provided
                    if (skinTone != null) {
                        obj.skinTone = skinTone
                    }
                    // Force render to reflect changes immediately
                    try { obj.update(0f, null) } catch (_: Throwable) {}

                    // Re-register meshes for shadows after they've been recreated
                    shadowManager?.let { manager ->
                        val meshes = getMeshes()
                        manager.addShadowCasters(meshes)
                        manager.configureShadowReceivers(meshes)
                    }
                }
            }

            override fun updateText(text: String) {
                if (obj is TextBillboard) {
                    obj.text = text
                }
            }

            override fun triggerWindBoost(velocity: Vector3, durationSeconds: Float) {
                if (obj is Character) {
                    obj.triggerWindBoost(velocity, durationSeconds)
                }
            }
            
            override fun getMeshes(): Array<AbstractMesh> {
                return node.getChildMeshes(false)
            }
        }
    }
}
