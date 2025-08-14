package logic

import debug.WebSocketDebug
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import utils.json

/**
 * Runner for Delay nodes
 *
 * Waits for the configured number of seconds (default 5) before continuing.
 */
class DelayNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val jsonData = node.data["data"] ?: "{}"

        // Default to 5 seconds if missing or invalid
        var delaySeconds = 5.0
        try {
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)
            val secondsStr = nodeData.properties.find { it.key == PropertyKey.DELAY_SECONDS }?.value
            val parsed = secondsStr?.toDoubleOrNull()
            if (parsed != null) {
                delaySeconds = parsed
            }
        } catch (e: Throwable) {
            // Keep default on parse error
            WebSocketDebug.logError("Failed to parse Delay node data for ${node.id}", e, context.session)
        }

        // Normalize to non-negative and convert to ms
        val delayMs = (delaySeconds.coerceAtLeast(0.0) * 1000.0).toLong()

        WebSocketDebug.logMessage(
            "DELAY_NODE_EXECUTE",
            "Executing Delay node: ${node.id}. Waiting ${delayMs}ms...",
            context.session
        )

        try {
            if (delayMs > 0) delay(delayMs)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Delay interrupted for node: ${node.id}", e, context.session)
            return false
        }

        WebSocketDebug.logMessage(
            "DELAY_NODE_COMPLETE",
            "Delay complete for node: ${node.id}",
            context.session
        )
        return true
    }
}
