package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import logic.TakeItemsNodeRunner
import models.TakeItemsConfirmation
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for take items confirmations from clients.
 */
class TakeItemsConfirmHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            val confirmation = json.decodeFromString<TakeItemsConfirmation>(message.data)
            if (confirmation.userId != connection.userId) {
                WebSocketDebug.logError("TakeItemsConfirm: UserId mismatch", null, session)
                return
            }
            val ok = TakeItemsNodeRunner.processConfirmation(confirmation)
            if (ok) {
                WebSocketDebug.logMessage("TAKE_ITEMS_CONFIRM_PROCESSED", "requestId=${confirmation.requestId} accepted=${confirmation.accepted}", session)
            } else {
                WebSocketDebug.logMessage("TAKE_ITEMS_CONFIRM_IGNORED", "requestId=${confirmation.requestId}", session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing TakeItems confirmation", e, session)
        }
    }
}
