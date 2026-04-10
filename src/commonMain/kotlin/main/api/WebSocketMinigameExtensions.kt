package main.api

import models.MinigameResult
import models.WebSocketAction

/**
 * Extension functions for WebSocketApi related to minigame operations
 */

/**
 * Send a minigame result back to the server
 * 
 * @param result The minigame result data
 * @return true if the message was sent successfully, false otherwise
 */
fun WebSocketApi.sendMinigameResult(result: MinigameResult): Boolean {
    val message = WebSocketMessage.fromEnum(
        WebSocketAction.MINIGAME_RESULT,
        ApiClient.json.encodeToString(MinigameResult.serializer(), result)
    )
    return sendMessage(message)
}
