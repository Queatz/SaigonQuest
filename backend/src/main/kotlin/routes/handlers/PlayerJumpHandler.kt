package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import models.PlayerJumpData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for PLAYER_JUMP WebSocket messages
 *
 * This handler processes player jump events and broadcasts them to all clients in the same world.
 */
class PlayerJumpHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Parse player jump data
            val jumpData = json.decodeFromString<PlayerJumpData>(message.data)
            WebSocketDebug.logMessage("PLAYER_JUMP", "from: ${connection.userId}, for: ${jumpData.userId}, world: $worldId", session)
            
            // Broadcast jump event to all other clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.PLAYER_JUMP,
                data = message.data
            )
            
            WebSocketDebug.logMessage("BROADCASTING_JUMP", "user: ${jumpData.userId}, world: $worldId (excluding sender ${connection.userId})", session)
            WebSocketUtils.broadcastToWorldExcept(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), connection.userId, activeConnections, session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing PLAYER_JUMP message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
