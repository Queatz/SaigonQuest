package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ErrorResponse
import models.Notification
import repository.NotificationRepository
import repository.UserRepository

/**
 * Configure routes for notification operations
 */
fun Routing.configureNotificationRoutes() {
    val notificationRepository = NotificationRepository()
    val userRepository = UserRepository()
    
    route("/notification") {
        // All notification endpoints require authentication
        authenticate {
            // Get all notifications for the current user
            get {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                // Optional limit parameter
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                
                // Get notifications
                val notifications = notificationRepository.getByUserId(userId, limit)
                
                call.respond(notifications)
            }
            
            // Get unread notification count for the current user
            get("/unread-count") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                // Get unread count
                val unreadCount = notificationRepository.getUnreadCount(userId)
                
                call.respond(unreadCount)
            }
            
            // Mark a notification as read
            put("/{id}/read") {
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Get notification
                val notification = notificationRepository.getById(id)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Notification not found", code = HttpStatusCode.NotFound.value)
                    )
                
                // Check if notification belongs to user
                if (notification.userId != userId) {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            message = "You don't have permission to access this notification",
                            code = HttpStatusCode.Forbidden.value
                        )
                    )
                }
                
                // Mark as read
                val updatedNotification = notificationRepository.markAsRead(id)
                    ?: return@put call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to mark notification as read", code = HttpStatusCode.InternalServerError.value)
                    )
                
                call.respond(updatedNotification)
            }
            
            // Mark all notifications as read for the current user
            put("/read-all") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Mark all as read
                val count = notificationRepository.markAllAsRead(userId)
                
                call.respond(mapOf("markedAsRead" to count))
            }
            
            // Create a notification (admin only)
            post {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get user from database and check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                // Get notification from request
                val notification = call.receive<Notification>()
                
                // Create notification
                val createdNotification = notificationRepository.create(notification)
                
                call.respond(HttpStatusCode.Created, createdNotification)
            }
        }
    }
}
