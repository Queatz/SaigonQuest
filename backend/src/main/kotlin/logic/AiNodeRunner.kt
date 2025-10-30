package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import services.DeepSeekClient
import utils.json

/**
 * Runner for AI nodes
 *
 * Sends the provided system and user prompts to DeepSeek using the
 * Structured JSON API and emits the returned text to the string output.
 *
 * This is a pure value node: no trigger input or output.
 */
class AiNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse AI node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val systemPrompt = "${getInput(PropertyKey.SYSTEM_PROMPT) ?: ""}\n\n Respond with a JSON object that has a single field named 'text' containing your reply."
        val userPrompt = getInput(PropertyKey.USER_PROMPT) ?: return false

        WebSocketDebug.logMessage(
            "AI_NODE_CALL",
            "Calling DeepSeek for node ${node.id}",
            context.session
        )

        val text = DeepSeekClient.promptText(systemPrompt, userPrompt, context.session)
        return if (!text.isNullOrBlank()) {
            // Emit to string output (TEXT at out_0)
            eventData["${node.id}_out_0"] = text
            eventData["${node.id}_text"] = text
            true
        } else {
            WebSocketDebug.logMessage(
                "AI_NODE_EMPTY",
                "DeepSeek returned empty/null text for node ${node.id}",
                context.session
            )
            false
        }
    }
}
