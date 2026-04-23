package main.components.player

import lib.AbstractMesh
import main.components.world.isPassable
import main.components.world.isSkybox
import main.components.world.isWater

/**
 * A context that holds pre-filtered meshes for efficient collision detection and raycasting.
 * This avoids redundant filtering of the entire world mesh array on every operation.
 */
class WorldCollisionContext(val allWorldMeshes: Array<AbstractMesh>) {
    /**
     * Meshes that represent the ground and solid obstacles.
     * Excludes skybox, water, and passable (transparent/non-colliding) meshes.
     */
    val groundMeshes: Array<AbstractMesh> = allWorldMeshes.filter {
        !it.isSkybox() && !it.isWater() && !it.isPassable()
    }.toTypedArray()

    /**
     * Meshes that represent water surfaces.
     * Only includes meshes marked as water that are NOT passable.
     */
    val waterMeshes: Array<AbstractMesh> = allWorldMeshes.filter {
        it.isWater() && !it.isPassable()
    }.toTypedArray()

    /**
     * All meshes that should be considered for general path blocking checks.
     * Usually the same as ground meshes.
     */
    val obstacleMeshes: Array<AbstractMesh> = groundMeshes
}
