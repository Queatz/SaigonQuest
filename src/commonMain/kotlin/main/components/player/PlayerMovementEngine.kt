package main.components.player

import lib.AbstractMesh
import lib.Mesh
import lib.Vector3
import main.components.world.PlayerActivity
import main.components.world.WorldObjectHandle
import main.components.world.effect.WindSystem
import kotlin.math.max

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

    // Reusable vectors for performance
    private val tempVector3 = Vector3()
    private val tempVector3_2 = Vector3()
    private val tempVector3_3 = Vector3()
    private val tempVector3_4 = Vector3()

    // Click-to-move state
    private var playerTargetPosition: Vector3? = null
    private var isMoving = false
    private var isAutoRunning = false

    fun isMoving() = isMoving
    fun isAutoRunning() = isAutoRunning
    fun isJumping() = isJumping

    // Inactivity auto-sit
    private var timeSinceLastMovement = 0.0
    private val autoSitTimeout = 300.0

    // Minimal-progress detection
    private var lastSignificantPosition: Vector3? = null
    private var timeWithMinimalMovement = 0.0
    private val minimalMovementThreshold = 0.01f
    private val giveUpTimeThreshold = 1.0

    // Jump state
    private var isJumping = false
    private var jumpElapsed = 0.0
    private val jumpDuration = 0.5 // total seconds
    private val jumpMaxHeight = 0.25f
    private var jumpDirection: Vector3? = null
    private var jumpBaseY = 0f

    fun jump(currentMoveDirection: Vector3, playerPosition: Vector3) {
        if (!isJumping && getActivity() != PlayerActivity.Sit) {
            isJumping = true
            jumpElapsed = 0.0
            jumpBaseY = playerPosition.y

            var effectiveDirection = currentMoveDirection

            // If no keyboard input, but click-to-move is active, use that direction
            if (effectiveDirection.length() == 0f && isMoving && playerTargetPosition != null) {
                playerTargetPosition!!.subtractToRef(playerPosition, tempVector3)
                val toTarget = tempVector3
                val toTargetXZ = tempVector3_2.set(toTarget.x, 0f, toTarget.z)
                if (toTargetXZ.length() > 0.1f) {
                    effectiveDirection = toTargetXZ.normalize()
                }
            }

            // Capture normalized movement direction for the jump
            jumpDirection = if (effectiveDirection.length() > 0f) {
                val dir = effectiveDirection.clone().normalize()
                onMoveDirection(dir)
                dir
            } else {
                Vector3.Zero()
            }
            setActivity(PlayerActivity.Jump)
        }
    }

    fun setTargetPosition(position: Vector3, currentPosition: Vector3) {
        playerTargetPosition = Vector3(position.x, position.y + 0.5f, position.z)
        isMoving = true
        position.subtractToRef(currentPosition, tempVector3)
        isAutoRunning = tempVector3.length() > 10f
        setActivity(PlayerActivity.Moving)
    }

    fun stopMovement() {
        playerTargetPosition = null
        isMoving = false
        isAutoRunning = false
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
        collisionContext: WorldCollisionContext,
        moveCircle: Mesh?,
        speedMultiplier: Float = 1.0f,
    ): Vector3? {
        val deltaTime = movementDelta.nextDeltaSeconds()
        val currentPosition = playerHandle.position
        
        // 1. Determine environment info once per frame
        val envInfo = WorldCollisionUtils.getEnvironmentInfo(
            context = collisionContext,
            position = currentPosition,
            collider = collider,
            moveCircle = moveCircle,
        )

        val isOnWater = envInfo.isOnWater
        val actualSpeedMultiplier = if (isAutoRunning) {
            max(speedMultiplier, 4.0f)
        } else {
            speedMultiplier
        }
        val speedFactor = if (isOnWater) 0.5f else 1.0f
        val frameSpeed = (baseMovementSpeed * actualSpeedMultiplier * speedFactor * deltaTime).toFloat()

        var finalMoveDirection = Vector3.Zero()
        var isPlayerMoving = false

        if (isJumping) {
            jumpElapsed += deltaTime
            if (jumpElapsed >= jumpDuration) {
                isJumping = false
                jumpElapsed = 0.0
                jumpDirection = null
            } else {
                val dir = jumpDirection ?: Vector3.Zero()
                if (dir.length() > 0f) {
                    dir.scaleToRef(frameSpeed, tempVector3_3)
                    finalMoveDirection = tempVector3_3
                }
                isPlayerMoving = true
            }
        }

        if (!isJumping) {
            if (moveDirection.length() > 0f) {
                moveDirection.scaleToRef(frameSpeed, tempVector3_3)
                finalMoveDirection = tempVector3_3
                // Notify facing update
                onMoveDirection(moveDirection)
                // Cancel click-to-move
                stopMovement()
                isPlayerMoving = true
                
                moveDirection.scaleToRef(baseMovementSpeed * actualSpeedMultiplier * speedFactor, tempVector3_2)
                val velocity = tempVector3_2
                // Temporary wind boost while moving (extends 1s tail)
                playerHandle.triggerWindBoost(velocity)
                // Stand if sitting
                if (getActivity() == PlayerActivity.Sit) {
                    setActivity(null)
                }
                timeSinceLastMovement = 0.0
            } else if (isMoving && playerTargetPosition != null) {
                val currentPos = playerHandle.position
                val targetPos = playerTargetPosition!!
                // Separate horizontal (XZ) and vertical (Y) components
                targetPos.subtractToRef(currentPos, tempVector3)
                val toTarget = tempVector3
                val toTargetXZ = tempVector3_2.set(toTarget.x, 0f, toTarget.z)
                val distanceXZ = toTargetXZ.length()
                // Stop when close in XZ; Y will be adjusted via ground snap
                if (distanceXZ < 0.1f) {
                    stopMovement()
                } else {
                    // XZ movement at constant speed independent of Y
                    val moveXZ = if (distanceXZ > 0f) {
                        val stepXZ = kotlin.math.min(frameSpeed, distanceXZ)
                        toTargetXZ.normalize()
                        toTargetXZ.scaleInPlace(stepXZ)
                        toTargetXZ
                    } else {
                        Vector3.Zero()
                    }
                    // Keep Y movement as before (based on full 3D direction)
                    val distance3D = toTarget.length()
                    val moveY = if (distance3D > 0f) {
                        val step3D = kotlin.math.min(frameSpeed, distance3D)
                        (toTarget.y / distance3D) * step3D
                    } else 0f
                    
                    tempVector3_3.set(moveXZ.x, moveY, moveXZ.z)
                    finalMoveDirection = tempVector3_3
                    // Use horizontal direction for facing
                    onMoveDirection(moveXZ)
                    
                    moveXZ.normalizeToRef(tempVector3)
                    tempVector3.scaleInPlace(baseMovementSpeed * actualSpeedMultiplier * speedFactor)
                    val velocity = tempVector3
                    
                    isPlayerMoving = true
                    // Temporary wind boost while moving-to-target as well
                    playerHandle.triggerWindBoost(velocity)
                    if (getActivity() == PlayerActivity.Sit) {
                        setActivity(null)
                    }
                    timeSinceLastMovement = 0.0
                }
            }
        }

        if (!isPlayerMoving) {
            timeSinceLastMovement += deltaTime
            if (timeSinceLastMovement >= autoSitTimeout && getActivity() != PlayerActivity.Sit) {
                setActivity(PlayerActivity.Sit)
            }
            if (getActivity() == PlayerActivity.Moving || getActivity() == PlayerActivity.Jump) {
                setActivity(null)
            }
        } else {
            if (isJumping) {
                if (getActivity() != PlayerActivity.Jump) setActivity(PlayerActivity.Jump)
            } else if (getActivity() != PlayerActivity.Moving) {
                setActivity(PlayerActivity.Moving)
            }
        }

        if (finalMoveDirection.length() > 0f || isJumping || true) {
            val currentPos = playerHandle.position

            fun tryMove(candidatePos: Vector3): Vector3? {
                if (WorldCollisionUtils.isValidPosition(collisionContext, collider, moveCircle, candidatePos)) {
                    val res = Vector3()
                    val success = WorldCollisionUtils.findValidYPositionToRef(collisionContext, collider, moveCircle, candidatePos, res, 0.5f)
                    if (!success) return null
                    var validPos = res

                    if (isJumping) {
                        val t = jumpElapsed / jumpDuration
                        val h = 4 * jumpMaxHeight * t * (1.0 - t)
                        validPos.y = kotlin.math.max(validPos.y, jumpBaseY + h.toFloat())
                    }

                    if (WorldCollisionUtils.isPathBlocked(collisionContext, collider, moveCircle, currentPos, validPos)) {
                        return null
                    }
                    return validPos
                }
                return null
            }

            currentPos.addToRef(finalMoveDirection, tempVector3_4)
            val newPos = tempVector3_4
            var finalPos = tryMove(newPos)

            // If blocked, try sliding movement (X-only or Z-only)
            if (finalPos == null && finalMoveDirection.length() > 0.001f) {
                val moveX = tempVector3_2.set(finalMoveDirection.x, 0f, 0f)
                val moveZ = tempVector3_3.set(0f, 0f, finalMoveDirection.z)

                // Prioritize the larger component for smoother sliding
                val firstMove = if (kotlin.math.abs(moveX.x) > kotlin.math.abs(moveZ.z)) moveX else moveZ
                val secondMove = if (firstMove === moveX) moveZ else moveX

                finalPos = tryMove(currentPos.add(firstMove)) ?: tryMove(currentPos.add(secondMove))
            }

            // If still blocked and jumping, at least allow vertical movement (falling/rising) at current XZ
            if (finalPos == null && isJumping) {
                finalPos = tryMove(currentPos)
            }

            if (finalPos != null) {
                if (isMoving && playerTargetPosition != null) {
                    if (lastSignificantPosition == null) lastSignificantPosition = currentPos
                    val movementX = kotlin.math.abs(finalPos.x - lastSignificantPosition!!.x)
                    val movementZ = kotlin.math.abs(finalPos.z - lastSignificantPosition!!.z)
                    val significant = movementX > minimalMovementThreshold || movementZ > minimalMovementThreshold
                    if (significant) {
                        lastSignificantPosition = finalPos
                        timeWithMinimalMovement = 0.0
                    } else {
                        timeWithMinimalMovement += deltaTime
                        if (timeWithMinimalMovement >= giveUpTimeThreshold) stopMovement()
                    }
                }
                return finalPos
            } else if (isMoving) {
                stopMovement()
            }
        }
        return null
    }
}
