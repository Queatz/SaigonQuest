package main.components.player

import lib.AbstractMesh
import lib.Mesh
import lib.Ray
import lib.Vector3
import main.components.world.isSkybox

/**
 * Utilities for validating movement and computing supported Y positions against world meshes.
 */
object WorldCollisionUtils {
    fun isValidPosition(
        worldMeshes: Array<AbstractMesh>,
        collider: Mesh?,
        moveCircle: Mesh?,
        position: Vector3,
    ): Boolean {
        val rayOrigin = Vector3(position.x, position.y + 1f, position.z)
        val rayDirection = Vector3(0f, -1f, 0f)
        val ray = Ray().also {
            it.origin = rayOrigin
            it.direction = rayDirection
        }
        for (mesh in worldMeshes) {
            if (mesh == collider || mesh == moveCircle || mesh.isSkybox()) continue
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) return true
        }
        return false
    }

    fun findValidYPosition(
        worldMeshes: Array<AbstractMesh>,
        collider: Mesh?,
        moveCircle: Mesh?,
        position: Vector3,
        yPadding: Float = 0.5f,
    ): Vector3? {
        val ray = Ray().also {
            it.origin = Vector3(position.x, 10f, position.z)
            it.direction = Vector3(0f, -1f, 0f)
        }
        for (mesh in worldMeshes) {
            if (mesh == collider || mesh == moveCircle || mesh.isSkybox()) continue
            val hit = ray.intersectsMesh(mesh)
            if (hit.pickedPoint != null) {
                return Vector3(position.x, hit.pickedPoint.y + yPadding, position.z)
            }
        }
        return null
    }
}
