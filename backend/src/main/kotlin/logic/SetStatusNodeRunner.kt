package logic

import debug.WebSocketDebug
import models.Object
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.ObjectRepository
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import utils.json

/**
 * Runner for SetStatus nodes
 *
 * Sets the object's status text and broadcasts an OBJECT_UPDATE so all clients refresh the object.
 */
class SetStatusNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("SET_STATUS_NODE_EXECUTE", "Executing SetStatus node with data: ${node.data}", context.session)

        // Extract status text from input override or node property
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("SetStatus: failed to parse node data", e, context.session)
            PropertyBasedNodeData(emptyList())
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        val overrideFromInput = (eventData[inKey(PropertyKey.TEXT_CONTENT)] as? String)?.trim().orEmpty()
        val statusText = if (overrideFromInput.isNotBlank()) {
            overrideFromInput
        } else {
            nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value?.trim().orEmpty()
        }

        // Status can be blank to clear existing status; that's allowed
        WebSocketDebug.logMessage("SET_STATUS_VALUE", "New status='${statusText}'", context.session)

        // Load the object
        val repo = ObjectRepository()
        val obj = try { repo.getById(context.objectId) } catch (_: Throwable) { null }
        if (obj == null) {
            WebSocketDebug.logError("SetStatus: object not found: ${context.objectId}", null, context.session)
            return false
        }

        // Update and persist
        val updated = try {
            val newObj = obj.copy(status = statusText)
            repo.update(obj.key, newObj)
        } catch (e: Throwable) {
            WebSocketDebug.logError("SetStatus: failed to persist object status", e, context.session)
            null
        }
        if (updated == null) {
            return false
        }

        // Broadcast OBJECT_UPDATE to all clients in the world (mirror ObjectRoutes.kt behavior)
        try {
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.OBJECT_UPDATE,
                data = json.encodeToString(Object.serializer(), updated),
            )
            val messageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketUtils.broadcastToWorld(context.worldId, messageString, context.activeConnections, context.session)
            WebSocketDebug.logMessage("SET_STATUS_BROADCAST", "Broadcasted OBJECT_UPDATE for object ${updated.key}", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("SetStatus: failed to broadcast OBJECT_UPDATE", e, context.session)
            // Non-fatal: status already updated; still consider node success
        }

        return true
    }
}
