package main.components.objects

import kotlinx.browser.document
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Node
import lib.Vector3
import main.components.audio.SoundManager
import main.components.camera.CameraController
import models.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Updates the proximity status of an object.
 * When nearby, always show its status (without highlight).
 *
 * @param objectId The ID of the object
 * @param isNearby Whether the player is nearby
 */
fun SceneObjectManager.setObjectNearby(objectId: String, isNearby: Boolean) {
    if (isNearby) {
        nearbyObjectIds.add(objectId)
        // Reveal status if it's nearby
        animateStatusText(objectId, true)
    } else {
        nearbyObjectIds.remove(objectId)
        // Hide status only if it's no longer nearby AND not hovered AND not interactable AND not a quest indicator
        if (hoveredObjectId != objectId && interactableObjectId != objectId && !activeQuestIndicators.containsKey(objectId)) {
            animateStatusText(objectId, false)
        }
    }
}

/**
 * Sets the current interactable object (the closest one within interaction radius).
 * This object will be highlighted and its status text will be prefixed with "🆇 ".
 *
 * @param objectId The ID of the object, or null to clear
 */
fun SceneObjectManager.setObjectInteractable(objectId: String?) {
    if (interactableObjectId == objectId) return

    val previousId = interactableObjectId
    interactableObjectId = objectId

    // 1) Update previous interactable object
    if (previousId != null) {
        // Restore status text (removes "🆇 " prefix)
        val originalStatus = objectStatusTexts[previousId] ?: ""
        val node = objectMeshes[previousId]
        if (node != null) {
            handleObjectStatusText(previousId, originalStatus, node)
        }

        // Hide status if neither hovered nor nearby nor interactable nor quest indicator anymore
        if (previousId != hoveredObjectId && !nearbyObjectIds.contains(previousId) && !activeQuestIndicators.containsKey(previousId)) {
            animateStatusText(previousId, false)
        }
    }

    // 2) Update new interactable object
    if (objectId != null) {
        // Play hover sound when an object becomes interactable (if not already hovered by mouse)
        if (objectId != hoveredObjectId) {
            SoundManager.playSound("raw/sounds/object_hover.wav")
        }

        val node = objectMeshes[objectId]
        if (node != null) {
            // Update status text (adds "🆇 " prefix)
            val status = objectStatusTexts[objectId] ?: ""
            handleObjectStatusText(objectId, status, node)

            // Ensure visibility
            animateStatusText(objectId, true)
        }
    }

    // Update selection outline
    updateSelectionOutline()
}

/**
 * Gets the current interactable object ID.
 */
fun SceneObjectManager.getInteractableObjectId(): String? = interactableObjectId

/**
 * Highlights an object with a white outline when hovered
 * 
 * @param objectId The ID of the object to highlight
 * @return true if the object was highlighted successfully, false otherwise
 */
fun SceneObjectManager.highlightObject(objectId: String): Boolean {
    // If already hovered, no need to re-highlight
    if (hoveredObjectId == objectId) return true

    // Remove highlight from previously hovered object
    unhighlightObject()
    
    // Find the object node
    val node = objectMeshes[objectId]
    if (node != null) {
        try {
            // Get all meshes from the node (including the node itself if it's a mesh)
            val allMeshes = node.getChildMeshes(false).toMutableList()
            if (node is AbstractMesh) {
                allMeshes.add(node)
            }

            // Store the hovered object ID
            hoveredObjectId = objectId

            // Update selection outline
            updateSelectionOutline()
            
            // Show status text for the hovered object
            animateStatusText(objectId, true)
            
            // Change cursor to grab
            document.body?.style?.cursor = "grab"

            return true
        } catch (error: Throwable) {
            console.error("[DEBUG_HIGHLIGHT] Error highlighting object", error)
            return false
        }
    }
    return false
}

/**
 * Removes highlight from the currently hovered object
 */
fun SceneObjectManager.unhighlightObject() {
    hoveredObjectId?.let { objectId ->

        val node = objectMeshes[objectId]
        if (node != null) {
            try {
                // Get all meshes from the node (including the node itself if it's a mesh)
                val allMeshes = node.getChildMeshes(false).toMutableList()
                if (node is AbstractMesh) {
                    allMeshes.add(node)
                }

                // Reset cursor
                document.body?.style?.cursor = "default"

                // Hide status text for the unhovered object, unless still nearby, interactable, or a quest indicator
                if (!nearbyObjectIds.contains(objectId) && interactableObjectId != objectId && !activeQuestIndicators.containsKey(objectId)) {
                    animateStatusText(objectId, false)
                }

            } catch (error: Throwable) {
                console.error("[DEBUG_HIGHLIGHT] Error unhighlighting object", error)
            }
        } else {
        }
        hoveredObjectId = null
        updateSelectionOutline()
    }
}

/**
 * Updates the selection outline layer by clearing it and re-adding all meshes for the currently
 * hovered and interactable objects. This is a workaround since SelectionOutlineLayer does not
 * have a public removeSelection method.
 */
internal fun SceneObjectManager.updateSelectionOutline() {
    selectionOutlineLayer.clearSelection()

    // 1. Add selected object highlight
    selectedObject?.let { id ->
        val node = objectMeshes[id]
        if (node != null) {
            node.getChildMeshes(false).forEach { mesh ->
                selectionOutlineLayer.addSelection(mesh)
            }
            if (node is AbstractMesh) selectionOutlineLayer.addSelection(node)
        }
    }

    // 2. Add interactable object highlight
    interactableObjectId?.let { id ->
        // Avoid re-adding if it's the same as selected
        if (id != selectedObject) {
            val node = objectMeshes[id]
            if (node != null) {
                node.getChildMeshes(false).forEach { mesh ->
                    selectionOutlineLayer.addSelection(mesh)
                }
                if (node is AbstractMesh) selectionOutlineLayer.addSelection(node)
            }
        }
    }

    // 3. Add hovered object highlight
    hoveredObjectId?.let { id ->
        // Avoid re-adding if it's the same as selected or interactable
        if (id != selectedObject && id != interactableObjectId) {
            val node = objectMeshes[id]
            if (node != null) {
                node.getChildMeshes(false).forEach { mesh ->
                    selectionOutlineLayer.addSelection(mesh)
                }
                if (node is AbstractMesh) selectionOutlineLayer.addSelection(node)
            }
        }
    }
}

/**
 * Selects an object in the scene and highlights it
 * 
 * @param objectId The ID of the object to select
 * @return true if the object was selected successfully, false otherwise
 */
fun SceneObjectManager.selectObject(objectId: String): Boolean {
    // Deselect the currently selected object if any
    deselectCurrentObject()
    
    // Find the object node
    val node = objectMeshes[objectId]
    if (node != null) {
        try {
            // Store the selected object ID
            selectedObject = objectId
            
            // Update the selection outline to show the highlight
            updateSelectionOutline()
            
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
fun SceneObjectManager.deselectCurrentObject() {
    selectedObject?.let { objectId ->
        selectedObject = null
        // Update the selection outline to remove the highlight
        updateSelectionOutline()
    }
}

/**
 * Locates an object in the scene by moving the camera to focus on it
 * 
 * @param objectId The ID of the object to locate
 * @return true if the object was located successfully, false otherwise
 */
fun SceneObjectManager.locateObject(objectId: String): Boolean {
    // Find the object node
    val node = objectMeshes[objectId]
    if (node != null) {
        try {
            // Select the object to highlight it
            selectObject(objectId)
            
            // Move the camera to focus on the object
            cameraController.focusOnPosition(node.getAbsolutePosition())
            
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
 * Moves the player character to an object's location with a random offset.
 * Also focuses the camera on the object.
 * 
 * @param objectId The ID of the object to move to
 * @param playerController The player controller to handle movement
 */
fun SceneObjectManager.movePlayerToObject(objectId: String, playerController: main.components.player.PlayerController) {
    val node = objectMeshes[objectId] ?: return
    val pos = node.getAbsolutePosition().clone()
    
    // Random offset between 0.5 and 1.5 units away from the object center
    val angle = Random.nextDouble(0.0, 2.0 * PI)
    val radius = Random.nextDouble(0.5, 1.5)
    pos.x += (radius * cos(angle)).toFloat()
    pos.z += (radius * sin(angle)).toFloat()
    
    // Teleport player and focus camera
    playerController.teleportTo(pos)
    cameraController.focusOnPosition(node.getAbsolutePosition())
    
    // Highlight the object
    selectObject(objectId)
}
