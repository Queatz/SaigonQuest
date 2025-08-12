package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.PlayerTypingData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import state.WorldStateStore
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for PLAYER_TYPING WebSocket messages
 *
 * This handler processes player typing status updates and broadcasts them to all clients in the same world.
 * When a player starts or stops typing, this handler ensures all other clients are notified.
 */
class PlayerTypingHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Parse player typing data
            val typingData = json.decodeFromString<PlayerTypingData>(message.data)
            WebSocketDebug.logMessage("TYPING_STATUS", "from: ${connection.userId}, for: ${typingData.userId}, isTyping: ${typingData.isTyping}, world: $worldId", session)
            
            // Update authoritative world state
            WorldStateStore.updateTyping(
                worldId = worldId,
                userId = typingData.userId,
                isTyping = typingData.isTyping,
            )

            // Broadcast typing status update to all other clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.PLAYER_TYPING,
                data = message.data
            )
            
            WebSocketDebug.logMessage("BROADCASTING_TYPING_STATUS", "user: ${typingData.userId}, isTyping: ${typingData.isTyping}, world: $worldId", session)
            WebSocketUtils.broadcastToWorld(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), activeConnections, session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing PLAYER_TYPING message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
