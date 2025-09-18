package main.components.player

import lib.AbstractMesh
import lib.Mesh
import lib.Vector3
import main.components.world.PlayerActivity
import main.components.world.WorldObjectHandle
import main.components.world.effect.WindSystem

/**
 * Encapsulates local player movement + click-to-move state and logic.
 * Handles auto-sit, minimal-progress detection, and direction updates via callbacks.
 */
class PlayerMovementEngine(
    private val onMoveDirection: (Vector3) -> Unit,
    private val getActivity: () -> PlayerActivity?,
    private val setActivity: (PlayerActivity?) -> Unit,
) {
    private val movementDelta = FrameDeltaTracker()
    private val baseMovementSpeed = 3.0f

    // Click-to-move state
    private var playerTargetPosition: Vector3? = null
    private var isMoving = false

    // Inactivity auto-sit
    private var timeSinceLastMovement = 0.0
    private val autoSitTimeout = 300.0

    // Minimal-progress detection
    private var lastSignificantPosition: Vector3? = null
    private var timeWithMinimalMovement = 0.0
    private val minimalMovementThreshold = 0.01f
    private val giveUpTimeThreshold = 1.0

    fun setTargetPosition(position: Vector3) {
        playerTargetPosition = Vector3(position.x, position.y + 0.5f, position.z)
        isMoving = true
        setActivity(PlayerActivity.Moving)
    }

    fun stopMovement() {
        playerTargetPosition = null
        isMoving = false
        lastSignificantPosition = null
        timeWithMinimalMovement = 0.0
    }

    /**
     * Advances movement for this frame. Returns new valid position if moved.
     */
    fun update(
        moveDirection: Vector3,
        playerHandle: WorldObjectHandle,
        collider: Mesh,
        worldMeshes: Array<AbstractMesh>,
        moveCircle: Mesh?,
    ): Vector3? {
        val deltaTime = movementDelta.nextDeltaSeconds()
        val frameSpeed = (baseMovementSpeed * deltaTime).toFloat()

        var finalMoveDirection = Vector3.Zero()
        var isPlayerMoving = false

        if (moveDirection.length() > 0f) {
            finalMoveDirection = moveDirection.scale(frameSpeed)
            // Notify facing update
            onMoveDirection(moveDirection)
            // Cancel click-to-move
            stopMovement()
            isPlayerMoving = true
            // Temporary wind boost while moving (extends 1s tail)
            WindSystem.triggerMovementBoost(finalMoveDirection)
            // Stand if sitting
            if (getActivity() == PlayerActivity.Sit) {
                setActivity(null)
            }
            timeSinceLastMovement = 0.0
        } else if (isMoving && playerTargetPosition != null) {
            val currentPos = playerHandle.position
            val targetPos = playerTargetPosition!!
            // Separate horizontal (XZ) and vertical (Y) components
            val toTarget = targetPos.subtract(currentPos)
            val toTargetXZ = Vector3(toTarget.x, 0f, toTarget.z)
            val distanceXZ = toTargetXZ.length()
            // Stop when close in XZ; Y will be adjusted via ground snap
            if (distanceXZ < 0.1f) {
                stopMovement()
            } else {
                // XZ movement at constant speed independent of Y
                val moveXZ = if (distanceXZ > 0f) {
                    val stepXZ = kotlin.math.min(frameSpeed, distanceXZ)
                    val dirXZ = toTargetXZ.clone()
                    dirXZ.normalize()
                    dirXZ.scaleInPlace(stepXZ)
                    dirXZ
                } else {
                    Vector3.Zero()
                }
                // Keep Y movement as before (based on full 3D direction)
                val distance3D = toTarget.length()
                val moveY = if (distance3D > 0f) {
                    val step3D = kotlin.math.min(frameSpeed, distance3D)
                    (toTarget.y / distance3D) * step3D
                } else 0f
                finalMoveDirection = Vector3(moveXZ.x, moveY, moveXZ.z)
                // Use horizontal direction for facing
                onMoveDirection(moveXZ)
                isPlayerMoving = true
                // Temporary wind boost while moving-to-target as well
                WindSystem.triggerMovementBoost(finalMoveDirection)
                if (getActivity() == PlayerActivity.Sit) {
                    setActivity(null)
                }
                timeSinceLastMovement = 0.0
            }
        }

        if (!isPlayerMoving) {
            timeSinceLastMovement += deltaTime
            if (timeSinceLastMovement >= autoSitTimeout && getActivity() != PlayerActivity.Sit) {
                setActivity(PlayerActivity.Sit)
            }
            if (getActivity() == PlayerActivity.Moving) {
                setActivity(null)
            }
        } else if (getActivity() != PlayerActivity.Moving) {
            setActivity(PlayerActivity.Moving)
        }

        if (finalMoveDirection.length() > 0f) {
            val currentPos = playerHandle.position
            val newPos = currentPos.add(finalMoveDirection)
            if (WorldCollisionUtils.isValidPosition(worldMeshes, collider, moveCircle, newPos)) {
                val validPos = WorldCollisionUtils.findValidYPosition(worldMeshes, collider, moveCircle, newPos, 0.5f)
                if (isMoving && playerTargetPosition != null && validPos != null) {
                    if (lastSignificantPosition == null) lastSignificantPosition = currentPos
                    val movementX = kotlin.math.abs(validPos.x - lastSignificantPosition!!.x)
                    val movementZ = kotlin.math.abs(validPos.z - lastSignificantPosition!!.z)
                    val significant = movementX > minimalMovementThreshold || movementZ > minimalMovementThreshold
                    if (significant) {
                        lastSignificantPosition = validPos
                        timeWithMinimalMovement = 0.0
                    } else {
                        timeWithMinimalMovement += deltaTime
                        if (timeWithMinimalMovement >= giveUpTimeThreshold) stopMovement()
                    }
                }
                return validPos
            } else if (isMoving) {
                stopMovement()
            }
        }
        return null
    }
}
