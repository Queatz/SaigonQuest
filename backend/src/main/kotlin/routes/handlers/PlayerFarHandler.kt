package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import logic.NodeLogicRunner
import logic.NodeRunnerContext
import kotlinx.coroutines.launch
import models.LogicNode
import models.PlayerNearbyData
import repository.ObjectRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for USER_FAR WebSocket messages
 *
 * This handler processes proximity-exit messages and runs the node logic
 * for the associated object, starting from PlayerFar or VisitorFar.
 */
class PlayerFarHandler(
    private val objectRepository: ObjectRepository
) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>, 
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PLAYER_FAR_HANDLER", "Processing USER_FAR action", session)
        WebSocketDebug.logMessage("PLAYER_FAR_DETAILS", "Connection user: ${connection.userId}, World: $worldId", session)

        try {
            // Parse the nearby data (reused for far as well)
            val farData = json.decodeFromString<PlayerNearbyData>(message.data)
            WebSocketDebug.logMessage(
                "PLAYER_FAR_PARSED",
                "Far data: from=${connection.userId}, for=${farData.userId}, objectId=${farData.objectId}, isPlayer=${farData.isPlayer}, world=$worldId",
                session
            )

            // Get the object model
            val objectModel = objectRepository.getById(farData.objectId)
            if (objectModel == null) {
                WebSocketDebug.logError("Object not found with ID: ${farData.objectId}", null, session)
                return
            }
            WebSocketDebug.logMessage("OBJECT_FOUND", "Found object: title=${objectModel.title}, worldId=${objectModel.worldId}", session)

            // Build execution context
            val context = NodeRunnerContext(
                connection = connection,
                worldId = worldId,
                objectId = farData.objectId,
                activeConnections = activeConnections,
                session = session,
                isNight = farData.isNight,
            )

            val runner = NodeLogicRunner(
                objectData = objectModel,
                context = context,
            )

            val eventData = mapOf(
                "userId" to farData.userId,
                "objectId" to farData.objectId,
                "isPlayer" to farData.isPlayer,
            )

            // Run starting at the correct event node asynchronously
            session.launch {
                try {
                    if (farData.isPlayer) {
                        WebSocketDebug.logMessage("PROCESSING_PLAYER_FAR", "Processing as player far (async)", session)
                        runner.processNodes(LogicNode.PlayerFar(), eventData)
                    } else {
                        WebSocketDebug.logMessage("PROCESSING_VISITOR_FAR", "Processing as visitor far (async)", session)
                        runner.processNodes(LogicNode.VisitorFar(), eventData)
                    }
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Error in async PlayerFar processing", e, session)
                } finally {
                    WebSocketDebug.logMessage("NODE_PROCESSING_COMPLETE", "Completed node processing for object: ${objectModel.title}", session)
                }
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing USER_FAR message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
