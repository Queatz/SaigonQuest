package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import models.DialogueConfirmation
import models.DialogueData
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Runner for PlayerAudioInput nodes
 *
 * Displays a dialog with a title, description, audio upload button and a confirm button.
 * On confirm, the uploaded audio URLs are propagated via the node's string output.
 */
class PlayerAudioInputNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerAudioInputNodeRunner::class.java)
        private val pendingConfirmations = ConcurrentHashMap<String, CompletableDeferred<List<String>?>>()
        private const val CONFIRMATION_TIMEOUT = 600L // 10 minutes (longer timeout for audio upload)

        fun processConfirmation(confirmation: DialogueConfirmation): Boolean {
            val dialogueId = confirmation.dialogueId
            val deferred = pendingConfirmations[dialogueId] ?: return false
            val ok = deferred.complete(confirmation.audioUrls)
            logger.debug("PlayerAudioInput processConfirmation: completed id={} ok={} urls_present={}", dialogueId, ok, confirmation.audioUrls != null)
            return ok
        }
    }

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse PlayerAudioInput node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val title = getInput(PropertyKey.TITLE)
        val description = getInput(PropertyKey.TEXT_CONTENT) ?: ""
        val allowMultiple = nodeData.properties.find { it.key == PropertyKey.ALLOW_MULTIPLE }?.value?.toBoolean() ?: false
        val confirmLabel = getInput(PropertyKey.CONFIRM_LABEL) ?: "Confirm"

        val dialogueId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<List<String>?>()
        pendingConfirmations[dialogueId] = deferred

        val dialogueData = DialogueData(
            dialogueId = dialogueId,
            userId = context.connection.userId,
            text = description,
            nodeId = node.id,
            buttons = listOf(confirmLabel),
            imageUrl = null,
            objectTitle = null,
            title = title,
            requiresAudio = true,
            allowMultipleAudio = allowMultiple,
            confirmLabel = confirmLabel,
        )

        val message = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = json.encodeToString(dialogueData)
        )

        WebSocketDebug.logMessage("PLAYER_AUDIO_INPUT_SHOW_SEND", "dialogueId=$dialogueId nodeId=${node.id}", context.session)
        context.connection.session.send(json.encodeToString(message))

        val audioUrls = try {
            WebSocketDebug.logMessage("PLAYER_AUDIO_INPUT_AWAIT_START", "dialogueId=$dialogueId", context.session)
            withTimeoutOrNull(CONFIRMATION_TIMEOUT.seconds) { deferred.await() }
        } finally {
            pendingConfirmations.remove(dialogueId)
            if (!deferred.isCompleted) deferred.cancel()
        }

        return if (audioUrls != null) {
            WebSocketDebug.logMessage("PLAYER_AUDIO_INPUT_CONFIRMED", "dialogueId=$dialogueId count=${audioUrls.size}", context.session)

            if (audioUrls.isEmpty()) {
                eventData["outputProperty"] = PropertyKey.NO_AUDIO
            } else {
                eventData["outputProperty"] = PropertyKey.NEXT

                // Store URLs into the string output port (index 2)
                val result = audioUrls.joinToString(",")
                val outPortId = "${node.id}_out_2"
                eventData[outPortId] = result
                // Also store a convenience "audio_url" key for easier resolution by downstream processors
                eventData["${node.id}_audio_url"] = result

                // Store markdown into the string output port (index 3)
                val markdown = audioUrls.joinToString(" ") { "![audio]($it)" }
                val markdownPortId = "${node.id}_out_3"
                eventData[markdownPortId] = markdown
                // Also store a convenience "audio_markdown" key
                eventData["${node.id}_audio_markdown"] = markdown
            }
            true
        } else {
            WebSocketDebug.logMessage("PLAYER_AUDIO_INPUT_TIMEOUT_OR_CANCEL", "dialogueId=$dialogueId", context.session)
            false
        }
    }
}
