package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import routes.WebSocketConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility functions for WebSocket handlers
 */
object WebSocketUtils {
    /**
     * Broadcast a message to all clients in a world
     *
     * @param worldId The ID of the world to broadcast to
     * @param message The message to broadcast
     * @param activeConnections Map of active connections by world ID
     * @param session The WebSocket session (for logging purposes)
     */
    suspend fun broadcastToWorld(
        worldId: String,
        message: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        val connections = activeConnections[worldId]
        if (connections.isNullOrEmpty()) {
            WebSocketDebug.logBroadcast("NO_CONNECTIONS", "No active connections", worldId)
            return
        }
        
        WebSocketDebug.logBroadcast("BROADCASTING", "To ${connections.size} clients", worldId)
        WebSocketDebug.logBroadcast("MESSAGE_CONTENT", message, worldId)
        var successCount = 0
        
        connections.forEach { connection ->
            try {
                WebSocketDebug.logBroadcast("SENDING_TO", "User: ${connection.userId}", worldId)
                connection.session.send(message)
                successCount++
                WebSocketDebug.logBroadcast("SENT_SUCCESS", "To user: ${connection.userId}", worldId)
            } catch (e: Exception) {
                // Log error but continue with other connections
                WebSocketDebug.logError("Error sending message to client ${connection.userId}", e, session)
            }
        }
        
        WebSocketDebug.logBroadcast("BROADCAST_COMPLETE", "Success: $successCount/${connections.size} clients", worldId)
    }
}
