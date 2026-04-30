package main.components.world

import lib.AbstractMesh
import lib.Node
import lib.Scene
import lib.TransformNode

/**
 * A 3D model that can be rendered in the world
 */
class Model(
    var mesh: AbstractMesh,
) : WorldObject() {

    private var modelMesh: AbstractMesh? = null
    private var node: TransformNode? = null

    override fun render(scene: Scene): Node? {
        // Create a transform node that will act as our container
        val container = TransformNode("model_container_${id}", scene)

        // Clone the mesh and make it a child of the container
        val clonedMesh = mesh.clone("model_${id}")
        clonedMesh.parent = container

        if (clonedMesh.isBillboard()) {
            setupAnchorBillboard(clonedMesh, scene)
        }

        // Store references to the meshes
        modelMesh = clonedMesh
        node = container

        return container
    }

    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
        // Model doesn't need camera for updates
    }

    override fun dispose() {
        modelMesh?.dispose()
        node?.dispose()
    }
}
