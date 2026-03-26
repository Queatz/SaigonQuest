package logic

import debug.WebSocketDebug
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import utils.json

/**
 * Runner for PlaySound nodes
 *
 * Plays a sound for everyone in the world or just the current user.
 */
class PlaySoundNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("PLAY_SOUND_NODE_EXECUTE", "Executing PlaySound node with data: ${node.data}", context.session)

        // Parse node data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse PlaySound node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        val overrideFromInput = (eventData[inKey(PropertyKey.SOUND_URL)] as? String)?.trim().orEmpty()
        val soundUrl = if (overrideFromInput.isNotBlank()) {
            overrideFromInput
        } else {
            nodeData.properties.find { it.key == PropertyKey.SOUND_URL }?.value?.trim().orEmpty()
        }

        val playForEveryone = nodeData.properties.find { it.key == PropertyKey.PLAY_FOR_EVERYONE }?.value?.toBooleanStrictOrNull() ?: true

        if (soundUrl.isBlank()) {
            WebSocketDebug.logError("PlaySound: SOUND_URL is missing or blank", null, context.session)
            return false
        }

        try {
            // Broadcast to world or current user
            val broadcast = WebSocketMessage(
                action = WebSocketAction.PLAY_SOUND,
                data = json.encodeToString(mapOf("url" to soundUrl))
            )
            val broadcastStr = json.encodeToString(WebSocketMessage.serializer(), broadcast)

            if (playForEveryone) {
                WebSocketUtils.broadcastToWorld(context.worldId, broadcastStr, context.activeConnections, context.session)
                WebSocketDebug.logMessage("PLAY_SOUND_NODE_SUCCESS", "Broadcast play sound message to world ${context.worldId}", context.session)
            } else {
                WebSocketUtils.broadcastToUser(context.connection.userId, broadcastStr, context.activeConnections, context.session)
                WebSocketDebug.logMessage("PLAY_SOUND_NODE_SUCCESS", "Send play sound message to current user ${context.connection.userId}", context.session)
            }

            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("PlaySound: failed to broadcast message", e, context.session)
            return false
        }
    }
}
