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
import repository.ObjectRepository
import routes.WebSocketMessage
import utils.json
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Runner for dialogue nodes
 *
 * This node runner handles displaying text messages to players.
 * It sends a SHOW_DIALOGUE message to the client to display the text,
 * then waits for the user to click OK before continuing.
 */
class DialogueNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(DialogueNodeRunner::class.java)
        // Map to track pending dialogue confirmations with a deferred button index
        private val pendingConfirmations = ConcurrentHashMap<String, CompletableDeferred<Int>>()
        
        // Timeout for waiting for confirmation (in seconds)
        private const val CONFIRMATION_TIMEOUT = 300L // 5 minutes
        
        /**
         * Process a dialogue confirmation
         *
         * @param confirmation The confirmation data
         * @return True if the confirmation was processed, false otherwise
         */
        fun processConfirmation(confirmation: DialogueConfirmation): Boolean {
            val dialogueId = confirmation.dialogueId
            val deferred = pendingConfirmations[dialogueId]
            if (deferred == null) {
                logger.debug("Dialogue processConfirmation: no pending for id={}", dialogueId)
                return false
            }
            val ok = deferred.complete(confirmation.buttonIndex)
            logger.debug("Dialogue processConfirmation: completed id={} ok={} index={}", dialogueId, ok, confirmation.buttonIndex)
            return ok
        }
    }
    
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        
        // Parse the JSON data to extract the text and buttons
        val nodeData = try {
            // Try to parse as PropertyBasedNodeData
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse dialogue node data", e, context.session)
            return false
        }
        
        // Extract text content
        val text = nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value ?: return false
        
        // Extract optional image URL
        val imageUrl = nodeData.properties.find { it.key == PropertyKey.IMAGE_URL }?.value?.takeIf { it.isNotBlank() }

        // Extract buttons (default to "OK" if none specified)
        val buttonsValue = nodeData.properties.find { it.key == PropertyKey.BUTTONS }?.value ?: "OK"
        val buttons = buttonsValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val buttonsList = buttons.ifEmpty { listOf("OK") }
        
        // Resolve object title if we have an objectId
        val objectTitle = try {
            if (context.objectId.isNotBlank()) ObjectRepository().getById(context.objectId)?.title?.takeIf { it.isNotBlank() } else null
        } catch (e: Throwable) {
            null
        }
        
        // Generate a unique dialogue ID
        val dialogueId = UUID.randomUUID().toString()
        
        // Register the dialogue ID in the pending confirmations map with a deferred result
        val deferred = CompletableDeferred<Int>()
        logger.debug("Dialogue register: id={}", dialogueId)
        pendingConfirmations[dialogueId] = deferred
        
        // Create a dialogue data object with buttons
        val dialogueData = DialogueData(
            dialogueId = dialogueId,
            userId = context.connection.userId,
            text = text,
            nodeId = node.id,
            buttons = buttonsList,
            imageUrl = imageUrl,
            objectTitle = objectTitle,
        )
        
        val dialogueMessage = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = json.encodeToString(dialogueData)
        )
        
        // Send the dialogue message to the client
        WebSocketDebug.logMessage("DIALOGUE_SHOW_SEND", "dialogueId=$dialogueId nodeId=${node.id}", context.session)
        context.connection.session.send(json.encodeToString(dialogueMessage))
        
        // Await the confirmation result via the deferred
        val buttonIndex = try {
            WebSocketDebug.logMessage("DIALOGUE_AWAIT_START", "dialogueId=$dialogueId", context.session)
            withTimeoutOrNull(CONFIRMATION_TIMEOUT.seconds) {
                deferred.await()
            }
        } finally {
            // Clean up regardless of outcome
            pendingConfirmations.remove(dialogueId)
            if (!deferred.isCompleted) deferred.cancel()
        }

        // If confirmed within timeout, propagate button index; otherwise fail
        return if (buttonIndex != null && buttonIndex >= 0) {
            WebSocketDebug.logMessage("DIALOGUE_CONFIRMED", "dialogueId=$dialogueId buttonIndex=$buttonIndex", context.session)
            eventData["buttonIndex"] = buttonIndex
            true
        } else {
            WebSocketDebug.logMessage("DIALOGUE_TIMEOUT_OR_INVALID", "dialogueId=$dialogueId", context.session)
            false
        }
    }
}
