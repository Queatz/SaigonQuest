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
import repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Configure routes for chat operations
 */
fun Routing.configureChatRoutes() {
    val chatRepository = ChatRepository()
    val userRepository = UserRepository()
    
    route("/chat") {
        // Get chat messages for a world - no authentication required for reading
        get("/{worldId}") {
            val worldId = call.parameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            // Optional parameters
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val olderThan = call.request.queryParameters["olderThan"]?.toLongOrNull() ?: System.currentTimeMillis()
            
            // Get messages
            val messages = chatRepository.getMessagesByWorldId(worldId, limit, olderThan)
            
            // If authenticated, update last read timestamp
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserId(principal)

            // Update last read
            if (userId != null) {
                chatRepository.updateLastReadTimestamp(userId, worldId, System.currentTimeMillis())
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
                    lastReadTimestamp = timestamp
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
                    timestamp = System.currentTimeMillis()
                )
                
                // Save message
                val savedMessage = chatRepository.saveMessage(chatMessage)
                
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
    }
}
