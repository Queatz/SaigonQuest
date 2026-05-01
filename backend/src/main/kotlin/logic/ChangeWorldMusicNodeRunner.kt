package logic

import debug.WebSocketDebug
import models.WebSocketAction
import models.WorldMusicUpdateData
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.WorldRepository
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import utils.json

/**
 * Runner for ChangeWorldMusic nodes
 */
class ChangeWorldMusicNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    private val worldRepository = WorldRepository()

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("CHANGE_WORLD_MUSIC_NODE_EXECUTE", "Executing ChangeWorldMusic node", context.session)

        // Parse node data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse ChangeWorldMusic node data", e, context.session)
            return false
        }

        val musicUrl = nodeData.properties.find { it.key == PropertyKey.MUSIC_URL }?.value?.trim().orEmpty()
        val musicName = nodeData.properties.find { it.key == PropertyKey.MUSIC_NAME }?.value?.trim().orEmpty()

        if (musicUrl.isBlank()) {
            WebSocketDebug.logError("ChangeWorldMusic: MUSIC_URL is missing or blank", null, context.session)
            return false
        }

        try {
            val world = worldRepository.getById(context.worldId) ?: return false
            
            val isOutdoors = world.isOutdoors
            val isNight = context.isNight ?: false // Default to day if unknown

            val updatedWorld = if (isOutdoors) {
                if (isNight) {
                    world.copy(nightMusicUrl = musicUrl, nightMusicTitle = musicName)
                } else {
                    world.copy(musicUrl = musicUrl, musicTitle = musicName)
                }
            } else {
                world.copy(musicUrl = musicUrl, musicTitle = musicName)
            }

            worldRepository.update(context.worldId, updatedWorld)

            // Notify all players in the world
            val updateData = WorldMusicUpdateData(
                dayUrl = updatedWorld.musicUrl,
                dayTitle = updatedWorld.musicTitle,
                nightUrl = updatedWorld.nightMusicUrl,
                nightTitle = updatedWorld.nightMusicTitle,
                isOutdoors = isOutdoors
            )

            val broadcast = WebSocketMessage(
                action = WebSocketAction.UPDATE_WORLD_MUSIC,
                data = json.encodeToString(WorldMusicUpdateData.serializer(), updateData)
            )
            val broadcastStr = json.encodeToString(WebSocketMessage.serializer(), broadcast)

            WebSocketUtils.broadcastToWorld(context.worldId, broadcastStr, context.activeConnections, context.session)
            
            WebSocketDebug.logMessage("CHANGE_WORLD_MUSIC_NODE_SUCCESS", "Updated world music and broadcasted to world ${context.worldId}", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("ChangeWorldMusic: failed to update world music", e, context.session)
            return false
        }
    }
}
