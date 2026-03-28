package routes.handlers

import debug.WebSocketDebug
import io.ktor.server.websocket.DefaultWebSocketServerSession
import models.ChatMessage
import models.ChatSenderType
import models.Notification
import models.NotificationType
import models.WebSocketAction
import repository.ChatRepository
import repository.NotificationRepository
import repository.WorldRepository
import routes.WebSocketConnection
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import services.AiManager
import services.AlibabaAiClient

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
            val incoming = json.decodeFromString<ChatMessage>(message.data)
            WebSocketDebug.logMessage("CHAT_MESSAGE", "worldId=${incoming.worldId}, senderType=${incoming.senderType}, senderId=${incoming.senderId}, message=${incoming.message}", session)

            // Sanitize/override sender identity based on connection and requested type
            val sanitized: ChatMessage = when (incoming.senderType) {
                null, ChatSenderType.USER -> incoming.copy(
                    senderType = ChatSenderType.USER,
                    senderId = connection.user.key,
                    senderName = connection.user.username,
                )
                ChatSenderType.OBJECT -> incoming.copy(
                    senderType = ChatSenderType.OBJECT,
                    senderName = incoming.senderName.ifBlank { "Object" },
                )
                ChatSenderType.SYSTEM -> incoming.copy(
                    senderType = ChatSenderType.SYSTEM,
                    senderId = "",
                    senderName = incoming.senderName.ifBlank { "System" },
                )
            }
            
            // Save message to database
            WebSocketDebug.logMessage("SAVING", "Saving chat message to database", session)
            try {
                val savedMessage = chatRepository.saveMessage(sanitized)
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

                // --- Background Transcription ---
                if (savedMessage.message.contains("![audio](")) {
                    val audioUrl = savedMessage.message.substringAfter("![audio](").substringBefore(")")
                    println("[DEBUG_LOG] Detected audio message: $audioUrl")
                    
                    if (audioUrl.isNotBlank()) {
                        val client = AiManager.client
                        println("[DEBUG_LOG] Using transcription client: ${client::class.simpleName}")
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                println("[DEBUG_LOG] Starting async transcription for: $audioUrl")
                                val transcript = client.transcribe(audioUrl)
                                
                                if (!transcript.isNullOrBlank()) {
                                    println("[DEBUG_LOG] Transcription successful: \"$transcript\"")
                                    val updatedText = "${savedMessage.message}\n\n$transcript"
                                    val updatedMessage = savedMessage.copy(
                                        message = updatedText,
                                        edited = true,
                                    )
                                    val successUpdate = chatRepository.updateMessage(
                                        savedMessage.key,
                                        updatedMessage,
                                        savedMessage.senderId
                                    )
                                    
                                    if (successUpdate != null) {
                                        println("[DEBUG_LOG] Broadcasting updated chat message: ${successUpdate.key}")
                                        // Broadcast the update
                                        val broadcastUpdate = WebSocketMessage(
                                            action = WebSocketAction.UPDATE_CHAT_MESSAGE,
                                            data = json.encodeToString(ChatMessage.serializer(), successUpdate)
                                        )
                                        val payloadUpdate = json.encodeToString(WebSocketMessage.serializer(), broadcastUpdate)
                                        broadcastToWorldNoSession(worldId, payloadUpdate, activeConnections)
                                    } else {
                                        println("[DEBUG_LOG] Failed to update chat message in DB.")
                                    }
                                } else {
                                    println("[DEBUG_LOG] Transcription returned null or blank.")
                                }
                            } catch (e: Throwable) {
                                println("[DEBUG_LOG] Async transcription failed: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        println("[DEBUG_LOG] Audio URL is blank, transcription skipped.")
                    }
                }

                // After broadcasting, send one-time "new chat" notifications to eligible users
                try {
                    val worldRepo = WorldRepository()
                    val notificationRepo = NotificationRepository()
                    val world = try { worldRepo.getById(worldId) } catch (_: Throwable) { null }

                    // Build presence map: userId -> worldId they're currently in (if any)
                    val worldByUser: Map<String, String> = activeConnections.flatMap { (wId, conns) ->
                        conns.map { it.userId to wId }
                    }.toMap()

                    val markers = try { chatRepository.getReadMarkersByWorld(worldId) } catch (_: Throwable) { emptyList() }

                    markers.forEach { marker ->
                        try {
                            // Skip notifying the sender for USER-authored messages
                            if (savedMessage.senderType == ChatSenderType.USER && marker.userId == savedMessage.senderId) return@forEach
                            // Already notified since last visit
                            if (marker.notificationSent) return@forEach
                            // If their last read is at/after this message timestamp, skip
                            if (marker.lastReadTimestamp >= savedMessage.timestamp) return@forEach
                            // If online and currently in this same world, skip
                            val currentWorldForUser = worldByUser[marker.userId]
                            if (currentWorldForUser == worldId) return@forEach

                            val title = "New chat from \"${savedMessage.senderName}\" in \"${world?.title ?: "World"}\""
                            val description = savedMessage.message
                            val notification = Notification(
                                userId = marker.userId,
                                title = title,
                                description = description,
                                isRead = false,
                                type = NotificationType.CHAT_NEW,
                                worldId = worldId,
                                objectId = if (savedMessage.senderType == ChatSenderType.OBJECT) savedMessage.senderId else "",
                                otherUserId = if (savedMessage.senderType == ChatSenderType.USER) savedMessage.senderId else "",
                                timestamp = savedMessage.timestamp,
                            )
                            notificationRepo.create(notification)
                            chatRepository.setNotificationSent(marker.userId, worldId, true)
                        } catch (_: Throwable) { /* continue per user */ }
                    }
                } catch (_: Throwable) { /* keep chat pipeline resilient */ }
            } catch (e: Throwable) {
                WebSocketDebug.logError("Error saving chat message to database", e, session)
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error processing SEND_CHAT_MESSAGE message", e, session)
            WebSocketDebug.logMessage("MALFORMED_DATA", message.data, session)
        }
    }
}
