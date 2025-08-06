package logic

import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import routes.WebSocketConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Context data for node runners
 *
 * This class holds common parameters needed by node runners, making it easier
 * to pass them around and reducing parameter lists.
 */
data class NodeRunnerContext(
    val connection: WebSocketConnection,
    val worldId: String,
    val activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
    val json: Json,
    val session: DefaultWebSocketServerSession
)
