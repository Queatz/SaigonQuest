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
 * Handler for SEND_CHAT_MESSAGE WebSocket messages
 *
 * This handler processes chat message sending and broadcasts them to all clients in the same world.
 * It saves the message to the database before broadcasting.
 */
class SendChatMessageHandler(private val chatRepository: ChatRepository) : WebSocketHandler {
    override suspend fun handle(
        message: WebSocketMessage,
        connection: WebSocketConnection,
        worldId: String,
        activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>,
        session: DefaultWebSocketServerSession
    ) {
        WebSocketDebug.logMessage("PROCESSING", "SEND_CHAT_MESSAGE action", session)
        
        try {
            // Parse chat message
            val chatMessage = json.decodeFromString<ChatMessage>(message.data)
            WebSocketDebug.logMessage("CHAT_MESSAGE", "worldId=${chatMessage.worldId}, userId=${chatMessage.userId}, message=${chatMessage.message}", session)
            
            // Save message to database
            WebSocketDebug.logMessage("SAVING", "Saving chat message to database", session)
            try {
                val savedMessage = chatRepository.saveMessage(chatMessage)
                WebSocketDebug.logMessage("SAVED", "Message saved with ID: ${savedMessage.key}", session)
                
                // Broadcast message to all clients in the same world
                val broadcastMessage = WebSocketMessage(
                    action = WebSocketAction.RECEIVE_CHAT_MESSAGE,
                    data = json.encodeToString(ChatMessage.serializer(), savedMessage)
                )
                
                val broadcastMessageString = json.encodeToString(WebSocketMessage.serializer(), broadcastMessage)
                WebSocketDebug.logMessage("BROADCASTING", "To world: $worldId", session)
                WebSocketDebug.logMessage("BROADCAST_CONTENT", broadcastMessageString, session)
                
                WebSocketUtils.broadcastToWorld(worldId, broadcastMessageString, activeConnections, session)
            } catch (e: Throwable) {
                WebSocketDebug.logError("Error saving chat message to database", e, session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing SEND_CHAT_MESSAGE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
