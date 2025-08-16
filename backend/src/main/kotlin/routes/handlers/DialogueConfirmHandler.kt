package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logic.DialogueNodeRunner
import models.DialogueConfirmation
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for dialogue confirmation messages
 *
 * This handler processes DIALOGUE_CONFIRM messages sent by clients
 * when a user clicks the OK button on a dialogue.
 */
class DialogueConfirmHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Log the message
            WebSocketDebug.logMessage("DIALOGUE_CONFIRM_RECEIVED", "Processing dialogue confirmation", session)
            
            // Parse the dialogue confirmation from the message data
            val confirmation = json.decodeFromString<DialogueConfirmation>(message.data)
            
            // Verify that the user ID matches the connection
            if (confirmation.userId != connection.userId) {
                WebSocketDebug.logError("User ID mismatch in dialogue confirmation", null, session)
                return
            }
            
            // Process the confirmation
            val processed = DialogueNodeRunner.processConfirmation(confirmation)
            
            if (processed) {
                WebSocketDebug.logMessage("DIALOGUE_CONFIRM_PROCESSED", "dialogueId=${confirmation.dialogueId} buttonIndex=${confirmation.buttonIndex}", session)
            } else {
                WebSocketDebug.logMessage("DIALOGUE_CONFIRM_IGNORED", "dialogueId=${confirmation.dialogueId}", session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing dialogue confirmation", e, session)
        }
    }
}
