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
 * Runner for SetAsset nodes
 *
 * Changes the object's asset (file and collection) and broadcasts an OBJECT_UPDATE so all clients refresh the object.
 */
class SetAssetNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("SET_ASSET_NODE_EXECUTE", "Executing SetAsset node with data: ${node.data}", context.session)

        // Extract file and collection from input overrides or node properties
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("SetAsset: failed to parse node data", e, context.session)
            PropertyBasedNodeData(emptyList())
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        
        val fileOverride = (eventData[inKey(PropertyKey.FILE)] as? String)?.trim()
        val finalFile = if (!fileOverride.isNullOrBlank()) {
            fileOverride
        } else {
            nodeData.properties.find { it.key == PropertyKey.FILE }?.value?.trim().orEmpty()
        }

        val collectionOverride = (eventData[inKey(PropertyKey.COLLECTION)] as? String)?.trim()
        val finalCollection = if (!collectionOverride.isNullOrBlank()) {
            collectionOverride
        } else {
            nodeData.properties.find { it.key == PropertyKey.COLLECTION }?.value?.trim().orEmpty()
        }

        if (finalFile.isBlank() || finalCollection.isBlank()) {
            WebSocketDebug.logError("SetAsset: file or collection is missing. File: '$finalFile', Collection: '$finalCollection'", null, context.session)
            return false
        }

        WebSocketDebug.logMessage("SET_ASSET_VALUE", "New asset: file='$finalFile', collection='$finalCollection'", context.session)

        // Load the object
        val repo = ObjectRepository()
        val obj = try { repo.getById(context.objectId) } catch (_: Throwable) { null }
        if (obj == null) {
            WebSocketDebug.logError("SetAsset: object not found: ${context.objectId}", null, context.session)
            return false
        }

        // Update and persist
        val updated = try {
            val newObj = obj.copy(file = finalFile, collection = finalCollection)
            repo.update(obj.key, newObj)
        } catch (e: Throwable) {
            WebSocketDebug.logError("SetAsset: failed to persist object asset", e, context.session)
            null
        }
        if (updated == null) {
            return false
        }

        // Broadcast OBJECT_UPDATE to all clients in the world
        try {
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.OBJECT_UPDATE,
                data = json.encodeToString(Object.serializer(), updated),
            )
            val messageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketUtils.broadcastToWorld(context.worldId, messageString, context.activeConnections, context.session)
            WebSocketDebug.logMessage("SET_ASSET_BROADCAST", "Broadcasted OBJECT_UPDATE for object ${updated.key}", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("SetAsset: failed to broadcast OBJECT_UPDATE", e, context.session)
        }

        return true
    }
}
