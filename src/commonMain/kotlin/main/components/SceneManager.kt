package main.components

import main.components.camera.CameraController

/**
 * A singleton manager for accessing scene components globally.
 * 
 * This class provides access to key scene components like the CameraController
 * from anywhere in the application, without needing to pass them as parameters.
 */
object SceneManager {
    
    // The camera controller instance
    private var cameraController: CameraController? = null
    
    /**
     * Sets the camera controller instance.
     * 
     * @param controller The camera controller instance to store
     */
    fun setCameraController(controller: CameraController) {
        cameraController = controller
        println("[DEBUG] Camera controller registered with SceneManager")
    }
    
    /**
     * Gets the camera controller instance.
     * 
     * @return The camera controller instance, or null if not set
     */
    fun getCameraController(): CameraController? {
        return cameraController
    }
    
    /**
     * Clears all stored references.
     * Should be called when the scene is disposed.
     */
    fun clear() {
        cameraController = null
        println("[DEBUG] SceneManager cleared")
    }
}
