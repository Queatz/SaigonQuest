package main.components

import lib.Scene
import main.components.camera.CameraController
import main.components.objects.SceneObjectManager
import main.components.objects.SavedPositionManager
import main.components.player.OtherPlayersCoordinator
import main.components.player.PlayerController

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
    private var objectManager: SceneObjectManager? = null
    
    // The player controller instance
    private var playerController: PlayerController? = null
    
    // The other players coordinator instance
    private var otherPlayersCoordinator: OtherPlayersCoordinator? = null

    // The saved position manager instance
    private var savedPositionManager: SavedPositionManager? = null
    
    // The BabylonJS scene instance
    private var scene: Scene? = null

    // Proximity radius squared for object detection (2-unit radius squared)
    const val PROXIMITY_RADIUS_SQ: Float = 2f * 2f

    // Interaction radius squared (1-unit radius squared, half of proximity radius)
    const val INTERACTION_RADIUS_SQ: Float = 1f * 1f

    // Pending teleport position for the next scene load
    private var pendingTeleport: lib.Vector3? = null

    // Last known local player velocity in world space (per frame delta)
    private val playerVelocity: lib.Vector3 = lib.Vector3(0f, 0f, 0f)
    private var isVelocityKnown: Boolean = false
    
    /**
     * Sets the pending teleport position for the next scene load.
     * 
     * @param position The position to teleport to
     */
    fun setPendingTeleport(position: lib.Vector3?) {
        pendingTeleport = position
        println("[DEBUG] Pending teleport set to $position")
    }
    
    /**
     * Gets and clears the pending teleport position.
     * 
     * @return The pending teleport position, or null if none
     */
    fun getAndClearPendingTeleport(): lib.Vector3? {
        val pos = pendingTeleport
        pendingTeleport = null
        if (pos != null) println("[DEBUG] Pending teleport consumed: $pos")
        return pos
    }
    
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
    fun setObjectManager(manager: SceneObjectManager) {
        objectManager = manager
        println("[DEBUG] Object manager registered with SceneManager")
    }
    
    /**
     * Gets the object manager instance.
     * 
     * @return The object manager instance, or null if not set
     */
    fun getObjectManager(): SceneObjectManager? {
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
     * Sets the other players coordinator instance.
     * 
     * @param coordinator The other players coordinator instance to store
     */
    fun setOtherPlayersCoordinator(coordinator: OtherPlayersCoordinator) {
        otherPlayersCoordinator = coordinator
        println("[DEBUG] Other players coordinator registered with SceneManager")
    }
    
    /**
     * Gets the other players coordinator instance.
     * 
     * @return The other players coordinator instance, or null if not set
     */
    fun getOtherPlayersCoordinator(): OtherPlayersCoordinator? {
        return otherPlayersCoordinator
    }

    /**
     * Sets the saved position manager instance.
     *
     * @param manager The saved position manager instance to store
     */
    fun setSavedPositionManager(manager: SavedPositionManager) {
        savedPositionManager = manager
        println("[DEBUG] Saved position manager registered with SceneManager")
    }

    /**
     * Gets the saved position manager instance.
     *
     * @return The saved position manager instance, or null if not set
     */
    fun getSavedPositionManager(): SavedPositionManager? {
        return savedPositionManager
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

    /** Sets the local player's instantaneous velocity (world space units per frame). */
    fun setPlayerVelocity(v: lib.Vector3?) {
        if (v == null) {
            playerVelocity.set(0f, 0f, 0f)
            isVelocityKnown = false
        } else {
            playerVelocity.copyFrom(v)
            isVelocityKnown = true
        }
    }

    /** Gets the local player's instantaneous velocity. Null if unknown. */
    fun getPlayerVelocity(): lib.Vector3? = if (isVelocityKnown) playerVelocity else null
    
    /**
     * Clears all stored references.
     * Should be called when the scene is disposed.
     */
    fun clear() {
        cameraController = null
        objectManager = null
        playerController = null
        otherPlayersCoordinator = null
        savedPositionManager = null
        scene = null
        playerVelocity.set(0f, 0f, 0f)
        isVelocityKnown = false
        println("[DEBUG] SceneManager cleared")
    }
}
