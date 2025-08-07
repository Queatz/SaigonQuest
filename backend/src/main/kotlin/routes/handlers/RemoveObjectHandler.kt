package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for REMOVE_OBJECT WebSocket messages
 *
 * This handler processes object removal messages and broadcasts them to all clients in the same world.
 * When a client removes an object from the scene, this handler ensures all other clients also remove it.
 */
class RemoveObjectHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "REMOVE_OBJECT action", session)
        
        try {
            // The data is just the object ID as a string
            val objectId = message.data
            WebSocketDebug.logMessage("REMOVE_OBJECT", "from: ${connection.userId}, objectId: $objectId, world: $worldId", session)
            
            // Broadcast object removal to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.REMOVE_OBJECT,
                data = objectId
            )
            
            val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketDebug.logMessage("BROADCASTING_REMOVE_OBJECT", "objectId: $objectId, world: $worldId", session)
            
            WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing REMOVE_OBJECT message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
