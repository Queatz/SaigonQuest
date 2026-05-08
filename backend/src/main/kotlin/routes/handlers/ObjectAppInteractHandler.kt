package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import logic.NodeLogicRunner
import logic.NodeRunnerContext
import kotlinx.coroutines.launch
import models.LogicNode
import models.ObjectAppInteractData
import repository.ObjectRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for OBJECT_APP_INTERACT WebSocket messages
 */
class ObjectAppInteractHandler(
    private val objectRepository: ObjectRepository
) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("OBJECT_APP_INTERACT_HANDLER", "Starting to process OBJECT_APP_INTERACT action", session)
        
        try {
            val interactData = json.decodeFromString<ObjectAppInteractData>(message.data)
            val objectModel = objectRepository.getById(interactData.objectId)
            
            if (objectModel != null) {
                val context = NodeRunnerContext(
                    connection = connection,
                    worldId = worldId,
                    objectId = interactData.objectId,
                    activeConnections = activeConnections,
                    session = session
                )
                
                val nodeLogicRunner = NodeLogicRunner(
                    objectData = objectModel,
                    context = context
                )
                
                val eventData = mapOf(
                    "userId" to interactData.userId,
                    "objectId" to interactData.objectId,
                    "isPlayer" to interactData.isPlayer
                )
                
                session.launch {
                    try {
                        nodeLogicRunner.processNodes(LogicNode.ObjectAppInteract(), eventData)
                    } catch (e: Throwable) {
                        WebSocketDebug.logError("Error in async ObjectAppInteract processing", e, session)
                    }
                }
            } else {
                WebSocketDebug.logError("Object not found with ID: ${interactData.objectId}", null, session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing OBJECT_APP_INTERACT message", e, session)
        }
    }
}
