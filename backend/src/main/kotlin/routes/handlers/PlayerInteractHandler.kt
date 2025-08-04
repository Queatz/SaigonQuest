package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.PlayerInteractData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for PLAYER_INTERACT WebSocket messages
 *
 * This handler processes player interaction messages and broadcasts them to all clients in the same world.
 * When a player interacts with an object, this handler ensures all other clients are notified.
 */
class PlayerInteractHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        json: Json,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "PLAYER_INTERACT action", session)
        
        try {
            // Parse the player interaction data
            val interactData = json.decodeFromString<PlayerInteractData>(message.data)
            WebSocketDebug.logMessage("PLAYER_INTERACT", "from: ${connection.userId}, for: ${interactData.userId}, objectId: ${interactData.objectId}, world: $worldId", session)
            
            // Broadcast player interaction to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.USER_INTERACT,
                data = message.data
            )
            
            val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketDebug.logMessage("BROADCASTING_PLAYER_INTERACT", "user: ${interactData.userId}, objectId: ${interactData.objectId}, world: $worldId", session)
            
            WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing PLAYER_INTERACT message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
