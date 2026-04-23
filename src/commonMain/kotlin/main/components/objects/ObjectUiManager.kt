package main.components.objects

import kotlinx.browser.window
import lib.AbstractMesh
import lib.Node
import lib.TransformNode
import lib.Vector3
import main.components.world.DepthPicking
import main.components.world.CharacterDirection
import main.components.world.PlayerActivity
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import models.ObjectTypingData
import kotlin.math.abs

/**
 * Updates the floating UI positions and quest indicators
 */
fun SceneObjectManager.update(deltaTime: Float) {
    if (objectStatusTextHandles.isEmpty() && objectTypingHandles.isEmpty()) return

    uiUpdateFrameCounter++
    // Throttle floating UI updates to every 3 frames for better performance
    if (uiUpdateFrameCounter % 3 != 0) return

    // Update status text positions
    objectStatusTextHandles.forEach { (objectId, handle) ->
        updateObjectFloatingUiPosition(
            objectId,
            handle,
            isStatusText = true
        )
    }

    // Update typing indicator positions
    objectTypingHandles.forEach { (objectId, handle) ->
        updateObjectFloatingUiPosition(
            objectId,
            handle,
            isStatusText = false
        )
    }
}

/**
 * Updates the position of a floating UI element (status text or typing indicator) 
 * based on the object's current bounding box.
 */
internal fun SceneObjectManager.updateObjectFloatingUiPosition(
    objectId: String,
    handle: WorldObjectHandle,
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
    
    // Check if this specific node or its children have active animations (groups or node-level)
    val hasActiveAnimations = try {
        node.animations.isNotEmpty() || 
        childMeshes.any { it.animations.isNotEmpty() } ||
        scene.animationGroups.any { ag -> 
            ag.isPlaying && (ag.targetedAnimations.any { ta -> 
                val target = ta.target
                target == node || (target is Node && isDescendantOfNode(target, node))
            })
        }
    } catch (_: Throwable) {
        true // Fallback to updating if check fails
    }

    if (hasActiveAnimations || isFirstTime) {
        if (childMeshes.isEmpty()) return

        var maxY = -1e9f
        var found = false

        if (node is TransformNode) {
            node.computeWorldMatrix()
        }

        for (mesh in childMeshes) {
            try {
                // Avoid forcing matrix re-computation if not necessary
                mesh.computeWorldMatrix()
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
                // Skip manual position update if the status text is currently animating (scaling in/out)
                // This prevents conflicts between the manual update and the animation.
                // We consider it animating if it's visible and scaling is not yet at 1.0.
                val isAnimating = handle.isVisible && handle.node.scaling.x < 0.999f
                
                if (!isAnimating) {
                    if (isStatusText) {
                        objectStatusOffsets[objectId] = offsetY
                    }
                    handle.position = Vector3(0f, offsetY, 0f)
                }
            }
        }
    }
}

internal fun isDescendantOfNode(child: Node, ancestor: Node): Boolean {
    var current: Node? = child.parent
    while (current != null) {
        if (current == ancestor) return true
        current = current.parent
    }
    return false
}

/**
 * Animates the status text for an object (slide up + zoom in + bounce)
 */
internal fun SceneObjectManager.animateStatusText(objectId: String, show: Boolean) {
    val handle = objectStatusTextHandles[objectId] ?: return
    val offsetY = objectStatusOffsets[objectId] ?: handle.position.y
    ObjectStatusAnimator.animateStatusText(objectId, handle, show, Vector3(0f, offsetY, 0f), scene)
}

/**
 * Helper method to handle object status text
 */
internal fun SceneObjectManager.handleObjectStatusText(objectId: String, status: String, node: Node) {
    try {
        console.log("[DEBUG_STATUS] Handling status text for object: $objectId, status: '$status'")
        
        // Check if the object already has a status text handle
        val existingHandle = objectStatusTextHandles[objectId]

        // Read previous status and cache latest for restoration after typing
        objectStatusTexts[objectId] = status

        // Quest override: if this object is a quest target, prepend quest symbol and use quest title
        val activeQuest = activeQuestIndicators[objectId]
        val finalStatus = if (activeQuest != null) {
            activeQuest.title
        } else {
            status
        }

        // Prepend "🆇 " if it's the interactable object or a quest target
        val isInteractable = objectId == interactableObjectId
        val displayedStatus = if (activeQuest != null) {
            "🆇 $finalStatus"
        } else if (isInteractable) {
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
            // Determine vertical position (above highest point of object)
            val objectHandle = object : WorldObjectHandle {
                override val id: String = objectId
                override var node: Node = node
                override fun remove() {}
                override fun updateDirection(direction: CharacterDirection) {}
                override fun updateActivity(activity: PlayerActivity?) {}
                override fun updateAppearance(additionalSpriteSheets: List<SpriteSheet>?, skinTone: Float?) {}
                override fun updateText(text: String) {}
                override fun triggerWindBoost(velocity: Vector3, durationSeconds: Float) {}
                override fun getMeshes(): Array<AbstractMesh> {
                    return node.getChildMeshes(false)
                }
            }

            // Task 3: Mesh Caching
            val childMeshes = cachedMeshes.getOrPut(objectId) {
                node.getChildMeshes(false).filter { !DepthPicking.isStatusBillboardMesh(it) }
            }
            
            // Task 1: Floating UI Caching - Reuse height if available
            var highestY = node.position.y
            val cachedY = cachedHighestY[objectId]
            
            if (cachedY != null) {
                highestY = cachedY
            } else if (childMeshes.isNotEmpty()) {
                try {
                    if (node is TransformNode) {
                        node.computeWorldMatrix(true)
                    }
                    var maxY = -1e9f
                    var found = false
                    for (mesh in childMeshes) {
                        mesh.computeWorldMatrix(true)
                        val boundingInfo = mesh.getBoundingInfo()
                        val meshMaxY = boundingInfo.boundingBox.maximumWorld.y
                        if (meshMaxY > maxY) {
                            maxY = meshMaxY
                            found = true
                        }
                    }
                    if (found) {
                        highestY = maxY
                        console.log("[DEBUG_STATUS] Highest Y point of bounding boxes: $highestY")
                    }
                } catch (e: Throwable) {
                    console.error("[DEBUG_STATUS] Error finding highest Y point: ${e.message}")
                    highestY = node.position.y
                }
            } else {
                console.log("[DEBUG_STATUS] No child meshes found, using node position")
            }
            
            val offsetY = (highestY + 0.5f) - node.position.y
            objectStatusOffsets[objectId] = offsetY
            cachedHighestY[objectId] = highestY

            if (existingHandle != null) {
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

                if (existingHandle.node.parent != node) {
                    existingHandle.node.parent = node
                }

                if (objectId == hoveredObjectId || nearbyObjectIds.contains(objectId) || objectId == interactableObjectId || activeQuestIndicators.containsKey(objectId)) {
                    animateStatusText(objectId, true)
                } else {
                    if (existingHandle.isVisible) {
                        animateStatusText(objectId, false)
                    } else {
                        existingHandle.position = Vector3(0f, offsetY, 0f)
                    }
                }
            } else {
                console.log("[DEBUG_STATUS] Creating new status text for object: $objectId with status: '$displayedStatus'")
                val statusTextHandle = worldObjectManager.createObjectStatusText(objectHandle, displayedStatus)

                if (statusTextHandle != null) {
                    try { statusTextHandle.node.parent = node } catch (_: Throwable) {}
                    objectStatusTextHandles[objectId] = statusTextHandle
                    objectDisplayedStatusTexts[objectId] = displayedStatus
                    
                    statusTextHandle.isVisible = false
                    statusTextHandle.node.scaling = Vector3(0f, 0f, 0f)
                    statusTextHandle.position = Vector3(0f, offsetY - 0.2f, 0f)
                    
                    if (objectId == hoveredObjectId || nearbyObjectIds.contains(objectId) || objectId == interactableObjectId || activeQuestIndicators.containsKey(objectId)) {
                        animateStatusText(objectId, true)
                    }
                    console.log("[DEBUG_STATUS] Successfully created and stored status text for object: $objectId")
                }
            }
        }
    } catch (e: Throwable) {
        console.error("[DEBUG_STATUS] Error handling status text for object: $objectId", e)
    }
}

/**
 * Handles typing indicators for objects via WebSocket
 */
fun SceneObjectManager.setObjectTypingLocal(data: ObjectTypingData) {
    val objectId = data.objectId
    val node = objectMeshes[objectId] ?: return
    
    objectTypingTimeouts.remove(objectId)?.let { window.clearTimeout(it) }
    
    if (data.isTyping) {
        if (!objectTypingHandles.containsKey(objectId)) {
            val statusOffsetY = objectStatusOffsets[objectId] ?: 1.0f
            val typingOffsetY = statusOffsetY + 0.4f
            
            val objectHandle = object : WorldObjectHandle {
                override val id: String = objectId
                override var node: Node = node
                override fun remove() {}
                override fun updateDirection(direction: CharacterDirection) {}
                override fun updateActivity(activity: PlayerActivity?) {}
                override fun updateAppearance(additionalSpriteSheets: List<SpriteSheet>?, skinTone: Float?) {}
                override fun updateText(text: String) {}
                override fun triggerWindBoost(velocity: Vector3, durationSeconds: Float) {}
                override fun getMeshes(): Array<AbstractMesh> {
                    return node.getChildMeshes(false)
                }
            }
            val handle = worldObjectManager.createObjectStatusText(objectHandle, "💬")
            if (handle != null) {
                handle.node.parent = node
                handle.position = Vector3(0f, typingOffsetY, 0f)
                objectTypingHandles[objectId] = handle
            }
        }
        
        val timeoutId = window.setTimeout({
            setObjectTypingLocal(ObjectTypingData(objectId, false))
        }, 5000)
        objectTypingTimeouts[objectId] = timeoutId
    } else {
        objectTypingHandles.remove(objectId)?.remove()
        // Restore status text if it exists
        val status = objectStatusTexts[objectId]
        if (status != null) {
            handleObjectStatusText(objectId, status, node)
        }
    }
}
