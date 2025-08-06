package logic

import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import models.WebSocketAction
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import routes.WebSocketMessage

/**
 * Runner for dialogue nodes
 *
 * This node runner handles displaying text messages to players.
 * It sends a SHOW_DIALOGUE message to the client to display the text.
 */
class DialogueNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
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
        
        // Create a dialogue message
        val dialogueData = mapOf(
            "userId" to context.connection.userId,
            "text" to text
        )
        
        val dialogueMessage = WebSocketMessage(
            action = WebSocketAction.SHOW_DIALOGUE,
            data = context.json.encodeToString(dialogueData)
        )
        
        // Send the dialogue message to the client
        context.connection.session.send(context.json.encodeToString(dialogueMessage))
        
        return true
    }
}
