package main.components.objects

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Animation
import lib.Color3
import lib.Mesh
import lib.MeshMetadata
import lib.Node
import lib.PBRMaterial
import lib.Scene
import lib.TransformNode
import lib.Vector3
import lib.SelectionOutlineLayer
import main.api.ObjectApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.GraphicsSettings
import main.components.camera.CameraController
import main.components.shadow.ShadowManager
import main.components.world.WorldLoader
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import main.components.world.isSkybox
import models.Object
import models.ObjectKind
import models.Position
import models.Rotation
import models.Scale
import models.WebSocketAction
import models.MoveObjectData
import models.ObjectTypingData
import models.QuestStep
import models.ChatMessage
import models.ChatSenderType

/**
 * Implementation of the ObjectManager interface for managing objects in the 3D scene.
 * This class handles the core state and lifecycle of 3D objects.
 * Logic for placement, interaction, and UI has been moved to extension functions in separate files.
 */
class SceneObjectManager(
    internal val scene: Scene,
    internal val cameraController: CameraController,
    internal var worldObjectManager: WorldObjectManager,
) {
    // Currently selected object
    internal var selectedObject: String? = null
    
    // Map of object IDs to their meshes/nodes
    internal val objectMeshes = mutableMapOf<String, Node>()
    
    // Map of object IDs to their status text handles and offsets
    internal val objectStatusTextHandles = mutableMapOf<String, WorldObjectHandle>()
    internal val objectStatusOffsets = mutableMapOf<String, Float>()
    
    // Performance caches
    internal val cachedMeshes = mutableMapOf<String, List<AbstractMesh>>()
    internal val cachedHighestY = mutableMapOf<String, Float>()

    // Cache status texts
    internal val objectStatusTexts = mutableMapOf<String, String>()
    internal val objectDisplayedStatusTexts = mutableMapOf<String, String>()

    // Typing indicators
    internal val objectTypingHandles = mutableMapOf<String, WorldObjectHandle>()
    internal val objectTypingTimeouts = mutableMapOf<String, Int>()

    // Chat messages
    internal val objectChatHandles = mutableMapOf<String, WorldObjectHandle>()
    internal val objectChatTimeouts = mutableMapOf<String, Int>()
    
    // Tracking model sources and kinds
    internal val objectModelSources = mutableMapOf<String, Pair<String, String>>()
    internal val objectKinds = mutableMapOf<String, ObjectKind>()
    internal val activeQuestIndicators = mutableMapOf<String, QuestStep>()
    
    // Placement/Drag state
    internal val objectVerticalOffsets = mutableMapOf<String, Float>()
    internal var draggingObjectId: String? = null
    internal var isDragging = false
    internal var dragStartPosition: Vector3? = null
    internal var dragClickOffset: Vector3? = null
    
    // Create Object Placement Mode state
    internal var isCreatePlacementActive: Boolean = false
    internal var createPlacementPreview: Node? = null
    internal var onCreatePlacementConfirm: ((Vector3) -> Unit)? = null
    internal var onCreatePlacementCancel: (() -> Unit)? = null
    internal var pendingCreatePosition: Vector3? = null
    
    // Hover and interaction
    internal var hoveredObjectId: String? = null
    internal var interactableObjectId: String? = null
    internal val nearbyObjectIds = mutableSetOf<String>()

    // Spatial hash for proximity checks
    internal val spatialHash = SpatialHash(10f)

    // Selection outline effect
    internal val selectionOutlineLayer = SelectionOutlineLayer("objectSelectionOutlineLayer", scene)

    internal var shadowManager: ShadowManager? = null
    internal val assetManager = ObjectAssetManager(scene)
    internal val coroutineScope = CoroutineScope(Dispatchers.Main)
    internal val Z_FIGHTING_BUFFER = 0.005f
    internal val json = utils.json
    
    var onError: ((String) -> Unit)? = null
    var onSuccess: ((String) -> Unit)? = null

    private var skyboxes: List<AbstractMesh> = emptyList()
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    internal var uiUpdateFrameCounter = 0

    init {
        selectionOutlineLayer.outlineThickness = 3.0f
        selectionOutlineLayer.outlineColor = Color3.White()

        GraphicsSettings.addObserver {
            selectionOutlineLayer.outlineThickness = if (GraphicsSettings.isLowGraphicsModeEnabled()) 1.0f else 3.0f
        }
    }

    fun setShadowManager(shadowManager: ShadowManager) {
        this.shadowManager = shadowManager
    }

    internal fun createObjectInScene(obj: Object) {
        val position = Vector3(obj.position.x, obj.position.y, obj.position.z)
        val rotation = Vector3(obj.rotation.x, obj.rotation.y, obj.rotation.z)
        val scale = Vector3(obj.scale.x, obj.scale.y, obj.scale.z)

        fun createFallback() {
            val fallback = createFallbackSphere(obj.key, position, rotation, scale)
            objectMeshes[obj.key] = fallback
            spatialHash.update(obj.key, position)
            WorldLoader.getCurrentPerformanceEngine()?.let { engine ->
                fallback.getChildMeshes().forEach { engine.registerObjectMesh(it) }
            }
            handleObjectStatusText(obj.key, obj.status, fallback)
        }

        if (obj.file.isNotBlank() && obj.collection.isNotBlank()) {
            objectModelSources[obj.key] = Pair(obj.file, obj.collection)
            assetManager.getNode(obj.file, obj.collection, { node ->
                node.position = position
                node.rotation = rotation
                node.scaling = scale
                startAnimations(node)
                objectMeshes[obj.key] = node
                spatialHash.update(obj.key, node.position)
                handleObjectStatusText(obj.key, obj.status, node)

                // Set objectId on the node and all its meshes for picking
                if (node.metadata == null) node.metadata = js("{}").unsafeCast<MeshMetadata>()

                node.getChildMeshes().also { meshes ->
                    shadowManager?.addShadowCasters(meshes)
                    shadowManager?.configureShadowReceivers(meshes)
                    WorldLoader.getCurrentPerformanceEngine()?.let { engine ->
                        meshes.forEach { engine.registerObjectMesh(it) }
                    }
                }
            }, {
                createFallback()
            })
        } else {
            createFallback()
        }
    }

    internal fun createFallbackSphere(
        name: String, position: Vector3, rotation: Vector3, scale: Vector3,
        color: Color3 = Color3(1f, 1f, 1f), emissiveColor: Color3 = Color3.White().scale(.2f),
        diameter: Float = .5f, segments: Int = 8, alpha: Float = .8f, isPickable: Boolean = true
    ): Node {
        val container = TransformNode("container_$name", scene)
        container.position = position
        container.rotation = rotation
        container.scaling = scale

        val sphere = lib.MeshBuilder.CreateSphere(name, object : lib.CreateSphereOptions {
            override val diameter = diameter
            override val segments = segments
        }, scene)
        sphere.parent = container
        sphere.isPickable = isPickable
        
        val material = PBRMaterial("material_$name", scene)
        material.albedoColor = color
        material.emissiveColor = emissiveColor
        material.alpha = alpha
        material.metallic = 0.0f
        material.roughness = 1.0f
        sphere.material = material
        
        return container
    }

    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        this.worldMeshes = meshes
        this.skyboxes = meshes.filter { it.isSkybox() }
    }

    fun getWorldMeshes(): Array<AbstractMesh> = worldMeshes
    fun getSkyboxes(): List<AbstractMesh> = skyboxes
    fun getSkybox(): AbstractMesh? = skyboxes.firstOrNull()

    fun setupWebSocketListeners(currentWorldId: String) {
        loadExistingObjects(currentWorldId)

        WebSocketApi.addListener(WebSocketAction.ADD_OBJECT) { message ->
            try {
                val obj = json.decodeFromString(Object.serializer(), message.data)
                if (obj.worldId == currentWorldId) createObjectPreferInventory(obj)
            } catch (e: Throwable) {
                console.error("Error handling ADD_OBJECT: ${e.message}")
            }
        }

        WebSocketApi.addListener(WebSocketAction.OBJECT_UPDATE) { message ->
            try {
                val obj = json.decodeFromString(Object.serializer(), message.data)
                if (obj.worldId == currentWorldId) {
                    val node = objectMeshes[obj.key]
                    val existingSource = objectModelSources[obj.key]
                    val newSource = Pair(obj.file, obj.collection)
                    if (node == null || existingSource != newSource) {
                        removeObject(obj.key)
                        createObjectPreferInventory(obj)
                    } else {
                        updateObjectTransform(obj)
                        handleObjectStatusText(obj.key, obj.status, node)
                    }
                } else {
                    removeObject(obj.key)
                }
            } catch (e: Throwable) {
                console.error("Error handling OBJECT_UPDATE: ${e.message}")
            }
        }

        WebSocketApi.addListener(WebSocketAction.REMOVE_OBJECT) { message ->
            try {
                val objectId = try {
                    json.decodeFromString<String>(message.data)
                } catch (_: Throwable) {
                    message.data
                }
                removeObject(objectId)
            } catch (e: Throwable) {
                console.error("Error handling REMOVE_OBJECT: ${e.message}")
            }
        }

        WebSocketApi.addListener(WebSocketAction.OBJECT_TYPING) { message ->
            try {
                val typingData = json.decodeFromString(ObjectTypingData.serializer(), message.data)
                setObjectTypingLocal(typingData)
            } catch (e: Throwable) {
                console.error("Error handling OBJECT_TYPING: ${e.message}")
            }
        }

        WebSocketApi.addListener(WebSocketAction.MOVE_OBJECT) { message ->
            try {
                val data = json.decodeFromString(MoveObjectData.serializer(), message.data)
                this@SceneObjectManager.moveObjectLocal(
                    data.objectId,
                    Vector3(data.position.x, data.position.y, data.position.z),
                    data.animate
                )
            } catch (e: Throwable) {
                console.error("Error handling MOVE_OBJECT: ${e.message}")
            }
        }

        WebSocketApi.addListener(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                val chat = json.decodeFromString(models.ChatMessage.serializer(), message.data)
                if (chat.senderType == models.ChatSenderType.OBJECT && chat.worldId == currentWorldId) {
                    showObjectChatMessage(chat.senderId, chat.message)
                }
            } catch (e: Throwable) {
                console.error("Error handling OBJECT_CHAT: ${e.message}")
            }
        }
    }

    fun loadExistingObjects(worldId: String) {
        coroutineScope.launch {
            try {
                val objects = ObjectApi.getObjectsByWorldId(worldId)
                objects.forEach { createObjectPreferInventory(it) }
            } catch (error: Throwable) {
                console.error("Error loading existing objects", error)
                onError?.invoke("Failed to load objects: ${error.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Updates an object's transform (position, rotation, scale) in the scene locally.
     * This does NOT persist the change to the server.
     */
    fun updateObjectTransform(obj: Object) {
        val node = objectMeshes[obj.key] ?: return
        
        node.position.set(obj.position.x, obj.position.y, obj.position.z)
        node.rotation.set(obj.rotation.x, obj.rotation.y, obj.rotation.z)
        node.scaling.set(obj.scale.x, obj.scale.y, obj.scale.z)
        
        spatialHash.update(obj.key, node.position)
    }

    /**
     * Moves an object to a new position locally, optionally with animation.
     */
    fun moveObjectLocal(objectId: String, position: Vector3, animate: Boolean) {
        val node = objectMeshes[objectId] ?: return
        
        if (animate) {
            Animation.CreateAndStartAnimation(
                name = "moveObject",
                target = node,
                targetProperty = "position",
                framePerSecond = 60,
                totalFrame = 30, // 0.5 seconds
                from = node.position,
                to = position,
                loopMode = Animation.ANIMATIONLOOPMODE_CONSTANT,
                onAnimationEnd = {
                    spatialHash.update(objectId, node.position)
                }
            )
        } else {
            node.position.set(position.x, position.y, position.z)
            spatialHash.update(objectId, node.position)
        }
    }

    fun getSelectedObject(): String? = selectedObject

    fun getObjectMeshes(): Map<String, List<AbstractMesh>> =
        objectMeshes.mapValues { (_, node) ->
            val meshes = node.getChildMeshes(false).filterIsInstance<AbstractMesh>().toMutableList()
            if (node is AbstractMesh) meshes.add(node)
            meshes
        }

    fun getObjectNodes(): Map<String, Node> = objectMeshes

    fun getNearbyObjectIds(position: Vector3, radius: Float): Set<String> = spatialHash.getNearby(position, radius)

    fun isInventoryObject(objectId: String): Boolean = objectKinds[objectId] == ObjectKind.Inventory

    /**
     * Excludes a mesh from being highlighted as an object.
     * 
     * @param mesh The mesh to exclude
     */
    fun excludeMeshFromHighlight(mesh: AbstractMesh) {
        mesh.isPickable = false
    }

    fun dispose() {
        selectionOutlineLayer.dispose()
        assetManager.dispose()
        objectMeshes.values.forEach { it.dispose() }
        objectMeshes.clear()
        objectStatusTextHandles.values.forEach { it.remove() }
        objectStatusTextHandles.clear()
        objectTypingHandles.values.forEach { it.remove() }
        objectTypingHandles.clear()
        objectTypingTimeouts.values.forEach { window.clearTimeout(it) }
        objectTypingTimeouts.clear()
        objectChatHandles.values.forEach { it.remove() }
        objectChatHandles.clear()
        objectChatTimeouts.values.forEach { window.clearTimeout(it) }
        objectChatTimeouts.clear()
        spatialHash.clear()
        cachedMeshes.clear()
        cachedHighestY.clear()
    }

    fun onMouseLeave() {
        document.body?.style?.cursor = "default"
    }

    internal fun startAnimations(node: Node) {
        try {
            if (node.animations.isNotEmpty()) scene.beginAnimation(node, 0f, 1000f, true)
            node.getChildMeshes(false).forEach { mesh ->
                if (mesh.animations.isNotEmpty()) scene.beginAnimation(mesh, 0f, 1000f, true)
            }
            scene.animationGroups.forEach { ag ->
                if (ag.targetedAnimations.any { ta -> 
                    val target = ta.target
                    target == node || (target is Node && isDescendantOfNode(target, node))
                }) {
                    ag.play(true)
                }
            }
        } catch (e: Throwable) {
            console.warn("[DEBUG_ANIMATION] Error starting animations: ${e.message}")
        }
    }

    fun updateActiveQuests(steps: Map<String, QuestStep>) {
        activeQuestIndicators.clear()
        activeQuestIndicators.putAll(steps)
        objectMeshes.forEach { (objectId, node) ->
            val status = objectStatusTexts[objectId] ?: ""
            handleObjectStatusText(objectId, status, node)
        }
    }
}
