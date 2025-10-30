package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import repository.UserRepository

/**
 * Runner for PlayerName nodes.
 * Produces the current user/player name as a string output (index 0).
 */
class PlayerNameNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val user = try { UserRepository().getById(context.connection.userId) } catch (_: Throwable) { null }
        val name = user?.username?.takeIf { it.isNotBlank() } ?: ""
        WebSocketDebug.logMessage(
            "PLAYER_NAME_NODE",
            "Resolved name='$name' for userId=${context.connection.userId}",
            context.session
        )

        // First (and only) output is string NAME at index 0
        val outPortId = "${node.id}_out_0"
        eventData[outPortId] = name
        // Optional convenience alias for downstream consumption
        eventData["${node.id}_name"] = name
        return true
    }
}
