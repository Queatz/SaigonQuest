package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ChatMessage
import models.ChatReadMarker
import models.ErrorResponse
import repository.ChatRepository
import repository.WorldRepository
import models.ChatMessagePin
import models.WebSocketAction
import routes.activeConnections
import utils.json
import repository.UserRepository
import kotlin.time.Clock

import kotlin.time.Instant

/**
 * Configure routes for chat operations
 */
fun Routing.configureChatRoutes() {
    val chatRepository = ChatRepository()
    val userRepository = UserRepository()
    val worldRepository = WorldRepository()
    
    route("/chat") {
        // Get chat messages for a world - no authentication required for reading
        get("/{worldId}") {
            val worldId = call.parameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            // Optional parameters
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val olderThan = call.request.queryParameters["olderThan"]?.toLongOrNull()?.let {
                Instant.fromEpochMilliseconds(it)
            } ?: Clock.System.now()

            // Determine minimum timestamp based on world's chat longevity (0.0 = forever)
            val minTimestamp = try {
                val world = worldRepository.getById(worldId)
                val days = world?.chatLongevity ?: 0.0
                if (days > 0.0) {
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    val cutoffMs = nowMs - (days * 24.0 * 60.0 * 60.0 * 1000.0).toLong()
                    Instant.fromEpochMilliseconds(cutoffMs)
                } else null
            } catch (_: Throwable) { null }
            
            // Get messages
            val messages = chatRepository.getMessagesByWorldId(worldId, limit, olderThan, minTimestamp)
            
            // If authenticated, update last read timestamp
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserId(principal)

            // Update last read
            if (userId != null) {
                chatRepository.updateLastReadTimestamp(userId, worldId, Clock.System.now())
            }
            
            // Return messages
            call.respond(messages)
        }
        
        // Get unread message count for a world - requires authentication
        authenticate {
            get("/{worldId}/unread-count") {
                val worldId = call.parameters["worldId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                // Create a ChatReadMarker to represent the query
                val readMarker = ChatReadMarker(
                    userId = userId,
                    worldId = worldId
                )
                
                // Get last read timestamp using the ChatReadMarker object
                val lastReadTimestamp = chatRepository.getLastReadTimestamp(readMarker)
                
                // Update the lastReadTimestamp in the readMarker
                readMarker.lastReadTimestamp = lastReadTimestamp
                
                // Get unread count using the ChatReadMarker object
                val unreadCount = chatRepository.getUnreadMessageCount(readMarker)
                
                call.respond(unreadCount)
            }
        }
        
        // Mark messages as read up to a timestamp - requires authentication
        authenticate {
            put("/{worldId}/mark-read") {
                val worldId = call.parameters["worldId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Get timestamp from request or use current time
                val timestamp = call.request.queryParameters["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
                
                // Create a ChatReadMarker object
                val readMarker = ChatReadMarker(
                    userId = userId,
                    worldId = worldId,
                    lastReadTimestamp = Instant.fromEpochMilliseconds(timestamp)
                )
                
                // Update last read timestamp using the ChatReadMarker object
                val success = chatRepository.updateLastReadTimestamp(readMarker)
                
                if (success) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to update read timestamp", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
        
        // Save a chat message - requires authentication
        authenticate {
            post("/{worldId}") {
                val worldId = call.parameters["worldId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get message text from request
                val messageText = call.receiveText()
                
                if (messageText.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Message cannot be empty", code = HttpStatusCode.BadRequest.value)
                    )
                }
                
                // Get user details
                val user = userRepository.getById(userId) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                )
                
                // Create chat message
                val chatMessage = ChatMessage(
                    worldId = worldId,
                    userId = userId,
                    username = user.username,
                    message = messageText,
                    timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                )
                
                // Save message
                val savedMessage = chatRepository.saveMessage(chatMessage)

                // After saving, send one-time "new chat" notifications to eligible users
                try {
                    val world = try { worldRepository.getById(worldId) } catch (_: Throwable) { null }

                    // Build presence map: userId -> worldId they're currently in (if any)
                    val worldByUser: Map<String, String> = activeConnections.flatMap { (wId, conns) ->
                        conns.map { it.userId to wId }
                    }.toMap()

                    val markers = try { chatRepository.getReadMarkersByWorld(worldId) } catch (_: Throwable) { emptyList() }

                    val notificationRepo = repository.NotificationRepository()
                    markers.forEach { marker ->
                        try {
                            // Skip sender
                            if (marker.userId == savedMessage.userId) return@forEach
                            // Already notified since last visit
                            if (marker.notificationSent) return@forEach
                            // If their last read is at/after this message timestamp, skip
                            if (marker.lastReadTimestamp >= savedMessage.timestamp) return@forEach
                            // If online and currently in this same world, skip
                            val currentWorldForUser = worldByUser[marker.userId]
                            if (currentWorldForUser == worldId) return@forEach

                            val title = "New chat from \"${savedMessage.username}\" in \"${world?.title ?: "World"}\""
                            val description = savedMessage.message
                            val notification = models.Notification(
                                userId = marker.userId,
                                title = title,
                                description = description,
                                isRead = false,
                                type = models.NotificationType.CHAT_NEW,
                                worldId = worldId,
                                objectId = "",
                                otherUserId = savedMessage.userId,
                                timestamp = savedMessage.timestamp,
                            )
                            notificationRepo.create(notification)
                            chatRepository.setNotificationSent(marker.userId, worldId, true)
                        } catch (_: Throwable) { /* continue per user */ }
                    }
                } catch (_: Throwable) { /* keep REST path resilient */ }
                
                call.respond(HttpStatusCode.Created, savedMessage)
            }
        }
        
        // Update or delete a chat message - requires authentication
        authenticate {
            put("/{messageId}") {
                val messageId = call.parameters["messageId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing messageId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Get updated message from request
                val updatedMessage = call.receive<ChatMessage>()
                
                // Ensure the message exists and belongs to the user
                val result = if (updatedMessage.deleted) {
                    // Delete message
                    chatRepository.deleteMessage(messageId, userId)
                } else {
                    // Update message
                    chatRepository.updateMessage(messageId, updatedMessage)
                }
                
                if (result != null) {
                    call.respond(result)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Message not found or you don't have permission to modify it", code = HttpStatusCode.NotFound.value)
                    )
                }
            }
        }

        // List pinned messages for a world (public)
        get("/{worldId}/pins") {
            val worldId = call.parameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
            )

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val pins = chatRepository.listPins(worldId, limit, offset)
            call.respond(pins)
        }

        // Pin a message (owner only)
        authenticate {
            post("/{worldId}/messages/{messageId}/pin") {
                val worldId = call.parameters["worldId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val messageId = call.parameters["messageId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing messageId parameter", code = HttpStatusCode.BadRequest.value)
                )

                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val world = worldRepository.getById(worldId) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )

                if (world.userId != userId) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Only the world owner can pin messages", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val pin = chatRepository.pinMessage(worldId, messageId, userId)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Message not found or not in this world", code = HttpStatusCode.NotFound.value)
                    )

                // Broadcast pin added to websocket clients
                try {
                    val wsMessage = routes.WebSocketMessage(
                        action = WebSocketAction.PIN_ADDED,
                        data = json.encodeToString(ChatMessagePin.serializer(), pin)
                    )
                    val payload = json.encodeToString(wsMessage)
                    routes.handlers.broadcastToWorldNoSession(worldId, payload, activeConnections)
                } catch (_: Throwable) {}

                call.respond(HttpStatusCode.OK, pin)
            }
        }

        // Unpin a message (owner only)
        authenticate {
            delete("/{worldId}/messages/{messageId}/pin") {
                val worldId = call.parameters["worldId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val messageId = call.parameters["messageId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing messageId parameter", code = HttpStatusCode.BadRequest.value)
                )

                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete

                val world = worldRepository.getById(worldId) ?: return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )

                if (world.userId != userId) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Only the world owner can unpin messages", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val removed = chatRepository.unpinMessage(worldId, messageId)
                if (!removed) {
                    return@delete call.respond(HttpStatusCode.NotFound,
                        ErrorResponse(message = "Pin not found", code = HttpStatusCode.NotFound.value))
                }

                // Broadcast pin removed to websocket clients (send messageId as payload)
                try {
                    val wsMessage = routes.WebSocketMessage(
                        action = WebSocketAction.PIN_REMOVED,
                        data = json.encodeToString(messageId)
                    )
                    val payload = json.encodeToString(wsMessage)
                    routes.handlers.broadcastToWorldNoSession(worldId, payload, activeConnections)
                } catch (_: Throwable) {}

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
