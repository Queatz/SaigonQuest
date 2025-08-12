package main.api

import models.DialogueConfirmation
import models.WebSocketAction

/**
 * Extension functions for WebSocketApi related to dialogue operations
 */

/**
 * Send a dialogue confirmation message
 * 
 * This is called when the user clicks a button on a dialogue.
 * 
 * @param dialogueId The ID of the dialogue to confirm
 * @param userId The ID of the user confirming the dialogue
 * @param buttonIndex The index of the button that was clicked (0 is the default/first button)
 * @return true if the message was sent, false otherwise
 */
fun WebSocketApi.sendDialogueConfirmation(dialogueId: String, userId: String, buttonIndex: Int = 0): Boolean {
    console.log("WebSocketApi: Sending dialogue confirmation for dialogueId=$dialogueId, buttonIndex=$buttonIndex")
    
    if (!isConnected()) {
        console.error("WebSocketApi: Cannot send dialogue confirmation - WebSocket not connected")
        return false
    }
    
    try {
        val confirmation = DialogueConfirmation(
            dialogueId = dialogueId,
            userId = userId,
            buttonIndex = buttonIndex
        )
        
        val message = WebSocketMessage.fromEnum(
            action = WebSocketAction.DIALOGUE_CONFIRM,
            data = ApiClient.json.encodeToString(confirmation)
        )
        
        val success = sendMessage(message)
        
        if (success) {
            console.log("WebSocketApi: Dialogue confirmation sent successfully")
        } else {
            console.error("WebSocketApi: Failed to send dialogue confirmation")
        }
        
        return success
    } catch (e: Throwable) {
        console.error("WebSocketApi: Error sending dialogue confirmation", e)
        return false
    }
}
