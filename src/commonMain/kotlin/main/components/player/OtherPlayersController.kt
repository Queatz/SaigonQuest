package main.components.player

import lib.AbstractMesh
import lib.Scene
import lib.Vector3
import main.components.world.WorldObjectManager

/**
 * Facade delegating to a deterministic coordinator for other players.
 */
class OtherPlayersController(scene: Scene) {
    private val coordinator = OtherPlayersCoordinator(scene)

    fun setHttpWorldId(worldId: String) {
        coordinator.setHttpWorldId(worldId)
    }

    fun setWorldObjectManager(manager: WorldObjectManager) {
        coordinator.setWorldObjectManager(manager)
    }

    fun refreshCurrentUserId() {
        coordinator.refreshCurrentUserId()
    }

    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        coordinator.setWorldMeshes(meshes)
    }

    fun sendJoinEvent(pos: Vector3) {
        coordinator.sendJoinEvent(pos)
    }

    fun initialize() {
        coordinator.initialize()
    }

    fun getUserIdForMesh(mesh: dynamic): String? = coordinator.getUserIdForMesh(mesh)

    fun getPlayerData(userId: String): Pair<String, Boolean>? = coordinator.getPlayerData(userId)

    fun getUserIdForUsername(username: String): String? = coordinator.getUserIdForUsername(username)

    fun getPlayerMeshes(): Array<AbstractMesh> = coordinator.getPlayerMeshes()

    fun dispose() {
        coordinator.dispose()
    }
}
