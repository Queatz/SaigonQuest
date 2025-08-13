package main.components.player

import kotlinx.browser.window
import lib.AbstractMesh
import lib.Scene
import main.components.shadow.ShadowManager
import main.components.world.WorldObjectManager

/**
 * Thin facade orchestrating other player state, presence, HUD and animation.
 */
class OtherPlayersController(private val scene: Scene) {
    private val state = OtherPlayersState()
    private val hud = OtherPlayersHudManager(
        managerProvider = { state.worldObjectManager },
        myUserIdProvider = { state.myUserId },
        positionProvider = { uid -> state.playerHandles[uid]?.position },
    )
    private val factory = OtherPlayersFactory(state, hud)
    private val animator = OtherPlayersAnimator(state, hud)
    private val presence = OtherPlayersPresence(state, factory, hud)

    fun setHttpWorldId(worldId: String) {
        state.httpWorldIdOverride = worldId
    }

    fun setWorldObjectManager(manager: WorldObjectManager) {
        state.worldObjectManager = manager
    }

    fun refreshCurrentUserId() = presence.refreshCurrentUserId()

    fun setShadowManager(manager: ShadowManager) {
        state.shadowManager = manager
        animator.configureShadowsForPlayers()
    }

    fun initialize() {
        presence.initialize()
        scene.registerBeforeRender { animator.updatePlayerPositions() }
    }

    fun displayChatMessage(userId: String, message: String) {
        hud.showChatMessage(userId, message)
    }

    fun displayPlayerStatus(userId: String, status: String) {
        hud.setDesiredStatus(userId, status)
    }

    fun displayTypingIndicator(userId: String, isTyping: Boolean) {
        hud.setTyping(userId, isTyping)
    }

    fun getUserIdForMesh(mesh: dynamic): String? =
        state.playerNodes.entries.find { (_, node) ->
            node.getChildMeshes().any { it === mesh }
        }?.key

    fun getPlayerData(userId: String): Pair<String, Boolean>? {
        val username = state.playerUsernames[userId] ?: "Player"
        val isPlayer = state.playerIsPlayerFlags[userId] ?: false
        return Pair(username, isPlayer)
    }

    fun getUserIdForUsername(username: String): String? =
        state.playerUsernames.entries.find { it.value == username }?.key

    fun getPlayerMeshes(): Array<AbstractMesh> =
        state.playerNodes.values
            .toTypedArray()
            .map { it.getChildMeshes() }
            .toTypedArray()
            .flatten()
            .toTypedArray()

    fun dispose() {
        // Remove meshes and text
        state.playerHandles.values.forEach { it.remove() }
        state.playerHandles.clear()
        state.playerNodes.clear()
        state.usernameTextHandles.values.forEach { it.remove() }
        state.usernameTextHandles.clear()

        // Clear HUD and timers
        hud.disposeAll()
        state.pendingAppearanceRefreshTimers.forEach { (_, id) -> window.clearTimeout(id) }
        state.pendingAppearanceRefreshTimers.clear()

        // Remove listeners
        presence.dispose()

        // Clear movement/animation bookkeeping
        state.playerTargetPositions.clear()
        state.playerAnimationTimes.clear()
        state.playerDirections.clear()
    }
}
