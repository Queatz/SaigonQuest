package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import models.PlayerPositionData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import state.WorldStateStore
import repository.UserWorldPositionRepository
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for UPDATE_PLAYER_POSITION WebSocket messages
 *
 * This handler processes player position updates and broadcasts them to all clients in the same world.
 * When a player moves, this handler ensures all other clients see the movement.
 */
class UpdatePlayerPositionHandler : WebSocketHandler {
    private val userWorldPositionRepository = UserWorldPositionRepository()
    private val lastDbUpdate = ConcurrentHashMap<String, Long>() // userId -> timestamp
    private val DB_UPDATE_THROTTLE_MS = 5000L // 5 seconds

    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Parse player position data
            val positionData = json.decodeFromString<PlayerPositionData>(message.data)
            WebSocketDebug.logMessage("POSITION_UPDATE", "from: ${connection.userId}, for: ${positionData.userId}, world: $worldId", session)
            
            // Update authoritative world state
            WorldStateStore.updatePlayerPosition(
                worldId = worldId,
                userId = positionData.userId,
                position = positionData.position,
                isPlayer = positionData.isPlayer,
            )

            // Persist position to DB with throttling
            val now = System.currentTimeMillis()
            val userId = connection.userId
            val lastUpdate = lastDbUpdate[userId] ?: 0L
            if (now - lastUpdate > DB_UPDATE_THROTTLE_MS) {
                lastDbUpdate[userId] = now
                try {
                    userWorldPositionRepository.update(userId, worldId, positionData.position)
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Failed to persist user position to DB", e, session)
                }
            }

            // Broadcast position update to all other clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.UPDATE_PLAYER_POSITION,
                data = message.data
            )
            
            WebSocketDebug.logMessage("BROADCASTING_POSITION", "user: ${positionData.userId}, world: $worldId (excluding sender ${connection.userId})", session)
            WebSocketUtils.broadcastToWorldExcept(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), connection.userId, activeConnections, session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing UPDATE_PLAYER_POSITION message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
