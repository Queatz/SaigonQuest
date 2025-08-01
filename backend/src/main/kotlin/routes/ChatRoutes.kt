package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ChatMessage
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
            val userId = principal?.payload?.getClaim("userId")?.asString()
            
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
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(message = "Invalid token", code = HttpStatusCode.Unauthorized.value)
                )
                
                // Get last read timestamp
                val lastReadTimestamp = chatRepository.getLastReadTimestamp(userId, worldId)
                
                // Get unread count
                val unreadCount = chatRepository.getUnreadMessageCount(userId, worldId, lastReadTimestamp)
                
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
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@put call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(message = "Invalid token", code = HttpStatusCode.Unauthorized.value)
                )
                
                // Get timestamp from request or use current time
                val timestamp = call.request.queryParameters["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
                
                // Update last read timestamp
                val success = chatRepository.updateLastReadTimestamp(userId, worldId, timestamp)
                
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
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(message = "Invalid token", code = HttpStatusCode.Unauthorized.value)
                )
                
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
    }
}
