package logic

import debug.WebSocketDebug
import models.WebSocketAction
import models.ObjectTypingData
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import services.AiManager
import utils.json

/**
 * Runner for Transcribe Audio nodes
 *
 * Converts an audio file URL to text using the configured AI service.
 * This is a pure value node: no trigger input or output.
 */
class TranscribeAudioNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse Transcribe Audio node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val audioUrl = getInput(PropertyKey.AUDIO_URL)
        if (audioUrl.isNullOrBlank()) {
            WebSocketDebug.logMessage(
                "TRANSCRIBE_AUDIO_NODE_MISSING_URL",
                "No audio URL provided for node ${node.id}",
                context.session
            )
            return false
        }

        WebSocketDebug.logMessage(
            "TRANSCRIBE_AUDIO_NODE_CALL",
            "Transcribing audio for node ${node.id}: $audioUrl",
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

        val text = try {
            AiManager.client.transcribe(audioUrl)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Transcription failed for node ${node.id}", e, context.session)
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

        return if (text != null) {
            WebSocketDebug.logMessage(
                "TRANSCRIBE_AUDIO_NODE_SUCCESS",
                "Transcription successful for node ${node.id}",
                context.session
            )
            // Emit to result output (RESULT at out_0)
            eventData["${node.id}_out_0"] = text
            // Convenience alias
            eventData["${node.id}_result"] = text
            true
        } else {
            WebSocketDebug.logMessage(
                "TRANSCRIBE_AUDIO_NODE_FAILED",
                "Transcription failed or returned null for node ${node.id}",
                context.session
            )
            false
        }
    }
}
