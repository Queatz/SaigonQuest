package main.components.world

import lib.AbstractMesh
import lib.Vector3

/**
 * Handle for interacting with a world object
 */
interface WorldObjectHandle {
    /**
     * The unique ID of the object
     */
    val id: String

    var mesh: AbstractMesh
    
    /**
     * Position of the object in the 3D world
     */
    var position: Vector3
    
    /**
     * Rotation of the object in the 3D world
     */
    var rotation: Vector3
    
    /**
     * Scale of the object in the 3D world
     */
    var scale: Vector3
    
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
}
