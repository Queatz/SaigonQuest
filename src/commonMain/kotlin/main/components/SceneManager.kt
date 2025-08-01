package main.components

import lib.Scene
import main.components.camera.CameraController
import main.components.`object`.ObjectManager
import main.components.`object`.SceneObjectManager
import main.components.player.PlayerController
import main.components.player.OtherPlayersController

/**
 * A singleton manager for accessing scene components globally.
 * 
 * This class provides access to key scene components like the CameraController,
 * ObjectManager, and PlayerController from anywhere in the application, without needing to pass them as parameters.
 */
object SceneManager {
    
    // The camera controller instance
    private var cameraController: CameraController? = null
    
    // The object manager instance
    private var objectManager: ObjectManager? = null
    
    // The player controller instance
    private var playerController: PlayerController? = null
    
    // The other players controller instance
    private var otherPlayersController: OtherPlayersController? = null
    
    // The BabylonJS scene instance
    private var scene: Scene? = null
    
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
     * Sets the object manager instance.
     * 
     * @param manager The object manager instance to store
     */
    fun setObjectManager(manager: ObjectManager) {
        objectManager = manager
        println("[DEBUG] Object manager registered with SceneManager")
    }
    
    /**
     * Gets the object manager instance.
     * 
     * @return The object manager instance, or null if not set
     */
    fun getObjectManager(): ObjectManager? {
        return objectManager
    }
    
    /**
     * Sets the player controller instance.
     * 
     * @param controller The player controller instance to store
     */
    fun setPlayerController(controller: PlayerController) {
        playerController = controller
        println("[DEBUG] Player controller registered with SceneManager")
    }
    
    /**
     * Gets the player controller instance.
     * 
     * @return The player controller instance, or null if not set
     */
    fun getPlayerController(): PlayerController? {
        return playerController
    }
    
    /**
     * Sets the other players controller instance.
     * 
     * @param controller The other players controller instance to store
     */
    fun setOtherPlayersController(controller: OtherPlayersController) {
        otherPlayersController = controller
        println("[DEBUG] Other players controller registered with SceneManager")
    }
    
    /**
     * Gets the other players controller instance.
     * 
     * @return The other players controller instance, or null if not set
     */
    fun getOtherPlayersController(): OtherPlayersController? {
        return otherPlayersController
    }
    
    /**
     * Sets the scene instance.
     * 
     * @param sceneInstance The BabylonJS scene instance to store
     */
    fun setScene(sceneInstance: Scene) {
        scene = sceneInstance
        println("[DEBUG] Scene registered with SceneManager")
    }
    
    /**
     * Gets the scene instance.
     * 
     * @return The BabylonJS scene instance, or null if not set
     */
    fun getScene(): Scene? {
        return scene
    }
    
    /**
     * Clears all stored references.
     * Should be called when the scene is disposed.
     */
    fun clear() {
        cameraController = null
        objectManager = null
        playerController = null
        otherPlayersController = null
        scene = null
        println("[DEBUG] SceneManager cleared")
    }
}
