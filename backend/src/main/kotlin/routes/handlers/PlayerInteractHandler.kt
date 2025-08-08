package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import logic.NodeLogicRunner
import logic.NodeRunnerContext
import models.LogicNode
import models.PlayerInteractData
import repository.ObjectRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for USER_INTERACT WebSocket messages
 *
 * This handler processes player interaction messages and runs the node logic
 * for the interacted object.
 */
class PlayerInteractHandler(
    private val objectRepository: ObjectRepository
) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PLAYER_INTERACT_HANDLER", "Starting to process USER_INTERACT action", session)
        WebSocketDebug.logMessage("PLAYER_INTERACT_DETAILS", "Connection user: ${connection.userId}, World: $worldId", session)
        
        try {
            // Parse the player interaction data
            WebSocketDebug.logMessage("PLAYER_INTERACT_PARSING", "Parsing interaction data from message", session)
            val interactData = json.decodeFromString<PlayerInteractData>(message.data)
            WebSocketDebug.logMessage("PLAYER_INTERACT_PARSED", "Interaction data: from=${connection.userId}, for=${interactData.userId}, objectId=${interactData.objectId}, isPlayer=${interactData.isPlayer}, world=$worldId", session)

            // Get the object from the repository
            WebSocketDebug.logMessage("OBJECT_RETRIEVAL", "Retrieving object with ID: ${interactData.objectId}", session)
            val objectModel = objectRepository.getById(interactData.objectId)
            
            if (objectModel != null) {
                WebSocketDebug.logMessage("OBJECT_FOUND", "Found object: title=${objectModel.title}, worldId=${objectModel.worldId}", session)
                
                // Create a NodeRunnerContext
                WebSocketDebug.logMessage("CONTEXT_CREATION", "Creating NodeRunnerContext for processing", session)
                val context = NodeRunnerContext(
                    connection = connection,
                    worldId = worldId,
                    objectId = interactData.objectId, // Include the objectId
                    activeConnections = activeConnections,
                    session = session
                )
                
                // Create a NodeLogicRunner for this object
                WebSocketDebug.logMessage("LOGIC_RUNNER_CREATION", "Creating NodeLogicRunner for object: ${objectModel.title}", session)
                val nodeLogicRunner = NodeLogicRunner(
                    objectData = objectModel,
                    context = context
                )
                
                // Prepare event data
                WebSocketDebug.logMessage("EVENT_DATA_PREPARATION", "Preparing event data for node processing", session)
                val eventData = mapOf(
                    "userId" to interactData.userId,
                    "objectId" to interactData.objectId,
                    "isPlayer" to interactData.isPlayer
                )
                WebSocketDebug.logMessage("EVENT_DATA_PREPARED", "Event data prepared: $eventData", session)
                
                // Process the appropriate starting node based on whether the user is a player or visitor
                if (interactData.isPlayer) {
                    WebSocketDebug.logMessage("PROCESSING_PLAYER_INTERACT", "Processing as player interaction", session)
                    nodeLogicRunner.processNodes(LogicNode.PlayerInteract(), eventData)
                } else {
                    WebSocketDebug.logMessage("PROCESSING_VISITOR_INTERACT", "Processing as visitor interaction", session)
                    nodeLogicRunner.processNodes(LogicNode.VisitorInteract(), eventData)
                }
                
                WebSocketDebug.logMessage("NODE_PROCESSING_COMPLETE", "Completed node processing for object: ${objectModel.title}", session)
            } else {
                WebSocketDebug.logError("Object not found with ID: ${interactData.objectId}", null, session)
            }
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing USER_INTERACT message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
