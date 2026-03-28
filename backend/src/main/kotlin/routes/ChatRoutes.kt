package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.receive
import services.AiManager
import services.AlibabaAiClient
import models.ChatMessage
import models.ChatReadMarker
import models.ErrorResponse
import models.ChatSenderType
import repository.ChatRepository
import repository.WorldRepository
import repository.ObjectRepository
import models.ChatMessagePin
import models.Notification
import models.NotificationType
import models.canUserEdit
import models.WebSocketAction
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
    val objectRepository = ObjectRepository()

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
                chatRepository.updateLastReadTimestamp(
                    userId = userId,
                    worldId = worldId,
                    timestamp = Clock.System.now()
                )
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
                
                // Create a ChatReadMarker object
                val readMarker = ChatReadMarker(
                    userId = userId,
                    worldId = worldId,
                    lastReadTimestamp = Clock.System.now()
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
                
                // Receive JSON body only (no receiveText)
                val body = try { call.receive<models.chat.CreateChatMessageRequest>() } catch (t: Throwable) {
                    t.printStackTrace()
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request body", code = HttpStatusCode.BadRequest.value)
                    )
                }
                
                if (body.message.isBlank() && body.attachments.isEmpty()) {
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
                
                // Create chat message based on sender type
                val chatMessage = when (body.senderType) {
                    null, ChatSenderType.USER -> ChatMessage(
                        worldId = worldId,
                        senderType = ChatSenderType.USER,
                        senderId = userId,
                        senderName = user.username,
                        message = body.message,
                        timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                        attachments = body.attachments,
                    )
                    ChatSenderType.OBJECT -> {
                        val objectId = body.senderId.ifBlank {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(message = "Missing objectId (senderId) for OBJECT message", code = HttpStatusCode.BadRequest.value)
                            )
                        }
                        // Validate object and fetch display name
                        val obj = objectRepository.getById(objectId) ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(message = "Object not found", code = HttpStatusCode.NotFound.value)
                        )
                        if (obj.worldId != worldId) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(message = "Object does not belong to this world", code = HttpStatusCode.BadRequest.value)
                            )
                        }
                        val objectName = obj.title.ifBlank { "Object" }
                        ChatMessage(
                            worldId = worldId,
                            senderType = ChatSenderType.OBJECT,
                            senderId = objectId,
                            senderName = objectName,
                            message = body.message,
                            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                            attachments = body.attachments,
                        )
                    }
                    ChatSenderType.SYSTEM -> {
                        // Only world owner can send system messages
                        val world = worldRepository.getById(worldId) ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                        )
                        if (!world.canUserEdit(userId)) {
                            return@post call.respond(
                                HttpStatusCode.Forbidden,
                                ErrorResponse(message = "Only owner can send system messages", code = HttpStatusCode.Forbidden.value)
                            )
                        }
                        ChatMessage(
                            worldId = worldId,
                            senderType = ChatSenderType.SYSTEM,
                            senderId = "",
                            senderName = "System",
                            message = body.message,
                            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                            attachments = body.attachments,
                        )
                    }
                }
                
                // Save message
                val savedMessage = chatRepository.saveMessage(chatMessage)

                // Broadcast the new message to all clients in the world (including sender)
                try {
                    val wsMessage = routes.WebSocketMessage(
                        action = WebSocketAction.RECEIVE_CHAT_MESSAGE,
                        data = json.encodeToString(ChatMessage.serializer(), savedMessage),
                    )
                    val payload = json.encodeToString(wsMessage)
                    routes.handlers.broadcastToWorldNoSession(worldId, payload, activeConnections)
                } catch (_: Throwable) { /* keep REST path resilient */ }

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
                            // Respect subscription: only notify subscribed users
                            if (!marker.subscribed) return@forEach
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
                    chatRepository.updateMessage(messageId, updatedMessage, userId)
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

        // Subscription management
        authenticate {
            get("/{worldId}/subscription") {
                val worldId = call.parameters["worldId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val subscribed = try { chatRepository.getSubscription(userId, worldId) } catch (_: Throwable) { true }
                call.respond(mapOf("subscribed" to subscribed))
            }
            post("/{worldId}/subscribe") {
                val worldId = call.parameters["worldId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val ok = chatRepository.setSubscription(userId, worldId, true)
                if (ok) call.respond(HttpStatusCode.OK) else call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse(message = "Failed to subscribe", code = HttpStatusCode.InternalServerError.value))
            }
            delete("/{worldId}/subscribe") {
                val worldId = call.parameters["worldId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                val ok = chatRepository.setSubscription(userId, worldId, false)
                if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse(message = "Failed to unsubscribe", code = HttpStatusCode.InternalServerError.value))
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

                if (!world.canUserEdit(userId)) {
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

                if (!world.canUserEdit(userId)) {
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

        // Visited worlds management
        authenticate {
            get("/visited") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val items = chatRepository.listVisitedWorlds(userId)
                call.respond(items)
            }
            delete("/visited/{worldId}") {
                val worldId = call.parameters["worldId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                val ok = chatRepository.deleteReadMarker(userId, worldId)
                if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound,
                    ErrorResponse(message = "Visited world not found", code = HttpStatusCode.NotFound.value))
            }
        }

        // Transcribe audio - requires authentication
        authenticate {
            post("/transcribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = auth.JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val body = call.receive<Map<String, String>>()
                val audioUrl = body["audioUrl"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing audioUrl parameter", code = HttpStatusCode.BadRequest.value)
                )

                val client = AiManager.transcriptionClient
                if (!AiManager.isConfigured(client)) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            message = "Transcription provider not configured (missing ${AiManager.getApiKeyName(client)})",
                            code = HttpStatusCode.InternalServerError.value,
                        )
                    )
                }

                val text = client.transcribe(audioUrl)
                if (text != null) {
                    call.respond(mapOf("text" to text))
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Transcription failed", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
    }
}
