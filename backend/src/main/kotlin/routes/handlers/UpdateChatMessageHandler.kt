package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json
import models.ChatMessage
import models.WebSocketAction
import repository.ChatRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for UPDATE_CHAT_MESSAGE WebSocket messages
 *
 * This handler processes chat message updates and broadcasts them to all clients in the same world.
 * It updates the message in the database before broadcasting.
 */
class UpdateChatMessageHandler(private val chatRepository: ChatRepository) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "UPDATE_CHAT_MESSAGE action", session)
        
        try {
            // Parse chat message
            val chatMessage = json.decodeFromString<ChatMessage>(message.data)
            WebSocketDebug.logMessage(
                direction = "UPDATE_CHAT_MESSAGE",
                rawMessage = "key=${chatMessage.key}, senderId=${chatMessage.senderId}, message=${chatMessage.message}",
                session = session
            )
            
            // Update message in database
            WebSocketDebug.logMessage("UPDATING", "Updating chat message in database", session)
            try {
                val updatedMessage = chatRepository.updateMessage(chatMessage.key, chatMessage, connection.user.key)
                
                if (updatedMessage != null) {
                    WebSocketDebug.logMessage("UPDATED", "Message updated with ID: ${updatedMessage.key}", session)
                    
                    // Broadcast updated message to all clients in the same world
                    val broadcastMessage = WebSocketMessage(
                        action = WebSocketAction.UPDATE_CHAT_MESSAGE,
                        data = json.encodeToString(ChatMessage.serializer(), updatedMessage)
                    )
                    
                    val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
                    WebSocketDebug.logMessage("BROADCASTING", "To world: $worldId", session)
                    WebSocketDebug.logMessage("BROADCAST_CONTENT", broadcastMessageString, session)
                    
                    WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
                } else {
                    WebSocketDebug.logError("Failed to update message: ${chatMessage.key}", null, session)
                }
            } catch (e: Throwable) {
                WebSocketDebug.logError("Error updating chat message in database", e, session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing UPDATE_CHAT_MESSAGE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
