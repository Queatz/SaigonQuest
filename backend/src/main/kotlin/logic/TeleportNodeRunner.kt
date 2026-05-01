package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import models.Position
import models.WebSocketAction
import models.nodeeditor.NodeData
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.SavedPositionRepository
import repository.WorldRepository
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
        
        // Parse the JSON data
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse teleport node data", e, context.session)
            PropertyBasedNodeData()
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun fromEventOrNode(key: PropertyKey): String? {
            val eventVal = eventData[inKey(key)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value
        }

        var targetWorldUrl = fromEventOrNode(PropertyKey.TARGET_WORLD_ID)
        
        if (targetWorldUrl.isNullOrBlank()) {
            WebSocketDebug.logError("Missing targetWorldUrl in teleport node data", null, context.session)
            return false
        }

        // Try to resolve world ID to URL if it's an ID
        WorldRepository().getById(targetWorldUrl!!)?.let { world ->
            targetWorldUrl = world.url
        }

        val savedPositionId = fromEventOrNode(PropertyKey.SAVED_POSITION_ID)
        var targetPosition: Position? = null

        if (!savedPositionId.isNullOrBlank()) {
            val savedPos = SavedPositionRepository().getById(savedPositionId)
            if (savedPos != null) {
                targetPosition = savedPos.position
            }
        }

        if (targetPosition == null) {
            val positionStr = fromEventOrNode(PropertyKey.TARGET_POSITION)
            if (!positionStr.isNullOrBlank()) {
                val parts = positionStr.split(",")
                if (parts.size == 3) {
                    try {
                        targetPosition = Position(parts[0].trim().toFloat(), parts[1].trim().toFloat(), parts[2].trim().toFloat())
                    } catch (e: Throwable) {
                        WebSocketDebug.logError("Teleport: failed to parse position: $positionStr", e, context.session)
                    }
                }
            }
        }
        
        WebSocketDebug.logMessage("TELEPORT_TARGET", "Teleport target: $targetWorldUrl at $targetPosition", context.session)
        
        // Create a teleport message
        val userId = context.connection.userId
        
        val teleportData = mutableMapOf(
            "userId" to userId,
            "targetWorldUrl" to targetWorldUrl!!
        )
        
        targetPosition?.let {
            teleportData["x"] = it.x.toString()
            teleportData["y"] = it.y.toString()
            teleportData["z"] = it.z.toString()
        }
        
        val teleportMessage = WebSocketMessage(
            action = WebSocketAction.TELEPORT_PLAYER,
            data = json.encodeToString(teleportData)
        )
        
        try {
            // Send the teleport message to the client
            val messageJson = json.encodeToString(teleportMessage)
            context.connection.session.send(messageJson)
            WebSocketDebug.logMessage("TELEPORT_SENT", "Successfully sent teleport message to client", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to send teleport message", e, context.session)
            return false
        }
        
        return true
    }
}
