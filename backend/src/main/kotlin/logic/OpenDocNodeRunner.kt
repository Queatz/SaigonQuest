package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import models.OpenDocData
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json

/**
 * Runner for OpenDoc nodes
 *
 * This node runner handles opening a specific document in the WorldDocsDialog.
 * It sends an OPEN_DOC message to the client.
 */
class OpenDocNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("OPEN_DOC_NODE_EXECUTE", "Executing OpenDoc node with data: ${node.data}", context.session)
        
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        
        // Parse the JSON data to extract the Doc ID
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse OpenDoc node data", e, context.session)
            PropertyBasedNodeData()
        }

        fun inKey(name: String) = "${node.id}_in_${name}"
        fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
            val eventVal = eventData[inKey(nameLower)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }
        }

        val docId = fromEventOrNode("doc", PropertyKey.ID)
        
        if (docId.isNullOrBlank()) {
            WebSocketDebug.logError("Missing or empty docId in OpenDoc node data", null, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("OPEN_DOC_TARGET", "Doc ID to open: $docId", context.session)
        
        val userId = context.connection.userId
        
        val openDocData = OpenDocData(
            userId = userId,
            docId = docId
        )
        
        val openDocMessage = WebSocketMessage(
            action = WebSocketAction.OPEN_DOC,
            data = json.encodeToString(openDocData)
        )
        
        try {
            // Send the message to the client
            val messageJson = json.encodeToString(openDocMessage)
            context.connection.session.send(messageJson)
            WebSocketDebug.logMessage("OPEN_DOC_SENT", "Successfully sent open DOC message to client", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to send open DOC message", e, context.session)
            return false
        }
        
        return true
    }
}
