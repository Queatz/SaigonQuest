package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import utils.json

/**
 * Runner for PlainText nodes.
 * Simply takes the input text and provides it as an output.
 */
class PlainTextNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse PlainText node data", e, context.session)
            PropertyBasedNodeData()
        }

        val text = nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value ?: ""

        WebSocketDebug.logMessage(
            "PLAIN_TEXT_NODE",
            "Resolved text content: '${text.take(50)}${if (text.length > 50) "..." else ""}'",
            context.session
        )

        // Output index 0 is the text
        eventData["${node.id}_out_0"] = text
        // Named output for easier access
        eventData["${node.id}_text"] = text

        return true
    }
}
