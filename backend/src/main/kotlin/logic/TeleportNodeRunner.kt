package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import models.WebSocketAction
import models.nodeeditor.NodeData
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json

/**
 * Runner for teleport nodes
 *
 * This node runner handles teleporting players to a different location.
 * It sends a TELEPORT_PLAYER message to the client to change the world.
 */
class TeleportNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("TELEPORT_NODE_EXECUTE", "Executing teleport node with data: ${node.data}", context.session)
        
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        WebSocketDebug.logMessage("TELEPORT_NODE_JSON_DATA", "Node JSON data: $jsonData", context.session)
        
        // Parse the JSON data to extract the worldId
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse teleport node data", e, context.session)
            PropertyBasedNodeData()
        }

        fun inKey(name: String) = "${node.id}_in_${name}"
        fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
            val eventVal = eventData[inKey(nameLower)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value
        }

        var targetWorldUrl = fromEventOrNode("target_world_id", PropertyKey.TARGET_WORLD_ID)
        
        if (targetWorldUrl == null) {
            WebSocketDebug.logError("Missing worldId in teleport node data", null, context.session)
            return false
        }

        val worldRepository = repository.WorldRepository()
        worldRepository.getById(targetWorldUrl)?.let { world ->
            targetWorldUrl = world.url
        } ?: run {
            WebSocketDebug.logError("World with ID $targetWorldUrl not found", null, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("TELEPORT_TARGET", "Teleport target worldId: $targetWorldUrl", context.session)
        
        // Create a teleport message
        val userId = context.connection.userId
        WebSocketDebug.logMessage("TELEPORT_USER", "Teleporting user: $userId to world: $targetWorldUrl", context.session)
        
        val teleportData = mapOf(
            "userId" to userId,
            "targetWorldUrl" to targetWorldUrl
        )
        
        val teleportMessage = WebSocketMessage(
            action = WebSocketAction.TELEPORT_PLAYER,
            data = json.encodeToString(teleportData)
        )
        
        WebSocketDebug.logMessage("TELEPORT_MESSAGE_CREATED", "Created teleport message with action: ${teleportMessage.action}", context.session)
        
        try {
            // Send the teleport message to the client
            val messageJson = json.encodeToString(teleportMessage)
            WebSocketDebug.logMessage("TELEPORT_SENDING", "Sending teleport message: $messageJson", context.session)
            context.connection.session.send(messageJson)
            WebSocketDebug.logMessage("TELEPORT_SENT", "Successfully sent teleport message to client", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to send teleport message", e, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("TELEPORT_SUCCESS", "Teleport node execution completed successfully", context.session)
        return true
    }
}
