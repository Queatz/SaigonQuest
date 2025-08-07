package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.Object
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for OBJECT_UPDATE WebSocket messages
 *
 * This handler processes object update messages and broadcasts them to all clients in the same world.
 * Object updates include position, rotation, and scale changes.
 */
class ObjectUpdateHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "OBJECT_UPDATE action", session)
        
        try {
            // Parse the object data
            val obj = json.decodeFromString<Object>(message.data)
            WebSocketDebug.logMessage("OBJECT_UPDATE", "from: ${connection.userId}, objectId: ${obj.key}, world: $worldId", session)
            
            // Broadcast object update to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.OBJECT_UPDATE,
                data = message.data
            )
            
            val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketDebug.logMessage("BROADCASTING_OBJECT_UPDATE", "objectId: ${obj.key}, world: $worldId", session)
            
            WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing OBJECT_UPDATE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
