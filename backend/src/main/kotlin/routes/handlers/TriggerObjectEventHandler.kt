package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.TriggerEventData
import models.WebSocketAction
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for TRIGGER_OBJECT_EVENT WebSocket messages
 *
 * This handler processes object event trigger messages and broadcasts them to all clients in the same world.
 * When an object event is triggered, this handler ensures all other clients are notified.
 */
class TriggerObjectEventHandler : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "TRIGGER_OBJECT_EVENT action", session)
        
        try {
            // Parse the trigger event data
            val triggerData = json.decodeFromString<TriggerEventData>(message.data)
            WebSocketDebug.logMessage("TRIGGER_OBJECT_EVENT", "from: ${connection.userId}, objectId: ${triggerData.objectId}, eventType: ${triggerData.eventType}, world: $worldId", session)
            
            // Broadcast trigger event to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.TRIGGER_OBJECT_EVENT,
                data = message.data
            )
            
            val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
            WebSocketDebug.logMessage("BROADCASTING_TRIGGER_EVENT", "objectId: ${triggerData.objectId}, eventType: ${triggerData.eventType}, world: $worldId", session)
            
            WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing TRIGGER_OBJECT_EVENT message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
