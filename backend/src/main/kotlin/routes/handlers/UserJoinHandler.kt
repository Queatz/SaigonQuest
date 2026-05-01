package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.launch
import models.LogicNode
import models.UserJoinData
import models.WebSocketAction
import repository.ObjectRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import state.WorldStateStore
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for USER_JOIN WebSocket messages
 *
 * This handler processes user join messages and broadcasts them to all clients in the same world.
 * It also triggers PlayerJoined/VisitorJoined logic nodes on objects in the world.
 */
class UserJoinHandler(
    private val objectRepository: ObjectRepository
) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        // Check if this is a heartbeat ping message
        if (message.data.contains("\"ping\":")) {
            // This is a heartbeat ping, just log it and don't broadcast
            WebSocketDebug.logMessage("HEARTBEAT_PING", "from: ${connection.userId}", session)
            return
        }
        
        try {
            // Parse user join data
            val joinData = json.decodeFromString<UserJoinData>(message.data)
            WebSocketDebug.logMessage("USER_JOIN", "from: ${connection.userId}, for: ${joinData.userId}, world: ${joinData.worldId}", session)
            
            // Ensure the worldId in the join data matches the connection's worldId
            val correctedJoinData = if (joinData.worldId != worldId) {
                WebSocketDebug.logMessage("WORLD_MISMATCH", "message: ${joinData.worldId}, connection: $worldId", session)
                joinData.copy(worldId = worldId)
            } else {
                joinData
            }
            
            // Update authoritative world state
            WorldStateStore.upsertPlayer(
                worldId = worldId,
                userId = correctedJoinData.userId,
                username = correctedJoinData.username,
                isPlayer = correctedJoinData.isPlayer,
                position = correctedJoinData.position,
            )

            // Broadcast join notification to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.USER_JOIN,
                data = json.encodeToString(UserJoinData.serializer(), correctedJoinData)
            )
            
            WebSocketDebug.logMessage("BROADCASTING_JOIN", "user: ${correctedJoinData.userId}, world: $worldId", session)
            WebSocketUtils.broadcastToWorld(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), activeConnections, session)

            // Trigger logic nodes for joins
            val objects = objectRepository.getByWorldId(worldId)
            val baseEventData = mapOf(
                "userId" to correctedJoinData.userId,
                "username" to correctedJoinData.username,
                "isPlayer" to correctedJoinData.isPlayer,
                "position" to correctedJoinData.position,
                "worldId" to worldId,
            )

            if (correctedJoinData.isPlayer) {
                WebSocketDebug.logMessage("PLAYER_JOIN_EVENT", "Triggering PlayerJoined logic for world: $worldId", session)
            } else {
                WebSocketDebug.logMessage("VISITOR_JOIN_EVENT", "Triggering VisitorJoined logic for world: $worldId", session)
            }

            for (obj in objects) {
                val context = logic.NodeRunnerContext(
                    connection = connection,
                    worldId = worldId,
                    objectId = obj.key,
                    activeConnections = activeConnections,
                    session = session,
                    isNight = correctedJoinData.isNight,
                )

                val runner = logic.NodeLogicRunner(
                    objectData = obj,
                    context = context,
                )

                session.launch {
                    try {
                        if (correctedJoinData.isPlayer) {
                            runner.processNodes(LogicNode.PlayerJoined(), baseEventData)
                        } else {
                            runner.processNodes(LogicNode.VisitorJoined(), baseEventData)
                        }
                    } catch (e: Throwable) {
                        debug.WebSocketDebug.logError("Error in async join processing", e, session)
                    }
                }
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing USER_JOIN message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
