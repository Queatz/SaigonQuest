package main.components.player

import lib.AbstractMesh
import lib.Mesh
import lib.Ray
import lib.Vector3
import main.components.world.isSkybox
import main.components.world.isWater

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
        // Prepare target meshes once (exclude collider, move circle, skybox, and water)
        val targetMeshes = worldMeshes.filter {
            it != collider && it != moveCircle && !it.isSkybox() && !it.isWater()
        }.toTypedArray()

        // Ray 1: y + 10f
        val rayFar = Ray().also {
            it.origin = Vector3(position.x, position.y + 10f, position.z)
            it.direction = Vector3(0f, -1f, 0f)
        }
        // Ray 2: y + 2.5f
        val rayNear = Ray().also {
            it.origin = Vector3(position.x, position.y + 2.5f, position.z)
            it.direction = Vector3(0f, -1f, 0f)
        }
        // Ray 3: y + 1f
        val ray3 = Ray().also {
            it.origin = Vector3(position.x, position.y + 1f, position.z)
            it.direction = Vector3(0f, -1f, 0f)
        }

        val hitsFar = rayFar.intersectsMeshes(meshes = targetMeshes)
        val hitsNear = rayNear.intersectsMeshes(meshes = targetMeshes)
        val hits3 = ray3.intersectsMeshes(meshes = targetMeshes)

        // Combine hits and choose the point closest to the input position.y
        val bestHit = (hitsFar + hitsNear + hits3)
            .filter { it.pickedPoint != null }
            .minByOrNull { info -> kotlin.math.abs((info.pickedPoint!!.y) - position.y) }
            ?: return null

        return Vector3(
            position.x,
            bestHit.pickedPoint!!.y + yPadding,
            position.z,
        )
    }

    fun getWaterSurfacePoint(
        worldMeshes: Array<AbstractMesh>,
        position: Vector3,
    ): Vector3? {
        val rayOrigin = Vector3(position.x, position.y + 10f, position.z)
        val rayDirection = Vector3(0f, -1f, 0f)
        val ray = Ray().also {
            it.origin = rayOrigin
            it.direction = rayDirection
        }
        val waterMeshes = worldMeshes.filter { it.isWater() }.toTypedArray()
        if (waterMeshes.isEmpty()) return null

        val hits = ray.intersectsMeshes(meshes = waterMeshes)
        val bestHit = hits.filter { it.pickedPoint != null }
            .minByOrNull { kotlin.math.abs(it.pickedPoint!!.y - position.y) }
            ?: return null

        return bestHit.pickedPoint
    }
}
