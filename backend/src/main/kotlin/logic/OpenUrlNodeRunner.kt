package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import models.OpenUrlData
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json

/**
 * Runner for OpenUrl nodes
 *
 * This node runner handles opening URLs in a new browser window.
 * It sends an OPEN_URL message to the client to prompt the user to open the URL.
 */
class OpenUrlNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("OPEN_URL_NODE_EXECUTE", "Executing OpenUrl node with data: ${node.data}", context.session)
        
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        WebSocketDebug.logMessage("OPEN_URL_NODE_JSON_DATA", "Node JSON data: $jsonData", context.session)
        
        // Parse the JSON data to extract the URL
        val url = try {
            // Try to parse as PropertyBasedNodeData
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)
            // Find the property with key URL
            nodeData.properties.find { it.key == PropertyKey.URL }?.value
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse OpenUrl node data", e, context.session)
            null
        }
        
        if (url.isNullOrBlank()) {
            WebSocketDebug.logError("Missing or empty URL in OpenUrl node data", null, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("OPEN_URL_TARGET", "URL to open: $url", context.session)
        
        // Create an open URL message
        val userId = context.connection.userId
        WebSocketDebug.logMessage("OPEN_URL_USER", "Prompting user: $userId to open URL: $url", context.session)
        
        val openUrlData = OpenUrlData(
            userId = userId,
            url = url
        )
        
        val openUrlMessage = WebSocketMessage(
            action = WebSocketAction.OPEN_URL,
            data = json.encodeToString(openUrlData)
        )
        
        WebSocketDebug.logMessage("OPEN_URL_MESSAGE_CREATED", "Created open URL message with action: ${openUrlMessage.action}", context.session)
        
        try {
            // Send the open URL message to the client
            val messageJson = json.encodeToString(openUrlMessage)
            WebSocketDebug.logMessage("OPEN_URL_SENDING", "Sending open URL message: $messageJson", context.session)
            context.connection.session.send(messageJson)
            WebSocketDebug.logMessage("OPEN_URL_SENT", "Successfully sent open URL message to client", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to send open URL message", e, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("OPEN_URL_SUCCESS", "OpenUrl node execution completed successfully", context.session)
        return true
    }
}
