package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import models.AiChatData
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json

/**
 * Runner for AI chat nodes
 *
 * Sends a command to the client to open a new AI chat session
 * with the provided system prompt and initial message.
 */
class AiChatNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse AI chat node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val systemPrompt = getInput(PropertyKey.SYSTEM_PROMPT)
        val initialUserMessage = getInput(PropertyKey.USER_PROMPT)
        val initialAssistantMessage = getInput(PropertyKey.INITIAL_MESSAGE)

        WebSocketDebug.logMessage(
            "AI_CHAT_NODE_EXECUTE",
            "Opening AI chat for node ${node.id}",
            context.session
        )

        val aiChatData = AiChatData(
            userId = context.connection.userId,
            systemPrompt = systemPrompt,
            initialUserMessage = initialUserMessage,
            initialAssistantMessage = initialAssistantMessage
        )

        val aiChatMessage = WebSocketMessage(
            action = WebSocketAction.OPEN_AI_CHAT,
            data = json.encodeToString(AiChatData.serializer(), aiChatData)
        )

        try {
            context.connection.session.send(json.encodeToString(WebSocketMessage.serializer(), aiChatMessage))
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to send OPEN_AI_CHAT message", e, context.session)
            return false
        }

        return true
    }
}
