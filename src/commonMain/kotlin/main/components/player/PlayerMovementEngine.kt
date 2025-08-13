package main.components.player

import lib.AbstractMesh
import lib.Mesh
import lib.Vector3
import main.components.world.PlayerActivity
import main.components.world.WorldObjectHandle

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
        if (getActivity() == PlayerActivity.Moving) setActivity(null)
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
            // Stand if sitting
            if (getActivity() == PlayerActivity.Sit) setActivity(null)
            timeSinceLastMovement = 0.0
        } else if (isMoving && playerTargetPosition != null) {
            val currentPos = playerHandle.position
            val targetPos = playerTargetPosition!!
            val direction = targetPos.subtract(currentPos)
            val distance = direction.length()
            if (distance < 0.1f) {
                stopMovement()
            } else {
                direction.normalize()
                direction.scaleInPlace(kotlin.math.min(frameSpeed, distance))
                finalMoveDirection = direction
                onMoveDirection(direction)
                isPlayerMoving = true
                if (getActivity() == PlayerActivity.Sit) setActivity(null)
                timeSinceLastMovement = 0.0
            }
        }

        if (!isPlayerMoving) {
            timeSinceLastMovement += deltaTime
            if (timeSinceLastMovement >= autoSitTimeout && getActivity() != PlayerActivity.Sit) {
                setActivity(PlayerActivity.Sit)
            }
            if (getActivity() == PlayerActivity.Moving) setActivity(null)
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
