package main.components.`object`

/**
 * Interface for managing objects in the 3D scene
 */
interface ObjectManager {
    /**
     * Removes an object from the scene
     * 
     * @param objectId The ID of the object to remove
     */
    fun removeObject(objectId: String)
}
