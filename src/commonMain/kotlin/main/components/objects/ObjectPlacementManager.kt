package main.components.objects

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.Vector3
import lib.Node
import lib.Matrix
import lib.PickingInfo
import lib.TransformNode
import lib.AbstractMesh
import lib.Color3
import main.api.InventoryApi
import main.api.ItemApi
import main.api.ObjectApi
import main.components.inventory.InventoryState
import main.components.world.DepthPicking
import main.components.world.InventoryObject
import main.components.world.WorldLoader
import main.components.world.isSkybox
import models.Item
import models.Object
import models.ObjectKind
import models.Position
import models.WebSocketAction

/**
 * Handles dropping an inventory item into the scene
 */
fun SceneObjectManager.handleInventoryItemDrop(x: Int, y: Int, data: String, worldId: String, quantityOverride: Int? = null) {
    console.log("[DEBUG_DROP] Inventory item dropped at ($x, $y) in world $worldId")
    
    try {
        val payload = json.decodeFromString(main.components.inventory.InventoryDragPayload.serializer(), data)
        console.log("[DEBUG_DROP] Parsed inventory drag payload: itemKey=${payload.itemKey}, inventoryItemKey=${payload.inventoryItemKey}, quantity=${payload.quantity}")
        
        val maxQuantity = if (payload.quantity > 0) payload.quantity else 1
        val dropQuantity = quantityOverride?.coerceIn(1, maxQuantity) ?: 1
        
        coroutineScope.launch {
            try {
                val position = calculatePositionFromScreenCoordinates(x, y)
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

                InventoryState.refreshInventory(coroutineScope = coroutineScope)
                console.log("Item successfully placed and persisted: ${savedObj.key}")
                onSuccess?.invoke("Dropped '${item.title}' x$dropQuantity")
            } catch (error: Throwable) {
                console.error("Error persisting item placement", error)
                onError?.invoke("Failed to drop item: ${error.message ?: "Unknown error"}")
            }
        }
    } catch (error: Throwable) {
        console.error("Error parsing inventory drag payload", error)
        onError?.invoke("Failed to parse inventory data: ${error.message ?: "Unknown error"}")
    }
}

/**
 * Creates a 3D representation of an inventory item in the scene
 */
internal fun SceneObjectManager.createInventoryItemInScene(obj: Object, item: Item, overrideStatus: String? = null) {
    console.log("[DEBUG_ITEM] Creating 3D representation for item: ${obj.key}")
    
    try {
        val position = Vector3(obj.position.x, obj.position.y, obj.position.z)
        val inventoryObject = InventoryObject(
            item = item,
            initialPosition = position,
            objectId = obj.key
        )
        
        val node = inventoryObject.render(scene)
        if (node != null) {
            node.rotation = Vector3(obj.rotation.x, obj.rotation.y, obj.rotation.z)
            node.scaling = Vector3(obj.scale.x, obj.scale.y, obj.scale.z)
            startAnimations(node)
            objectMeshes[obj.key] = node
            spatialHash.update(obj.key, node.position)
            objectModelSources[obj.key] = Pair("", "")
            objectKinds[obj.key] = ObjectKind.Inventory
            handleObjectStatusText(obj.key, overrideStatus ?: obj.status, node)

            node.getChildMeshes().also { meshes ->
                shadowManager?.addShadowCasters(meshes)
                shadowManager?.configureShadowReceivers(meshes)
                WorldLoader.getCurrentPerformanceEngine()?.let { engine ->
                    meshes.forEach { engine.registerObjectMesh(it) }
                }
            }
        }
    } catch (error: Throwable) {
        console.error("[DEBUG_ITEM] Error creating inventory item", error)
    }
}

/**
 * Handles dropping an object into the scene
 */
fun SceneObjectManager.handleObjectDrop(x: Int, y: Int, data: String, worldId: String) {
    console.log("[DEBUG_DROP] Object dropped at ($x, $y) in world $worldId")
    
    try {
        val obj = json.decodeFromString(Object.serializer(), data)
        coroutineScope.launch {
            val position = calculatePositionFromScreenCoordinates(x, y)
            val updatedObj = obj.copy(
                position = Position(
                    x = position.x,
                    y = position.y + obj.verticalOffset,
                    z = position.z
                ),
                worldId = worldId
            )
            
            try {
                val savedObj = if (obj.key.isNotEmpty()) {
                    ObjectApi.updateObject(obj.key, updatedObj)
                } else {
                    ObjectApi.createObject(updatedObj)
                }
                
                createObjectInScene(savedObj)
                onSuccess?.invoke("Object '${savedObj.title}' placed successfully")
            } catch (error: Throwable) {
                console.error("Error persisting object placement", error)
                onError?.invoke("Failed to place object: ${error.message ?: "Unknown error"}")
            }
        }
    } catch (error: Throwable) {
        console.error("Error parsing object data", error)
        onError?.invoke("Failed to parse object data: ${error.message ?: "Unknown error"}")
    }
}

/**
 * Calculates a 3D position from 2D screen coordinates
 */
internal suspend fun SceneObjectManager.calculatePositionFromScreenCoordinates(clientX: Int, clientY: Int): Position {
    val depthPoint = try {
        DepthPicking.getVisibleWorldPointAt(
            scene = scene,
            x = clientX,
            y = clientY,
        )
    } catch (_: Throwable) { null }

    var basePoint: Vector3? = depthPoint

    if (basePoint == null) {
        val ray = scene.createPickingRay(
            x = clientX,
            y = clientY,
            world = Matrix.Identity(),
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
            basePoint = playerController?.getPlayerPosition() ?: Vector3.Zero()
        }
    }

    return Position(
        x = basePoint.x,
        y = basePoint.y + Z_FIGHTING_BUFFER,
        z = basePoint.z
    )
}

/**
 * Creates a 3D representation of an object in the scene, preferring inventory if it has an itemKey
 */
fun SceneObjectManager.createObjectPreferInventory(obj: Object) {
    objectKinds[obj.key] = obj.kind
    objectVerticalOffsets[obj.key] = obj.verticalOffset

    if (obj.kind != ObjectKind.Inventory) {
        createObjectInScene(obj)
        return
    }

    coroutineScope.launch {
        try {
            val inv = InventoryApi.getObjectInventory(obj.key)
            val items = InventoryApi.getInventoryItemsByIds(inv.itemOrder)
            val first = items.firstOrNull()
            if (first != null) {
                val itemDef = ItemApi.getItemById(first.item)
                val inventoryStatus = if (first.quantity > 1) "${itemDef.title} x${first.quantity}" else itemDef.title
                val finalStatus = if (obj.status.isBlank()) inventoryStatus else obj.status
                createInventoryItemInScene(obj, itemDef, finalStatus)
            } else {
                createObjectInScene(obj)
            }
        } catch (e: Throwable) {
            createObjectInScene(obj)
        }
    }
}

fun SceneObjectManager.isCreatePlacementModeActive(): Boolean = isCreatePlacementActive

/** Public API to allow editor dialog to consume the pending create position once. */
fun SceneObjectManager.consumePendingCreatePosition(): Vector3? {
    val p = pendingCreatePosition
    pendingCreatePosition = null
    return p
}

/** Starts the object placement mode with a small red non-pickable sphere under the cursor. */
fun SceneObjectManager.startCreatePlacementMode(
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
fun SceneObjectManager.updateCreatePlacementPreview(x: Int, y: Int) {
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
fun SceneObjectManager.confirmCreatePlacement(x: Int, y: Int) {
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
fun SceneObjectManager.cancelCreatePlacementMode() {
    val cancel = onCreatePlacementCancel
    endCreatePlacementMode()
    cancel?.invoke()
}

internal fun SceneObjectManager.endCreatePlacementMode() {
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

fun SceneObjectManager.startDragging(objectId: String, clickPoint: Vector3? = null): Boolean {
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

fun SceneObjectManager.updateDragging(position: Vector3): Boolean {
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

fun SceneObjectManager.endDragging(): Boolean {
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
    } catch (error: Throwable) {
        console.error("Error ending drag", error)
    }
    return false
}

/**
 * Removes an object from the scene
 */
fun SceneObjectManager.removeObject(objectId: String) {
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

            // Remove from performance engine
            WorldLoader.getCurrentPerformanceEngine()?.let { engine ->
                node.getChildMeshes().forEach { mesh ->
                    engine.unregisterObjectMesh(mesh)
                }
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
 * Moves an object to a new position in the scene and persists the change
 */
fun SceneObjectManager.moveObject(objectId: String, newPosition: Position) {
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
