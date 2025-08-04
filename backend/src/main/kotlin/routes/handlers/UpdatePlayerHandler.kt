package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.UpdatePlayerData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for UPDATE_PLAYER WebSocket messages
 *
 * This handler processes player updates and broadcasts them to all clients in the same world.
 * When a visitor becomes a player or player details are updated, this handler ensures all other clients
 * receive the updated information.
 */
class UpdatePlayerHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        json: Json,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Parse update player data
            val updateData = json.decodeFromString<UpdatePlayerData>(message.data)
            WebSocketDebug.logMessage("PLAYER_UPDATE", "from: ${connection.userId}, for: ${updateData.userId}, world: $worldId", session)
            
            // Broadcast player update to all other clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.UPDATE_PLAYER,
                data = message.data
            )
            
            WebSocketDebug.logMessage("BROADCASTING_PLAYER_UPDATE", "user: ${updateData.userId}, name: ${updateData.name}, isPlayer: ${updateData.isPlayer}, world: $worldId", session)
            WebSocketUtils.broadcastToWorld(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing UPDATE_PLAYER message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
