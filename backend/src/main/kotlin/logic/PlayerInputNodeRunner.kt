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
 * Runner for PlayerInput nodes
 *
 * Displays a dialog with a title, text, an input field and a confirm button.
 * On confirm, the entered text is propagated via the node's string output.
 */
class PlayerInputNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerInputNodeRunner::class.java)
        private val pendingConfirmations = ConcurrentHashMap<String, CompletableDeferred<String?>>()
        private const val CONFIRMATION_TIMEOUT = 300L // 5 minutes

        fun processConfirmation(confirmation: DialogueConfirmation): Boolean {
            val dialogueId = confirmation.dialogueId
            val deferred = pendingConfirmations[dialogueId] ?: return false
            val ok = deferred.complete(confirmation.inputText)
            logger.debug("PlayerInput processConfirmation: completed id={} ok={} text_present={}", dialogueId, ok, confirmation.inputText != null)
            return ok
        }
    }

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse PlayerInput node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val text = getInput(PropertyKey.TEXT_CONTENT) ?: ""
        val title = getInput(PropertyKey.TITLE)
        val placeholder = getInput(PropertyKey.PLACEHOLDER)
        val confirmLabel = getInput(PropertyKey.CONFIRM_LABEL) ?: "OK"

        val dialogueId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        pendingConfirmations[dialogueId] = deferred

        val dialogueData = DialogueData(
            dialogueId = dialogueId,
            userId = context.connection.userId,
            text = text,
            nodeId = node.id,
            buttons = listOf(confirmLabel),
            imageUrl = null,
            objectTitle = null,
            title = title,
            requiresInput = true,
            placeholder = placeholder,
            confirmLabel = confirmLabel,
        )

        val message = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = json.encodeToString(dialogueData)
        )

        WebSocketDebug.logMessage("PLAYER_INPUT_SHOW_SEND", "dialogueId=$dialogueId nodeId=${node.id}", context.session)
        context.connection.session.send(json.encodeToString(message))

        val inputText = try {
            WebSocketDebug.logMessage("PLAYER_INPUT_AWAIT_START", "dialogueId=$dialogueId", context.session)
            withTimeoutOrNull(CONFIRMATION_TIMEOUT.seconds) { deferred.await() }
        } finally {
            pendingConfirmations.remove(dialogueId)
            if (!deferred.isCompleted) deferred.cancel()
        }

        return if (inputText != null) {
            WebSocketDebug.logMessage("PLAYER_INPUT_CONFIRMED", "dialogueId=$dialogueId textPresent=${!inputText.isNullOrEmpty()}", context.session)
            // Store text into the string output port (index 1)
            val outPortId = "${node.id}_out_1"
            eventData[outPortId] = inputText
            // Also store a convenience "text" key for easier resolution by downstream processors
            eventData["${node.id}_text"] = inputText
            true
        } else {
            WebSocketDebug.logMessage("PLAYER_INPUT_TIMEOUT_OR_CANCEL", "dialogueId=$dialogueId", context.session)
            false
        }
    }
}
