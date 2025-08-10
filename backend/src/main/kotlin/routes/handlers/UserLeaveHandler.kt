package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.UserLeaveData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import state.WorldStateStore
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for USER_LEAVE WebSocket messages
 *
 * This handler processes user leave messages and broadcasts them to all clients in the same world.
 */
class UserLeaveHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Parse user leave data
            val leaveData = json.decodeFromString<UserLeaveData>(message.data)
            WebSocketDebug.logMessage("USER_LEAVE", "from: ${connection.userId}, for: ${leaveData.userId}, world: ${leaveData.worldId}", session)
            
            // Ensure the worldId in the leave data matches the connection's worldId
            val correctedLeaveData = if (leaveData.worldId != worldId) {
                WebSocketDebug.logMessage("WORLD_MISMATCH", "message: ${leaveData.worldId}, connection: $worldId", session)
                leaveData.copy(worldId = worldId)
            } else {
                leaveData
            }
            
            // Update authoritative world state
            WorldStateStore.removePlayer(worldId, correctedLeaveData.userId)

            // Broadcast leave notification to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.USER_LEAVE,
                data = json.encodeToString(UserLeaveData.serializer(), correctedLeaveData)
            )
            
            WebSocketDebug.logMessage("BROADCASTING_LEAVE", "user: ${correctedLeaveData.userId}, world: $worldId", session)
            WebSocketUtils.broadcastToWorld(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing USER_LEAVE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
