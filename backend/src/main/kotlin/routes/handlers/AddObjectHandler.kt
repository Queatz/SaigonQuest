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
 * Handler for ADD_OBJECT WebSocket messages
 *
 * This handler processes object addition messages and broadcasts them to all clients in the same world.
 * When a client adds a new object to the scene, this handler ensures all other clients also add it.
 */
class AddObjectHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "ADD_OBJECT action", session)
        
        try {
            // Parse the object data
            val obj = json.decodeFromString<Object>(message.data)
            WebSocketDebug.logMessage("ADD_OBJECT", "from: ${connection.userId}, objectId: ${obj.key}, world: $worldId", session)
            
            // Broadcast object addition to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.ADD_OBJECT,
                data = message.data
            )
            
            val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketDebug.logMessage("BROADCASTING_ADD_OBJECT", "objectId: ${obj.key}, world: $worldId", session)
            
            WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing ADD_OBJECT message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
