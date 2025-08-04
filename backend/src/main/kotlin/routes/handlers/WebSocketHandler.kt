package routes.handlers

import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import routes.WebSocketConnection
import routes.WebSocketMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for WebSocket message handlers
 *
 * This interface defines the contract for all WebSocket message handlers.
 * Each handler is responsible for processing a specific type of WebSocket message
 * and broadcasting updates to clients as needed.
 */
interface WebSocketHandler {
    /**
     * Handle a WebSocket message
     *
     * @param message The WebSocket message to handle
     * @param connection The WebSocket connection that sent the message
     * @param worldId The ID of the world the connection is in
     * @param activeConnections Map of active connections by world ID
     * @param json JSON serializer/deserializer
     * @param session The WebSocket session
     */
    suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        json: Json,
        session: DefaultWebSocketServerSession
    )
}
