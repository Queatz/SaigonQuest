package main.components.player

import lib.Mesh
import lib.Ray
import lib.Vector3
import main.components.world.effect.WindSystem

/**
 * Result of an environment check at a specific position.
 */
data class EnvironmentInfo(
    val isOnWater: Boolean,
    val groundPoint: Vector3?,
    val waterPoint: Vector3?,
)

/**
 * Utilities for validating movement and computing supported Y positions against world meshes.
 */
object WorldCollisionUtils {
    private val rayDown = Ray(Vector3(), Vector3(0f, -1f, 0f))
    private val rayGeneral = Ray(Vector3(), Vector3())
    private val tempVector3 = Vector3()
    private val tempVector3_2 = Vector3()
    private val downVector = Vector3(0f, -1f, 0f)

    /**
     * Performs a single environment check that returns all necessary information for movement/collision.
     * Consolidates multiple raycasts into one to improve performance.
     */
    fun getEnvironmentInfo(
        context: WorldCollisionContext,
        position: Vector3,
        collider: Mesh? = null,
        moveCircle: Mesh? = null,
        yPadding: Float = 0.5f,
    ): EnvironmentInfo {
        // Prepare reusable ray
        rayDown.direction = downVector
        rayDown.length = 1000f

        // 1. Raycast ground (3 probes for better accuracy near edges)
        var bestGroundPoint: Vector3? = null
        var bestGroundDist = Float.MAX_VALUE
        
        // Exclude specific meshes only if necessary
        val filteredGroundMeshes = if (collider == null && moveCircle == null) {
            context.groundMeshes
        } else {
            context.groundMeshes.filter { it != collider && it != moveCircle }.toTypedArray()
        }

        val yOffsets = arrayOf(10f, 2.5f, 1f)
        for (offset in yOffsets) {
            rayDown.origin.set(position.x, position.y + offset, position.z)
            val hits = rayDown.intersectsMeshes(filteredGroundMeshes)
            for (hit in hits) {
                hit.pickedPoint?.let { p ->
                    val d = kotlin.math.abs(p.y - position.y)
                    if (d < bestGroundDist) {
                        bestGroundDist = d
                        bestGroundPoint = p
                    }
                }
            }
        }

        // 2. Raycast water surface
        var waterPoint: Vector3? = null
        if (context.waterMeshes.isNotEmpty()) {
            rayDown.origin.set(position.x, position.y + 10f, position.z)
            rayDown.length = 1000f
            val hits = rayDown.intersectsMeshes(context.waterMeshes)
            var bestWaterDist = Float.MAX_VALUE
            for (hit in hits) {
                hit.pickedPoint?.let { p ->
                    val d = kotlin.math.abs(p.y - position.y)
                    if (d < bestWaterDist) {
                        bestWaterDist = d
                        waterPoint = p
                    }
                }
            }
        }

        // 3. Determine if we are on water
        val floatingPadding = 0.0f
        val isOnWater = waterPoint != null && 
            (bestGroundPoint == null || (waterPoint.y + floatingPadding) > (bestGroundPoint.y + yPadding))

        return EnvironmentInfo(
            isOnWater = isOnWater,
            groundPoint = bestGroundPoint,
            waterPoint = waterPoint,
        )
    }


    /**
     * Optimized version of isValidPosition that uses pre-filtered meshes.
     */
    fun isValidPosition(
        context: WorldCollisionContext,
        collider: Mesh?,
        moveCircle: Mesh?,
        position: Vector3,
    ): Boolean {
        rayGeneral.origin.set(position.x, position.y + 1f, position.z)
        rayGeneral.direction = downVector
        rayGeneral.length = 1000f
        
        val meshes = if (collider == null && moveCircle == null) {
            context.walkableMeshes
        } else {
            context.walkableMeshes.filter { it != collider && it != moveCircle }.toTypedArray()
        }
        
        val hits = rayGeneral.intersectsMeshes(meshes)
        return hits.any { it.pickedPoint != null }
    }


    fun getIsOnWater(
        context: WorldCollisionContext,
        collider: Mesh?,
        moveCircle: Mesh?,
        position: Vector3,
        yPadding: Float = 0.5f,
    ): Boolean {
        return getEnvironmentInfo(context, position, collider, moveCircle, yPadding).isOnWater
    }

    fun findValidYPosition(
        context: WorldCollisionContext,
        collider: Mesh?,
        moveCircle: Mesh?,
        position: Vector3,
        yPadding: Float = 0.5f,
        applyWaterOffset: Boolean = true,
    ): Vector3? {
        val info = getEnvironmentInfo(context, position, collider, moveCircle, yPadding)
        
        val finalMeshY = if (info.isOnWater) {
            info.waterPoint!!.y
        } else {
            info.groundPoint?.y ?: return null
        }

        var finalPadding = yPadding
        val floatingPadding = 0.0f
        if (info.isOnWater && applyWaterOffset) {
            finalPadding = floatingPadding

            // Add gentle vertical bobbling motion
            val time = WindSystem.timeSeconds
            finalPadding += kotlin.math.sin(time.toDouble() * 2.0).toFloat() * 0.05f
        }

        return Vector3(
            position.x,
            finalMeshY + finalPadding,
            position.z,
        )
    }

    fun findValidYPositionToRef(
        context: WorldCollisionContext,
        collider: Mesh?,
        moveCircle: Mesh?,
        position: Vector3,
        result: Vector3,
        yPadding: Float = 0.5f,
        applyWaterOffset: Boolean = true,
    ): Boolean {
        val info = getEnvironmentInfo(context, position, collider, moveCircle, yPadding)
        
        val finalMeshY = if (info.isOnWater) {
            info.waterPoint!!.y
        } else {
            info.groundPoint?.y ?: return false
        }

        var finalPadding = yPadding
        val floatingPadding = 0.0f
        if (info.isOnWater && applyWaterOffset) {
            finalPadding = floatingPadding

            // Add gentle vertical bobbling motion
            val time = WindSystem.timeSeconds
            finalPadding += kotlin.math.sin(time.toDouble() * 2.0).toFloat() * 0.05f
        }

        result.set(
            position.x,
            finalMeshY + finalPadding,
            position.z,
        )
        return true
    }

    /**
     * Optimized version of isPathBlocked using pre-filtered meshes and consolidated info.
     */
    fun isPathBlocked(
        context: WorldCollisionContext,
        collider: Mesh?,
        moveCircle: Mesh?,
        from: Vector3,
        to: Vector3,
    ): Boolean {
        // 1. Check if the step up is too high
        if (to.y - from.y > 0.6f) return true

        // 2. Check for physical obstacles in the way at multiple heights
        val horizontalDir = tempVector3.set(to.x - from.x, 0f, to.z - from.z)
        val distance = horizontalDir.length()
        if (distance < 0.01f) return false

        val checkHeights = arrayOf(0.1f, 1.2f)
        
        val targetMeshes = if (collider == null && moveCircle == null) {
            context.obstacleMeshes
        } else {
            context.obstacleMeshes.filter { it != collider && it != moveCircle }.toTypedArray()
        }

        horizontalDir.normalize()

        for (heightOffset in checkHeights) {
            rayGeneral.origin.set(from.x, from.y + heightOffset, from.z)
            rayGeneral.direction = horizontalDir
            rayGeneral.length = distance

            val hits = rayGeneral.intersectsMeshes(targetMeshes)
            for (hit in hits) {
                hit.pickedPoint?.let { pickedPoint ->
                    horizontalDir.scaleToRef(0.05f, tempVector3_2)
                    val probePoint = pickedPoint.subtractToRef(tempVector3_2, tempVector3_2)
                    val validYAtHit = findValidYPosition(context, collider, moveCircle, probePoint, 0.5f)
                    
                    if (validYAtHit == null) return true
                    
                    val groundYAtHit = validYAtHit.y - 0.5f
                    if (kotlin.math.abs(pickedPoint.y - groundYAtHit) > 0.3f) {
                        return true
                    }
                }
            }
        }
        
        return false
    }

    fun getWaterSurfacePoint(
        context: WorldCollisionContext,
        position: Vector3,
    ): Vector3? {
        if (context.waterMeshes.isEmpty()) return null
        
        rayGeneral.origin.set(position.x, position.y + 10f, position.z)
        rayGeneral.direction = downVector
        rayGeneral.length = 100.0f // Ensure it's long enough

        val hits = rayGeneral.intersectsMeshes(meshes = context.waterMeshes)
        var bestHit: Vector3? = null
        var bestDist = Float.MAX_VALUE
        for (hit in hits) {
            hit.pickedPoint?.let { p ->
                val d = kotlin.math.abs(p.y - position.y)
                if (d < bestDist) {
                    bestDist = d
                    bestHit = p
                }
            }
        }

        return bestHit
    }
}
