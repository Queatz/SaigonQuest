package logic

import debug.WebSocketDebug
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.withTimeoutOrNull
import models.DialogueConfirmation
import models.DialogueData
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
import utils.json
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
        // Map to track pending dialogue confirmations with button index
        private val pendingConfirmations = ConcurrentHashMap<String, Pair<Boolean, Int>>()
        
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
            if (pendingConfirmations.containsKey(dialogueId)) {
                pendingConfirmations[dialogueId] = Pair(true, confirmation.buttonIndex)
                return true
            }
            return false
        }
    }
    
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        
        // Parse the JSON data to extract the text and buttons
        val nodeData = try {
            // Try to parse as PropertyBasedNodeData
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Exception) {
            WebSocketDebug.logError("Failed to parse dialogue node data", e, context.session)
            return false
        }
        
        // Extract text content
        val text = nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value ?: return false
        
        // Extract buttons (default to "OK" if none specified)
        val buttonsValue = nodeData.properties.find { it.key == PropertyKey.BUTTONS }?.value ?: "OK"
        val buttons = buttonsValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val buttonsList = buttons.ifEmpty { listOf("OK") }
        
        // Generate a unique dialogue ID
        val dialogueId = UUID.randomUUID().toString()
        
        // Register the dialogue ID in the pending confirmations map with default button index -1
        pendingConfirmations[dialogueId] = Pair(false, -1)
        
        // Create a dialogue data object with buttons
        val dialogueData = DialogueData(
            dialogueId = dialogueId,
            userId = context.connection.userId,
            text = text,
            nodeId = node.id,
            buttons = buttonsList
        )
        
        val dialogueMessage = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = json.encodeToString(dialogueData)
        )
        
        // Send the dialogue message to the client
        context.connection.session.send(json.encodeToString(dialogueMessage))
        
        // Set up a listener for the confirmation
        val confirmationResult = withTimeoutOrNull(CONFIRMATION_TIMEOUT.seconds) {
            // Wait until the confirmation is received or timeout
            while (!pendingConfirmations.getOrDefault(dialogueId, Pair(false, -1)).first) {
                // Check for incoming messages
                val frame = context.connection.session.incoming.receive()
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val message = json.decodeFromString<WebSocketMessage>(text)
                        if (message.action == WebSocketAction.DIALOGUE_CONFIRM) {
                            val confirmation = json.decodeFromString<DialogueConfirmation>(message.data)
                            if (confirmation.dialogueId == dialogueId) {
                                pendingConfirmations[dialogueId] = Pair(true, confirmation.buttonIndex)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors
                    }
                }
            }
            pendingConfirmations[dialogueId]
        } ?: Pair(false, -1)
        
        // Get the button index that was confirmed
        val buttonIndex = confirmationResult.second
        
        // Clean up
        pendingConfirmations.remove(dialogueId)
        
        // Return the button index as part of the event data for the next node
        if (confirmationResult.first && buttonIndex >= 0) {
            // Add the button index to the event data
            eventData["buttonIndex"] = buttonIndex
            
            // The NodeLogicRunner will use this buttonIndex to determine which connection to follow
            return true
        }
        
        return false
    }
}
