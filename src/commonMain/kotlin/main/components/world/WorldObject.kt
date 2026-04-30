package main.components.world

import lib.Node
import lib.Scene
import lib.Vector3

/**
 * Base class for all objects that can be rendered in the world.
 */
sealed class WorldObject {
    /**
     * Unique identifier for this object
     */
    var id: String = ""

    /**
     * Position in the 3D world
     */
    var position: Vector3 = Vector3(0f, 0f, 0f)

    /**
     * Rotation in the 3D world
     */
    var rotation: Vector3 = Vector3(0f, 0f, 0f)

    /**
     * Scale in the 3D world
     */
    var scale: Vector3 = Vector3(1f, 1f, 1f)

    /**
     * Renders this object in the given scene
     *
     * @param scene The scene to render in
     * @return The created node or null if rendering failed
     */
    abstract fun render(scene: Scene): Node?

    /**
     * Updates the object's state
     *
     * @param deltaTime Time since last update in seconds
     * @param camera Optional camera to use for display direction calculation
     */
    open fun update(deltaTime: Float, camera: lib.ArcRotateCamera? = null) {}

    /**
     * Disposes resources used by this object
     */
    open fun dispose() {}
}
