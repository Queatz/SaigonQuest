package main.components.player

import kotlinx.browser.window
import lib.Vector3
import main.components.world.CharacterDirection

class OtherPlayersAnimator(
    private val state: OtherPlayersState,
    private val hud: OtherPlayersHudManager,
) {
    fun updatePlayerPositions() {
        val currentTime = window.performance.now()
        val deltaTime = state.movementDelta.nextDeltaSeconds()
        val frameMovementSpeed = (state.baseMovementSpeed * deltaTime).toFloat()

        state.playerHandles.forEach { (userId, handle) ->
            val animStart = state.playerAnimationTimes[userId] ?: currentTime
            val elapsed = (currentTime - animStart) / 1000.0
            if (elapsed < state.joinLeaveDuration) {
                if (state.playerTargetPositions.containsKey(userId)) {
                    val progress = (elapsed / state.joinLeaveDuration).toFloat()
                    handle.scale = Vector3(progress, progress, progress)
                } else {
                    val progress = 1.0f - (elapsed / state.joinLeaveDuration).toFloat()
                    if (progress > 0) handle.scale = Vector3(progress, progress, progress)
                }
            } else {
                handle.scale = Vector3(1f, 1f, 1f)
                val targetPos = state.playerTargetPositions[userId]
                if (targetPos != null) {
                    val currentPos = handle.position
                    val direction = targetPos.subtract(currentPos)
                    val distance = direction.length()
                    if (distance < 0.1f) {
                        handle.position = targetPos
                        state.usernameTextHandles[userId]?.let { textHandle ->
                            PlayerUiUtils.updateUsernameBillboardPosition(textHandle, targetPos)
                        }
                        hud.updateHudPosition(userId, targetPos)
                    } else {
                        direction.normalize()
                        run {
                            val currentFacing = state.playerDirections[userId] ?: CharacterDirection.S
                            val newFacing = main.components.world.calculateFacingFromVector(direction, currentFacing)
                            if (newFacing != currentFacing) {
                                state.playerDirections[userId] = newFacing
                                handle.updateDirection(newFacing)
                            }
                        }
                        direction.scaleInPlace(kotlin.math.min(frameMovementSpeed, distance))
                        val newPosition = currentPos.add(direction)
                        handle.position = newPosition
                        state.usernameTextHandles[userId]?.let { textHandle ->
                            PlayerUiUtils.updateUsernameBillboardPosition(textHandle, newPosition)
                        }
                        hud.updateHudPosition(userId, newPosition)
                        if (state.playerExplicitActivity[userId] != main.components.world.PlayerActivity.Sit) {
                            handle.updateActivity(main.components.world.PlayerActivity.Moving)
                        }
                    }
                }
            }
        }
    }

    fun configureShadowsForPlayers() {
        if (!state.hasShadowManager()) return
        val mgr = state.shadowManager
        state.playerHandles.forEach { (userId, handle) ->
            handle.node.getChildMeshes().forEach { mesh ->
                mgr.addShadowCaster(mesh)
                mgr.configureShadowReceiver(mesh)
            }
        }
    }
}
