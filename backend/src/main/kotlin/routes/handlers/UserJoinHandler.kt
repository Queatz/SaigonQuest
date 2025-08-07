package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.UserJoinData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for USER_JOIN WebSocket messages
 *
 * This handler processes user join messages and broadcasts them to all clients in the same world.
 * It also handles heartbeat ping messages.
 */
class UserJoinHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        // Check if this is a heartbeat ping message
        if (message.data.contains("\"ping\":")) {
            // This is a heartbeat ping, just log it and don't broadcast
            WebSocketDebug.logMessage("HEARTBEAT_PING", "from: ${connection.userId}", session)
            return
        }
        
        try {
            // Parse user join data
            val joinData = json.decodeFromString<UserJoinData>(message.data)
            WebSocketDebug.logMessage("USER_JOIN", "from: ${connection.userId}, for: ${joinData.userId}, world: ${joinData.worldId}", session)
            
            // Ensure the worldId in the join data matches the connection's worldId
            val correctedJoinData = if (joinData.worldId != worldId) {
                WebSocketDebug.logMessage("WORLD_MISMATCH", "message: ${joinData.worldId}, connection: $worldId", session)
                joinData.copy(worldId = worldId)
            } else {
                joinData
            }
            
            // Broadcast join notification to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.USER_JOIN,
                data = json.encodeToString(UserJoinData.serializer(), correctedJoinData)
            )
            
            WebSocketDebug.logMessage("BROADCASTING_JOIN", "user: ${correctedJoinData.userId}, world: $worldId", session)
            WebSocketUtils.broadcastToWorld(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing USER_JOIN message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
