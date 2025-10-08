package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import models.ChatMessage
import models.ChatSenderType
import models.UserLeaveData
import models.WebSocketAction
import repository.ChatRepository
import repository.UserRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import state.WorldStateStore
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for USER_LEAVE WebSocket messages
 *
 * This handler processes user leave messages and broadcasts them to all clients in the same world,
 * and also sends a system chat message announcing the leave.
 */
class UserLeaveHandler(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        try {
            // Parse user leave data
            val leaveData = json.decodeFromString<UserLeaveData>(message.data)
            WebSocketDebug.logMessage("USER_LEAVE", "from: ${connection.userId}, for: ${leaveData.userId}, world: ${leaveData.worldId}", session)
            
            // Ensure the worldId in the leave data matches the connection's worldId
            val correctedLeaveData = if (leaveData.worldId != worldId) {
                WebSocketDebug.logMessage("WORLD_MISMATCH", "message: ${leaveData.worldId}, connection: $worldId", session)
                leaveData.copy(worldId = worldId)
            } else {
                leaveData
            }
            
            // Update authoritative world state
            WorldStateStore.removePlayer(worldId, correctedLeaveData.userId)

            // Broadcast leave notification to all clients in the same world
            val broadcastMessage = WebSocketMessage(
                action = WebSocketAction.USER_LEAVE,
                data = json.encodeToString(UserLeaveData.serializer(), correctedLeaveData)
            )
            
            WebSocketDebug.logMessage("BROADCASTING_LEAVE", "user: ${correctedLeaveData.userId}, world: $worldId", session)
            WebSocketUtils.broadcastToWorld(worldId, json.encodeToString(WebSocketMessage.serializer(), broadcastMessage), activeConnections, session)

            // Also broadcast a system chat message announcing the leave, if not already sent
            if (!connection.hasAnnouncedLeave) {
                try {
                    val username = (
                        try {
                            // Prefer lookup by leaveData.userId to be robust
                            userRepository.getById(correctedLeaveData.userId)?.username
                        } catch (_: Throwable) {
                            null
                        } ?: connection.user.username
                    ).ifBlank { "Visitor" }

                    val systemChat = ChatMessage(
                        worldId = worldId,
                        senderType = ChatSenderType.SYSTEM,
                        senderId = "",
                        senderName = "System",
                        message = "$username left",
                    )
                    val savedSystemChat = chatRepository.saveMessage(systemChat)
                    val chatBroadcast = WebSocketMessage(
                        action = WebSocketAction.RECEIVE_CHAT_MESSAGE,
                        data = json.encodeToString(ChatMessage.serializer(), savedSystemChat)
                    )
                    val chatBroadcastString = json.encodeToString(WebSocketMessage.serializer(), chatBroadcast)
                    WebSocketDebug.logMessage("BROADCAST_SYSTEM_CHAT_LEAVE", chatBroadcastString, session)
                    try {
                        WebSocketUtils.broadcastToWorld(worldId, chatBroadcastString, activeConnections, session)
                    } catch (e: Throwable) {
                        WebSocketDebug.logError("Error broadcasting system leave chat", e, session)
                    }
                    connection.hasAnnouncedLeave = true
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Error sending system leave chat", e, session)
                }
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing USER_LEAVE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
