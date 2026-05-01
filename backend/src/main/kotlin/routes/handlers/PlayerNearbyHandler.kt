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
 * Handler for USER_NEARBY WebSocket messages
 *
 * This handler processes proximity-enter messages and runs the node logic
 * for the associated object, starting from PlayerNearby or VisitorNearby.
 */
class PlayerNearbyHandler(
    private val objectRepository: ObjectRepository
) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>, 
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PLAYER_NEARBY_HANDLER", "Processing USER_NEARBY action", session)
        WebSocketDebug.logMessage("PLAYER_NEARBY_DETAILS", "Connection user: ${connection.userId}, World: $worldId", session)

        try {
            // Parse the nearby data
            val nearbyData = json.decodeFromString<PlayerNearbyData>(message.data)
            WebSocketDebug.logMessage(
                "PLAYER_NEARBY_PARSED",
                "Nearby data: from=${connection.userId}, for=${nearbyData.userId}, objectId=${nearbyData.objectId}, isPlayer=${nearbyData.isPlayer}, world=$worldId",
                session
            )

            // Get the object model
            val objectModel = objectRepository.getById(nearbyData.objectId)
            if (objectModel == null) {
                WebSocketDebug.logError("Object not found with ID: ${nearbyData.objectId}", null, session)
                return
            }
            WebSocketDebug.logMessage("OBJECT_FOUND", "Found object: title=${objectModel.title}, worldId=${objectModel.worldId}", session)

            // Build execution context
            val context = NodeRunnerContext(
                connection = connection,
                worldId = worldId,
                objectId = nearbyData.objectId,
                activeConnections = activeConnections,
                session = session,
                isNight = nearbyData.isNight,
            )

            val runner = NodeLogicRunner(
                objectData = objectModel,
                context = context,
            )

            val eventData = mapOf(
                "userId" to nearbyData.userId,
                "objectId" to nearbyData.objectId,
                "isPlayer" to nearbyData.isPlayer,
            )

            // Run starting at the correct event node asynchronously
            session.launch {
                try {
                    if (nearbyData.isPlayer) {
                        WebSocketDebug.logMessage("PROCESSING_PLAYER_NEARBY", "Processing as player nearby (async)", session)
                        runner.processNodes(LogicNode.PlayerNearby(), eventData)
                    } else {
                        WebSocketDebug.logMessage("PROCESSING_VISITOR_NEARBY", "Processing as visitor nearby (async)", session)
                        runner.processNodes(LogicNode.VisitorNearby(), eventData)
                    }
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Error in async PlayerNearby processing", e, session)
                } finally {
                    WebSocketDebug.logMessage("NODE_PROCESSING_COMPLETE", "Completed node processing for object: ${objectModel.title}", session)
                }
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing USER_NEARBY message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
