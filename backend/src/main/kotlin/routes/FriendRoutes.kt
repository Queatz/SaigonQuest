package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import models.ErrorResponse
import models.Friend
import models.Notification
import models.NotificationType
import models.Position
import repository.FriendRepository
import repository.NotificationRepository
import repository.UserRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Routes for friend operations
 */
@OptIn(ExperimentalTime::class)
fun Routing.configureFriendRoutes() {
    val friendRepository = FriendRepository()
    val userRepository = UserRepository()
    val notificationRepository = NotificationRepository()
    
    // Data classes for request/response
    @Serializable
    data class FriendRequestBody(val friendId: String)
    
    // Get all friends for the current user
    authenticate {
        get("/friend") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
            
            val friends = friendRepository.getByUserId(userId)
            call.respond(friends)
        }
        
        // Send a friend request
        post("/friend/request") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            
            val requestBody = call.receive<FriendRequestBody>()
            val friendId = requestBody.friendId
            
            // Check if users exist
            val user = userRepository.getById(userId)
            val friend = userRepository.getById(friendId)
            
            if (user == null || friend == null) {
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
            
            // Check if they are already friends
            if (friendRepository.existsFriendship(userId, friendId)) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("Already friends"))
            }
            
            // Create a notification for the friend
            val notification = Notification(
                userId = friendId,
                title = "Friend Request",
                description = "${user.username} requested to be your friend",
                type = NotificationType.FRIEND_REQUEST,
                otherUserId = userId
            )
            
            val createdNotification = notificationRepository.create(notification)
            call.respond(HttpStatusCode.Created, createdNotification)
        }
        
        // Accept a friend request
        post("/friend/accept") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            
            val requestBody = call.receive<FriendRequestBody>()
            val friendId = requestBody.friendId
            
            // Check if users exist
            val user = userRepository.getById(userId)
            val friend = userRepository.getById(friendId)
            
            if (user == null || friend == null) {
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
            
            // Check if they are already friends
            if (friendRepository.existsFriendship(userId, friendId)) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("Already friends"))
            }
            
            // Create friendship records for both users
            val friendship1 = Friend(
                userId = userId,
                friendId = friendId,
                friendUsername = friend.username,
                lastActive = Clock.System.now(),
                lastWorldId = "",
                lastWorldName = ""
            )
            
            val friendship2 = Friend(
                userId = friendId,
                friendId = userId,
                friendUsername = user.username,
                lastActive = Clock.System.now(),
                lastWorldId = "",
                lastWorldName = ""
            )
            
            friendRepository.create(friendship1)
            friendRepository.create(friendship2)
            
            call.respond(HttpStatusCode.Created, friendship1)
        }
        
        // Remove a friend
        delete("/friend/{friendId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
            
            val friendId = call.parameters["friendId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Friend ID is required"))
            
            val success = friendRepository.deleteFriendship(userId, friendId)
            
            if (success) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Friendship not found"))
            }
        }
        
        // Update friend status (called when user changes world or position)
        put("/friend/status") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
            
            @Serializable
            data class StatusUpdateBody(
                val worldId: String,
                val worldName: String,
                val position: Position?
            )
            
            val statusUpdate = call.receive<StatusUpdateBody>()
            
            friendRepository.updateFriendStatus(
                userId = userId,
                lastWorldId = statusUpdate.worldId,
                lastWorldName = statusUpdate.worldName,
                lastPosition = statusUpdate.position
            )
            
            call.respond(HttpStatusCode.OK)
        }

        // Get online status for a set of friend IDs
        get("/friend/online") {
            val principal = call.principal<JWTPrincipal>()
            JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get

            val idsParam = call.request.queryParameters["ids"]
            if (idsParam == null) {
                return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ids query parameter is required"))
            }

            val ids = idsParam.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (ids.isEmpty()) {
                return@get call.respond(emptyMap<String, Boolean>())
            }

            // Build a set of online userIds from all worlds
            val onlineIds = activeConnections.values
                .flatMap { it }
                .map { it.userId }
                .toSet()

            val result = ids.associateWith { onlineIds.contains(it) }
            call.respond(result)
        }
    }
}
