package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.Object
import models.WebSocketAction
import repository.ObjectRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for OBJECT_UPDATE WebSocket messages
 *
 * This handler processes object update messages, persists them to the database,
 * and broadcasts them to all other clients in the same world.
 */
class ObjectUpdateHandler(private val objectRepository: ObjectRepository) : WebSocketHandler {
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
            
            // Persist the update to the database
            objectRepository.update(obj.key, obj)
            
            // Broadcast object update to all other clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.OBJECT_UPDATE,
                data = message.data
            )
            
            val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketDebug.logMessage("BROADCASTING_OBJECT_UPDATE", "objectId: ${obj.key}, world: $worldId (excluding sender)", session)
            
            WebSocketUtils.broadcastToWorldExcept(worldId, broadcastMessageString, connection.userId, activeConnections, session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing OBJECT_UPDATE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
