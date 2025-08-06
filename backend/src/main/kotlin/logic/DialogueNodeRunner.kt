package logic

import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import models.DialogueConfirmation
import models.DialogueData
import models.WebSocketAction
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage
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
        // Map to track pending dialogue confirmations
        private val pendingConfirmations = ConcurrentHashMap<String, Boolean>()
        
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
                pendingConfirmations[dialogueId] = true
                return true
            }
            return false
        }
    }
    
    override suspend fun execute(node: NodeData, eventData: Map<String, Any>): Boolean {
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        
        // Parse the JSON data to extract the text
        val text = try {
            // Try to parse as PropertyBasedNodeData
            val nodeData = context.json.decodeFromString<PropertyBasedNodeData>(jsonData)
            // Find the property with key TEXT_CONTENT
            nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value
        } catch (e: Exception) {
            null
        } ?: return false
        
        // Generate a unique dialogue ID
        val dialogueId = UUID.randomUUID().toString()
        
        // Register the dialogue ID in the pending confirmations map
        pendingConfirmations[dialogueId] = false
        
        // Create a dialogue data object
        val dialogueData = DialogueData(
            dialogueId = dialogueId,
            userId = context.connection.userId,
            text = text,
            nodeId = node.id
        )
        
        val dialogueMessage = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = context.json.encodeToString(dialogueData)
        )
        
        // Send the dialogue message to the client
        context.connection.session.send(context.json.encodeToString(dialogueMessage))
        
        // Set up a listener for the confirmation
        val confirmed = withTimeoutOrNull(CONFIRMATION_TIMEOUT.seconds) {
            // Wait until the confirmation is received or timeout
            while (!pendingConfirmations.getOrDefault(dialogueId, false)) {
                // Check for incoming messages
                val frame = context.connection.session.incoming.receive()
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val message = context.json.decodeFromString<WebSocketMessage>(text)
                        if (message.action == WebSocketAction.DIALOGUE_CONFIRM) {
                            val confirmation = context.json.decodeFromString<DialogueConfirmation>(message.data)
                            if (confirmation.dialogueId == dialogueId) {
                                pendingConfirmations[dialogueId] = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors
                    }
                }
            }
            true
        } ?: false
        
        // Clean up
        pendingConfirmations.remove(dialogueId)
        
        return confirmed
    }
}
