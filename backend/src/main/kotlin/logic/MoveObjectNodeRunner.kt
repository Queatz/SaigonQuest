package logic

import debug.WebSocketDebug
import models.Position
import models.WebSocketAction
import models.MoveObjectData
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.ObjectRepository
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import utils.json
import kotlinx.serialization.encodeToString

/**
 * Runner for MoveObject nodes
 *
 * This node runner handles moving an object to a new 3D position.
 * It updates the position in the database and broadcasts a MOVE_OBJECT message to all clients.
 */
class MoveObjectNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("MOVE_OBJECT_NODE_EXECUTE", "Executing MoveObject node with data: ${node.data}", context.session)

        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("MoveObject: failed to parse node data", e, context.session)
            PropertyBasedNodeData(emptyList())
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        
        fun fromEventOrNode(key: PropertyKey): String? {
            val eventVal = eventData[inKey(key)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value
        }

        val positionStr = fromEventOrNode(PropertyKey.TARGET_POSITION)
        val animateStr = fromEventOrNode(PropertyKey.ANIMATE) ?: "true"

        if (positionStr.isNullOrBlank()) {
            WebSocketDebug.logError("MoveObject: target position is missing", null, context.session)
            return false
        }

        val parts = positionStr.split(",")
        if (parts.size != 3) {
            WebSocketDebug.logError("MoveObject: invalid position format: $positionStr", null, context.session)
            return false
        }

        val targetPosition = try {
            Position(parts[0].trim().toFloat(), parts[1].trim().toFloat(), parts[2].trim().toFloat())
        } catch (e: Throwable) {
            WebSocketDebug.logError("MoveObject: failed to parse position: $positionStr", e, context.session)
            return false
        }

        val animate = animateStr.lowercase() == "true"

        WebSocketDebug.logMessage("MOVE_OBJECT_VALUE", "Target position: $targetPosition, animate: $animate", context.session)

        // Load the object
        val repo = ObjectRepository()
        val obj = try { repo.getById(context.objectId) } catch (_: Throwable) { null }
        if (obj == null) {
            WebSocketDebug.logError("MoveObject: object not found: ${context.objectId}", null, context.session)
            return false
        }

        // Update and persist
        val updated = try {
            val newObj = obj.copy(position = targetPosition)
            repo.update(obj.key, newObj)
        } catch (e: Throwable) {
            WebSocketDebug.logError("MoveObject: failed to persist object position", e, context.session)
            null
        }
        if (updated == null) {
            return false
        }

        // Broadcast MOVE_OBJECT to all clients in the world
        try {
            val moveData = MoveObjectData(
                objectId = updated.key,
                position = targetPosition,
                animate = animate
            )
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.MOVE_OBJECT,
                data = json.encodeToString(MoveObjectData.serializer(), moveData),
            )
            val messageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketUtils.broadcastToWorld(context.worldId, messageString, context.activeConnections, context.session)
            WebSocketDebug.logMessage("MOVE_OBJECT_BROADCAST", "Broadcasted MOVE_OBJECT for object ${updated.key}", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("MoveObject: failed to broadcast MOVE_OBJECT", e, context.session)
        }

        return true
    }
}
