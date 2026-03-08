package main.components.world

import lib.AbstractMesh
import lib.Node
import lib.Vector3

/**
 * Handle for interacting with a world object
 */
interface WorldObjectHandle {
    /**
     * The unique ID of the object
     */
    val id: String

    /**
     * The node representing this object in the scene
     * Position, rotation, and scale can be accessed directly from this node
     */
    var node: Node
    
    /**
     * Convenience property for accessing the node's position
     * This is provided for backward compatibility
     */
    var position: Vector3
        get() = node.position
        set(value) { node.position = value }
    
    /**
     * Convenience property for accessing the node's rotation
     * This is provided for backward compatibility
     */
    var rotation: Vector3
        get() = node.rotation
        set(value) { node.rotation = value }
    
    /**
     * Convenience property for accessing the node's scale
     * This is provided for backward compatibility
     */
    var scale: Vector3
        get() = node.scaling
        set(value) { node.scaling = value }
    
    /**
     * The visibility of the object
     */
    var isVisible: Boolean
        get() = node.isEnabled()
        set(value) { node.setEnabled(value) }
    
    /**
     * Removes the object from the scene
     */
    fun remove()
    
    /**
     * Updates the direction of a character object
     * No-op for non-character objects
     * 
     * @param direction The new direction
     */
    fun updateDirection(direction: CharacterDirection) {
        // Default implementation does nothing
    }
    
    /**
     * Updates the activity of a character object
     * No-op for non-character objects
     * 
     * @param activity The new activity, or null for standing
     */
    fun updateActivity(activity: PlayerActivity?) {
        // Default implementation does nothing
    }

    /**
     * Updates appearance of a character object in-place.
     * No-op for non-character objects.
     *
     * @param additionalSpriteSheets Optional list of additional sprite sheets to apply
     * @param skinTone Optional skin tone value (0f..1f)
     */
    fun updateAppearance(additionalSpriteSheets: List<SpriteSheet>?, skinTone: Float?) {
        // Default implementation does nothing
    }

    /**
     * Updates text of a text-based world object (e.g., TextBillboard) in-place.
     * No-op for non-text objects.
     */
    fun updateText(text: String) {
        // Default implementation does nothing
    }
    
    /**
     * Helper method to get all meshes for this object
     * 
     * @return Array of all meshes belonging to this object
     */
    fun getMeshes(): Array<AbstractMesh> {
        return node.getChildMeshes(false)
    }
}
