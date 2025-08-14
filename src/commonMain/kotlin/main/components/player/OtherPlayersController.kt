package main.components.player

import lib.Scene
import main.components.shadow.ShadowManager
import main.components.world.WorldObjectManager
import lib.AbstractMesh

/**
 * Facade delegating to a deterministic coordinator for other players.
 */
class OtherPlayersController(scene: Scene) {
    private val coordinator = OtherPlayersCoordinator(scene)

    fun setHttpWorldId(worldId: String) { coordinator.setHttpWorldId(worldId) }

    fun setWorldObjectManager(manager: WorldObjectManager) { coordinator.setWorldObjectManager(manager) }

    fun refreshCurrentUserId() { coordinator.refreshCurrentUserId() }

    fun setShadowManager(manager: ShadowManager) { coordinator.setShadowManager(manager) }

    fun setWorldMeshes(meshes: Array<AbstractMesh>) { coordinator.setWorldMeshes(meshes) }

    fun initialize() { coordinator.initialize() }

    fun getUserIdForMesh(mesh: dynamic): String? = coordinator.getUserIdForMesh(mesh)

    fun getPlayerData(userId: String): Pair<String, Boolean>? = coordinator.getPlayerData(userId)

    fun getUserIdForUsername(username: String): String? = coordinator.getUserIdForUsername(username)

    fun getPlayerMeshes(): Array<AbstractMesh> = coordinator.getPlayerMeshes()

    fun dispose() { coordinator.dispose() }
}
