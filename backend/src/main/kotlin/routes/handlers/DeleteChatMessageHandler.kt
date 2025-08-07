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
 * Handler for DELETE_CHAT_MESSAGE WebSocket messages
 *
 * This handler processes chat message deletions and broadcasts them to all clients in the same world.
 * It deletes the message from the database before broadcasting.
 */
class DeleteChatMessageHandler(private val chatRepository: ChatRepository) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "DELETE_CHAT_MESSAGE action", session)
        
        try {
            // Parse chat message
            val chatMessage = json.decodeFromString<ChatMessage>(message.data)
            WebSocketDebug.logMessage("DELETE_CHAT_MESSAGE", "key=${chatMessage.key}, userId=${chatMessage.userId}", session)
            
            // Delete message in database
            WebSocketDebug.logMessage("DELETING", "Deleting chat message in database", session)
            try {
                val deletedMessage = chatRepository.deleteMessage(chatMessage.key, chatMessage.userId)
                
                if (deletedMessage != null) {
                    WebSocketDebug.logMessage("DELETED", "Message deleted with ID: ${deletedMessage.key}", session)
                    
                    // Broadcast deleted message to all clients in the same world
                    val broadcastMessage = WebSocketMessage(
                        action = WebSocketAction.DELETE_CHAT_MESSAGE,
                        data = json.encodeToString(ChatMessage.serializer(), deletedMessage)
                    )
                    
                    val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
                    WebSocketDebug.logMessage("BROADCASTING", "To world: $worldId", session)
                    WebSocketDebug.logMessage("BROADCAST_CONTENT", broadcastMessageString, session)
                    
                    WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
                } else {
                    WebSocketDebug.logError("Failed to delete message: ${chatMessage.key}", null, session)
                }
            } catch (e: Exception) {
                WebSocketDebug.logError("Error deleting chat message in database", e, session)
            }
        } catch (e: Exception) {
            WebSocketDebug.logError("Error processing DELETE_CHAT_MESSAGE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
