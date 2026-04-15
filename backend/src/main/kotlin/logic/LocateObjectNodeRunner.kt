package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json

/**
 * Runner for LocateObject nodes
 *
 * This node runner handles moving a player to an object's location.
 * It sends a LOCATE_OBJECT message to the client.
 */
class LocateObjectNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("LOCATE_OBJECT_NODE_EXECUTE", "Executing locate object node", context.session)
        
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            PropertyBasedNodeData()
        }

        fun inKey(name: String) = "${node.id}_in_${name}"
        fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
            val eventVal = eventData[inKey(nameLower)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value
        }

        val objectId = fromEventOrNode("id", PropertyKey.ID)
        val worldId = fromEventOrNode("target_world_id", PropertyKey.TARGET_WORLD_ID)
        
        if (objectId == null) {
            WebSocketDebug.logError("Missing objectId in locate object node data", null, context.session)
            return false
        }
        
        val locateData = mutableMapOf(
            "objectId" to objectId
        )
        if (!worldId.isNullOrBlank()) {
            locateData["worldId"] = worldId
            val worldRepository = repository.WorldRepository()
            worldRepository.getById(worldId)?.let { world ->
                locateData["worldUrl"] = world.url
            }
        }
        
        val locateMessage = WebSocketMessage(
            action = WebSocketAction.LOCATE_OBJECT,
            data = json.encodeToString(locateData)
        )
        
        try {
            val messageJson = json.encodeToString(locateMessage)
            context.connection.session.send(messageJson)
            WebSocketDebug.logMessage("LOCATE_OBJECT_SENT", "Successfully sent locate object message to client", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to send locate object message", e, context.session)
            return false
        }
        
        return true
    }
}
