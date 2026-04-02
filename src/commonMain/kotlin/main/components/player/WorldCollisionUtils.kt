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

    /**
     * Checks if there's a physical obstacle (wall/steep slope) in the way between from and to.
     * Uses multiple horizontal raycasts at different heights to detect obstacles.
     * 
     * @return True if the path is blocked by a non-walkable obstacle
     */
    fun isPathBlocked(
        worldMeshes: Array<AbstractMesh>,
        collider: Mesh?,
        moveCircle: Mesh?,
        from: Vector3,
        to: Vector3,
    ): Boolean {
        // 1. Check if the step up is too high (Rule 2: small ledges are allowed)
        if (to.y - from.y > 0.6f) return true

        // 2. Check for physical obstacles in the way at multiple heights (Rule 1)
        val horizontalDir = Vector3(to.x - from.x, 0f, to.z - from.z)
        val distance = horizontalDir.length()
        if (distance < 0.01f) return false

        // Check at waist height and chest/head height
        val checkHeights = arrayOf(0.1f, 1.2f)
        
        // Prepare target meshes once (exclude collider, move circle, skybox, and water)
        val targetMeshes = worldMeshes.filter {
            it != collider && it != moveCircle && !it.isSkybox() && !it.isWater()
        }.toTypedArray()

        for (heightOffset in checkHeights) {
            val rayOrigin = Vector3(from.x, from.y + heightOffset, from.z)
            val ray = Ray().also {
                it.origin = rayOrigin
                it.direction = horizontalDir.normalize()
                it.length = distance.toDouble()
            }

            // Use intersectsMeshes to find any blocking geometry
            val hits = ray.intersectsMeshes(targetMeshes)
            for (hit in hits) {
                if (hit.pickedPoint != null) {
                    // Horizontal hit found! Check if this is a walkable slope/ledge or a wall.
                    // We use findValidYPosition at the hit point (probed slightly back) to find ground level.
                    val probePoint = hit.pickedPoint!!.subtract(ray.direction.scale(0.05f))
                    val validYAtHit = findValidYPosition(worldMeshes, collider, moveCircle, probePoint, 0.5f)
                    
                    // If no ground found at hit point, it's an impassable obstacle.
                    if (validYAtHit == null) return true
                    
                    val groundYAtHit = validYAtHit.y - 0.5f
                    // If the hit point is significantly above the ground level at that location, it's a wall.
                    // Increased threshold to 0.3f to be more lenient with jagged edges.
                    if (kotlin.math.abs(hit.pickedPoint!!.y - groundYAtHit) > 0.3f) {
                        return true
                    }
                }
            }
        }
        
        return false
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
