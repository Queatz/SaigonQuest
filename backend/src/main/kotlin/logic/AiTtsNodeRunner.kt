package logic

import debug.WebSocketDebug
import models.WebSocketAction
import models.ObjectTypingData
import models.nodeeditor.*
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import services.AiManager
import utils.json

/**
 * Runner for AI TTS generation nodes
 */
class AiTtsNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse AI TTS node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun outKey(key: PropertyKey) = "${node.id}_out_${key.name.lowercase()}"
        
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val text = getInput(PropertyKey.TEXT_CONTENT)

        if (text == null) {
            WebSocketDebug.logMessage("AI_TTS_NODE_SKIP", "No text for node ${node.id}", context.session)
            return true
        }

        WebSocketDebug.logMessage(
            "AI_TTS_NODE_EXECUTE",
            "Generating audio for node ${node.id} with text: $text",
            context.session
        )

        // Broadcast OBJECT_TYPING true (start) if we have an objectId
        if (context.objectId.isNotBlank()) {
            try {
                val startMsg = WebSocketMessage(
                    action = WebSocketAction.OBJECT_TYPING,
                    data = json.encodeToString(ObjectTypingData.serializer(), ObjectTypingData(context.objectId, true))
                )
                val startStr = json.encodeToString(WebSocketMessage.serializer(), startMsg)
                WebSocketUtils.broadcastToWorld(context.worldId, startStr, context.activeConnections, context.session)
                WebSocketDebug.logMessage("OBJECT_TYPING_START", "objectId=${context.objectId}", context.session)
            } catch (e: Throwable) {
                WebSocketDebug.logError("Failed to broadcast OBJECT_TYPING start", e, context.session)
            }
        }

        val audioUrl = try {
            AiManager.ttsClient.ttsUrl(text, token = context.connection.token)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to generate AI TTS", e, context.session)
            null
        } finally {
            // Always broadcast OBJECT_TYPING false (stop)
            if (context.objectId.isNotBlank()) {
                try {
                    val stopMsg = WebSocketMessage(
                        action = WebSocketAction.OBJECT_TYPING,
                        data = json.encodeToString(ObjectTypingData.serializer(), ObjectTypingData(context.objectId, false))
                    )
                    val stopStr = json.encodeToString(WebSocketMessage.serializer(), stopMsg)
                    WebSocketUtils.broadcastToWorld(context.worldId, stopStr, context.activeConnections, context.session)
                    WebSocketDebug.logMessage("OBJECT_TYPING_STOP", "objectId=${context.objectId}", context.session)
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Failed to broadcast OBJECT_TYPING stop", e, context.session)
                }
            }
        }

        if (audioUrl != null) {
            WebSocketDebug.logMessage(
                "AI_TTS_NODE_SUCCESS",
                "Generated audio for node ${node.id}: $audioUrl",
                context.session
            )
            // Output to both the named key and the indexed port for StringTemplate compatibility
            eventData[outKey(PropertyKey.AUDIO_URL)] = audioUrl
            eventData["${node.id}_out_0"] = audioUrl

            // Markdown output
            val audioMarkdown = "![audio]($audioUrl)"
            eventData[outKey(PropertyKey.AUDIO_MARKDOWN)] = audioMarkdown
            eventData["${node.id}_out_1"] = audioMarkdown

            // Convenience alias for easier resolution
            eventData["${node.id}_audio_markdown"] = audioMarkdown
            eventData["${node.id}_audio_url"] = audioUrl
        } else {
            WebSocketDebug.logMessage(
                "AI_TTS_NODE_FAILURE",
                "Failed to generate audio for node ${node.id}",
                context.session
            )
        }

        return true
    }
}
