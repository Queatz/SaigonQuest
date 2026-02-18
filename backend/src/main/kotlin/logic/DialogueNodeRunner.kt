package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        // Map to track pending dialogue confirmations with a channel for multiple events
        private val pendingConfirmations = ConcurrentHashMap<String, Channel<DialogueConfirmation>>()
        
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
            val channel = pendingConfirmations[dialogueId]
            if (channel == null) {
                logger.debug("Dialogue processConfirmation: no pending for id={}", dialogueId)
                return false
            }
            val result = channel.trySend(confirmation)
            val ok = result.isSuccess
            logger.debug("Dialogue processConfirmation: sent to channel id={} ok={} index={} word={}", dialogueId, ok, confirmation.buttonIndex, confirmation.clickedWord)
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
        
        fun inKey(name: String) = "${node.id}_in_${name}"
        fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
            val eventVal = eventData[inKey(nameLower)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }
        }

        // Extract text content (allow override from connected input)
        val text = fromEventOrNode("text_content", PropertyKey.TEXT_CONTENT) ?: ""
        
        // Optional title override
        val title = fromEventOrNode("title", PropertyKey.TITLE)
        
        // Extract optional image URL
        val imageUrl = fromEventOrNode("image_url", PropertyKey.IMAGE_URL)

        // Extract buttons (default to "OK" if none specified), allow override from input
        val buttonsValue = fromEventOrNode("buttons", PropertyKey.BUTTONS) ?: "OK"
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
        
        // Register the dialogue ID in the pending confirmations map with a channel
        val channel = Channel<DialogueConfirmation>(Channel.UNLIMITED)
        logger.debug("Dialogue register: id={}", dialogueId)
        pendingConfirmations[dialogueId] = channel
        
        // Populate the entire text content output
        eventData["${node.id}_out_text"] = text
        eventData["${node.id}_text"] = text
        
        // Create a dialogue data object with buttons
        val dialogueData = DialogueData(
            dialogueId = dialogueId,
            userId = context.connection.userId,
            text = text,
            nodeId = node.id,
            buttons = buttonsList,
            imageUrl = imageUrl,
            objectTitle = objectTitle,
            title = title,  // Optional custom title
        )
        
        val dialogueMessage = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = json.encodeToString(dialogueData)
        )
        
        // Send the dialogue message to the client
        WebSocketDebug.logMessage("DIALOGUE_SHOW_SEND", "dialogueId=$dialogueId nodeId=${node.id}", context.session)
        context.connection.session.send(json.encodeToString(dialogueMessage))
        
        // Await confirmation results via the channel
        try {
            WebSocketDebug.logMessage("DIALOGUE_AWAIT_START", "dialogueId=$dialogueId", context.session)
            while (true) {
                val confirmationResult = withTimeoutOrNull(CONFIRMATION_TIMEOUT.seconds) {
                    channel.receive()
                } ?: break // Timeout
                
                val clickedWord = confirmationResult.clickedWord
                if (!clickedWord.isNullOrBlank()) {
                    WebSocketDebug.logMessage("DIALOGUE_WORD_CLICKED", "dialogueId=$dialogueId word=$clickedWord", context.session)
                    
                    val branchData = eventData.toMutableMap()
                    branchData["clickedWord"] = clickedWord
                    branchData["${node.id}_out_clicked_word"] = clickedWord
                    branchData["isWordClick"] = true
                    
                    // Trigger branch ASYNCHRONOUSLY using coroutineScope but not awaiting it
                    // To truly not await it, we should use the context's session scope
                    context.session.launch {
                        context.logicRunner?.triggerBranch(node.id, "${node.id}_out_word_clicked", branchData)
                    }
                    // Continue loop to wait for more word clicks or a button click
                } else {
                    WebSocketDebug.logMessage("DIALOGUE_CONFIRMED", "dialogueId=$dialogueId buttonIndex=${confirmationResult.buttonIndex}", context.session)
                    eventData["buttonIndex"] = confirmationResult.buttonIndex
                    eventData["isWordClick"] = false
                    return true // Button clicked, finish this node
                }
            }
            WebSocketDebug.logMessage("DIALOGUE_TIMEOUT", "dialogueId=$dialogueId", context.session)
            return false
        } finally {
            // Clean up regardless of outcome
            pendingConfirmations.remove(dialogueId)
            channel.close()
        }
    }
}
