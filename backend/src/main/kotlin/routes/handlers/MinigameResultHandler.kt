package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import logic.MinigameNodeRunner
import models.MinigameResult
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for minigame result messages
 */
class MinigameResultHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            WebSocketDebug.logMessage("MINIGAME_RESULT_RECEIVED", "Processing minigame result", session)
            
            val result = json.decodeFromString<MinigameResult>(message.data).copy(userId = connection.userId)
            
            val processed = MinigameNodeRunner.processResult(result)
            
            if (processed) {
                WebSocketDebug.logMessage("MINIGAME_RESULT_PROCESSED", "minigameId=${result.minigameId}", session)
            } else {
                WebSocketDebug.logMessage("MINIGAME_RESULT_IGNORED", "minigameId=${result.minigameId}", session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing minigame result", e, session)
        }
    }
}
