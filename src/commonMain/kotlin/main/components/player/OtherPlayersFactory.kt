package main.components.player

import kotlinx.browser.window
import kotlinx.coroutines.launch
import lib.Vector3
import main.api.UserApi
import main.components.world.CharacterDirection
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle

class OtherPlayersFactory(
    private val state: OtherPlayersState,
    private val hud: OtherPlayersHudManager,
) {
    fun createUsernameTextBillboard(userId: String, position: Vector3, username: String?) {
        val manager = try { state.worldObjectManager } catch (_: Throwable) { return }
        val displayUsername = username ?: "Player-$userId"
        val textHandle = PlayerUiUtils.createUsernameBillboard(
            manager = manager,
            username = displayUsername,
            basePosition = position,
        )
        if (textHandle != null) state.usernameTextHandles[userId] = textHandle
    }

    fun configureShadowForPlayer(userId: String) {
        val handle = state.playerHandles[userId] ?: return
        if (!state.hasShadowManager()) return
        val mgr = state.shadowManager
        handle.node.let { node ->
            node.getChildMeshes().forEach { mesh ->
                mgr.addShadowCaster(mesh)
                mgr.configureShadowReceiver(mesh)
            }
        }
    }

    fun createPlayerMesh(
        userId: String,
        position: Vector3,
        isPlayer: Boolean,
        username: String? = null,
        animateJoin: Boolean = true,
    ) {
        // Remove existing but preserve stored flags
        removePlayerMesh(userId, preserveState = true)

        val manager = try { state.worldObjectManager } catch (_: Throwable) {
            // Defer creation until the WorldObjectManager is initialized
            window.setTimeout({ createPlayerMesh(userId, position, isPlayer, username, animateJoin) }, 50)
            return
        }

        val direction = CharacterDirection.S

        // 1) Create a base character immediately so the player is visible
        run {
            val baseSprite = SpriteSheet.createPlayerSpriteSheetForBodyType(sex = null, bodyType = null)
            val baseHandle: WorldObjectHandle? = manager.createCharacter(
                spriteSheet = baseSprite,
                direction = direction,
                position = position,
                additionalSpriteSheets = emptyList(),
                user = null
            )
            if (baseHandle != null) {
                if (animateJoin) {
                    baseHandle.scale = lib.Vector3(0f, 0f, 0f)
                    state.playerAnimationTimes[userId] = window.performance.now()
                } else {
                    baseHandle.scale = lib.Vector3(1f, 1f, 1f)
                }

                state.playerHandles[userId] = baseHandle
                state.playerNodes[userId] = baseHandle.node
                state.playerDirections[userId] = direction
                state.playerTargetPositions[userId] = position
                baseHandle.position = position

                configureShadowForPlayer(userId)
                // Use provided username if available; will refresh after fetching real user
                createUsernameTextBillboard(userId, position, username)
                hudPositionReady(userId)

                // Apply any known/pending status immediately
                val statusFromPending = state.pendingStatuses[userId]
                val statusFromCache = state.playerUpdateData[userId]?.status
                val initialStatus = when {
                    !statusFromPending.isNullOrBlank() -> statusFromPending
                    !statusFromCache.isNullOrBlank() -> statusFromCache
                    else -> null
                }
                if (!initialStatus.isNullOrBlank()) hud.setDesiredStatus(userId, initialStatus)
            }
        }

        // 2) Fetch the user's appearance and upgrade the character when available
        state.scope.launch {
            try {
                val user = UserApi.getUserById(userId) ?: return@launch

                val userSpriteSheet = SpriteSheet.createPlayerSpriteSheetForBodyType(user.sex, user.bodyType)
                val upgraded: WorldObjectHandle? = manager.createCharacter(
                    spriteSheet = userSpriteSheet,
                    direction = direction,
                    position = position,
                    additionalSpriteSheets = emptyList(),
                    user = user
                )
                if (upgraded == null) return@launch

                // Remove the temporary base handle if it still exists
                state.playerHandles[userId]?.remove()

                // Replace with upgraded handle
                upgraded.scale = lib.Vector3(1f, 1f, 1f)
                upgraded.position = position
                state.playerHandles[userId] = upgraded
                state.playerNodes[userId] = upgraded.node
                state.playerDirections[userId] = direction
                state.playerTargetPositions[userId] = position

                configureShadowForPlayer(userId)

                // Refresh username billboard with authoritative username
                state.usernameTextHandles[userId]?.remove()
                createUsernameTextBillboard(userId, position, user.username)
                hudPositionReady(userId)

                // Apply status with user fallback
                val statusFromPending = state.pendingStatuses[userId]
                val statusFromCache = state.playerUpdateData[userId]?.status
                val initialStatus = when {
                    !statusFromPending.isNullOrBlank() -> statusFromPending
                    !statusFromCache.isNullOrBlank() -> statusFromCache
                    user.status.isNotBlank() -> user.status
                    else -> null
                }
                if (!initialStatus.isNullOrBlank()) hud.setDesiredStatus(userId, initialStatus)
                state.pendingStatuses.remove(userId)
            } catch (t: Throwable) {
                console.error("OtherPlayersFactory: Error fetching User for $userId", t)
            }
        }
    }

    private fun hudPositionReady(userId: String) {
        hudPositionApply(userId)
    }

    private fun hudPositionApply(userId: String) {
        hud.setDesiredStatus(userId, (state.playerUpdateData[userId]?.status ?: "") )
    }

    fun removePlayerMesh(userId: String, preserveState: Boolean = false) {
        hud.removeHud(userId)

        state.playerHandles[userId]?.remove()
        state.playerHandles.remove(userId)
        state.playerNodes.remove(userId)
        state.playerTargetPositions.remove(userId)
        state.playerAnimationTimes.remove(userId)

        state.usernameTextHandles[userId]?.remove()
        state.usernameTextHandles.remove(userId)
        state.playerDirections.remove(userId)

        if (!preserveState) {
            state.playerUsernames.remove(userId)
            state.playerIsPlayerFlags.remove(userId)
            state.playerExplicitActivity.remove(userId)
        }
    }
}
