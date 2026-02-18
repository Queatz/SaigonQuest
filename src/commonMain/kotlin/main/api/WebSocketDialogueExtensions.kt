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
fun WebSocketApi.sendDialogueConfirmation(dialogueId: String, userId: String, buttonIndex: Int = 0, inputText: String? = null, clickedWord: String? = null): Boolean {
    console.log("WebSocketApi: Sending dialogue confirmation for dialogueId=$dialogueId, buttonIndex=$buttonIndex, clickedWord=$clickedWord")
    
    if (!isConnected()) {
        console.error("WebSocketApi: Cannot send dialogue confirmation - WebSocket not connected")
        return false
    }
    
    try {
        val confirmation = DialogueConfirmation(
            dialogueId = dialogueId,
            userId = userId,
            buttonIndex = buttonIndex,
            inputText = inputText,
            clickedWord = clickedWord
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

/**
 * Send a take-items confirmation message
 *
 * @param requestId The prompt request ID
 * @param userId The confirming user ID
 * @param accepted Whether the user accepted the transfer
 */
fun WebSocketApi.sendTakeItemsConfirmation(requestId: String, userId: String, accepted: Boolean): Boolean {
    console.log("WebSocketApi: Sending take-items confirmation for requestId=$requestId accepted=$accepted")
    if (!isConnected()) return false
    return try {
        val confirmation = models.TakeItemsConfirmation(requestId = requestId, userId = userId, accepted = accepted)
        val message = WebSocketMessage.fromEnum(
            action = models.WebSocketAction.TAKE_ITEMS_CONFIRM,
            data = ApiClient.json.encodeToString(confirmation)
        )
        sendMessage(message)
    } catch (e: Throwable) {
        console.error("WebSocketApi: Error sending take-items confirmation", e)
        false
    }
}
