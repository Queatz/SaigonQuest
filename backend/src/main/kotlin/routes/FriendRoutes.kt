package routes

import auth.JWTUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import models.ErrorResponse
import models.Friend
import models.FriendRequestData
import models.FriendRequestStatus
import models.FriendshipState
import models.FriendshipStatusDto
import models.Notification
import models.NotificationType
import models.Position
import models.WebSocketAction
import repository.FriendRepository
import repository.FriendRequestRepository
import repository.NotificationRepository
import repository.UserRepository
import routes.handlers.WebSocketUtils
import utils.json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Routes for friend operations
 */

fun Routing.configureFriendRoutes() {
    val friendRepository = FriendRepository()
    val userRepository = UserRepository()
    val notificationRepository = NotificationRepository()
    val friendRequestRepository = FriendRequestRepository()
    
    // Data classes for request/response
    @Serializable
    data class FriendRequestBody(val friendId: String)
    
    // Get all friends for the current user
    authenticate {
        get("/friend") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
            
            val friends = friendRepository.getByUserId(userId)

            val friendUserIds = friends.map { it.friendId }
            val usersById = userRepository.getByIds(friendUserIds).associateBy { it.key }

            // Enrich friends with live data (on-the-fly)
            val worldIdByUserId: Map<String, String> = activeConnections.flatMap { (worldId, conns) ->
                conns.map { it.userId to worldId }
            }.toMap()

            val worldRepository = repository.WorldRepository()

            val enriched = friends.map { f ->
                val currentWorldId = worldIdByUserId[f.friendId]
                val user = usersById[f.friendId]
                if (currentWorldId != null) {
                    val snapshot = state.WorldStateStore.snapshot(currentWorldId)
                    val position = snapshot.players.firstOrNull { it.userId == f.friendId }?.position
                    val worldName = worldRepository.getById(currentWorldId)?.title ?: ""
                    f.copy(
                        lastActive = Clock.System.now(),
                        lastWorldId = currentWorldId,
                        lastWorldName = worldName,
                        lastPosition = position,
                        friendStatus = user?.status ?: "",
                    )
                } else {
                    f.copy(
                        lastActive = user?.lastActive ?: Instant.DISTANT_PAST,
                        friendStatus = user?.status ?: "",
                    )
                }
            }

            call.respond(enriched)
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
        
        // Update friend note
        put("/friend/{friendId}/note") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
            
            val friendId = call.parameters["friendId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Friend ID is required"))
            
            @Serializable
            data class NoteUpdateBody(val note: String)
            
            val body = call.receive<NoteUpdateBody>()
            val success = friendRepository.updateNote(userId, friendId, body.note)
            
            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Friendship not found"))
            }
        }

        // Invite friend to world
        @Serializable
        data class WorldInvitationRequestBody(
            val friendId: String,
            val worldId: String,
            val x: Float,
            val y: Float,
            val z: Float
        )
        post("/friend/invite") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            val body = call.receive<WorldInvitationRequestBody>()

            val me = userRepository.getById(userId) ?: return@post call.respond(HttpStatusCode.NotFound)
            val worldRepository = repository.WorldRepository()
            val world = worldRepository.getById(body.worldId)

            // Check if they are friends
            if (!friendRepository.existsFriendship(userId, body.friendId)) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("You are not friends with this user"))
            }

            // Create notification for the friend
            notificationRepository.create(
                Notification(
                    userId = body.friendId,
                    title = "Invitation",
                    description = "${me.username} invited you to join them at ${world?.title ?: "their world"}",
                    type = NotificationType.WORLD_INVITATION,
                    otherUserId = userId,
                    otherUsername = me.username,
                    worldId = body.worldId,
                    worldName = world?.title ?: "",
                    x = body.x,
                    y = body.y,
                    z = body.z
                )
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

        // New Friend Requests API
        // List pending friend requests
        get("/friend-requests") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
            val direction = call.request.queryParameters["direction"]
            val list = when (direction) {
                "out" -> friendRequestRepository.getOutgoing(userId)
                else -> friendRequestRepository.getIncoming(userId)
            }
            call.respond(list)
        }

        // Create a friend request (new path)
        @Serializable
        data class NewFriendRequestBody(val toUserId: String)
        post("/friend-requests") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            val body = call.receive<NewFriendRequestBody>()
            val toUserId = body.toUserId

            if (userId == toUserId) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot send friend request to yourself"))
            }

            val me = userRepository.getById(userId)
            val other = userRepository.getById(toUserId)
            if (me == null || other == null) {
                return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }

            if (friendRepository.existsFriendship(userId, toUserId)) {
                // Already friends: respond with 204 No Content idempotently
                return@post call.respond(HttpStatusCode.NoContent)
            }

            // Cross-request auto-accept: if pending from other->me, accept it and create friendships
            val reversePending = friendRequestRepository.getPendingFromTo(toUserId, userId)
            if (reversePending != null) {
                // Accept reverse and upsert friendships atomically
                val f1 = friendRequestRepository.acceptAndUpsertFriendships(
                    requesterId = toUserId,
                    addresseeId = userId,
                    requesterUsername = other.username,
                    addresseeUsername = me.username,
                )

                // Broadcast friend request accepted (auto-accept due to reverse pending)
                run {
                    val payload = FriendRequestData(
                        id = reversePending.key,
                        requesterId = reversePending.requesterId,
                        requesterUsername = reversePending.requesterUsername,
                        addresseeId = reversePending.addresseeId,
                        status = FriendRequestStatus.Accepted,
                        createdAt = reversePending.createdAt,
                        decidedAt = Clock.System.now(),
                    )
                    val msg = WebSocketMessage(
                        action = WebSocketAction.FRIEND_REQUEST_ACCEPTED,
                        data = utils.json.encodeToString(payload)
                    )
                    val messageString = utils.json.encodeToString(msg)
                    WebSocketUtils.broadcastToUser(userId, messageString, activeConnections)
                    WebSocketUtils.broadcastToUser(toUserId, messageString, activeConnections)
                }

                // Notify original requester (reverse pending) that their friend request was accepted
                notificationRepository.create(
                    Notification(
                        userId = reversePending.requesterId,
                        title = "Friend request accepted",
                        description = "${me.username} accepted your friend request",
                        type = NotificationType.FRIEND_REQUEST_ACCEPTED,
                        otherUserId = userId,
                    )
                )

                return@post call.respond(HttpStatusCode.OK, f1 ?: Friend(
                    userId = userId,
                    friendId = toUserId,
                    friendUsername = other.username,
                    lastActive = Clock.System.now(),
                    lastWorldId = "",
                    lastWorldName = "",
                    friendStatus = other.status,
                ))
            }

            // Idempotent create using atomic upsert
            val createdOrExisting = friendRequestRepository.upsertPending(
                requesterId = userId,
                requesterUsername = me.username,
                addresseeId = toUserId,
            )

            // Notify addressee using Notifications system
            notificationRepository.create(
                Notification(
                    userId = toUserId,
                    title = "Friend Request",
                    description = "${me.username} requested to be your friend",
                    type = NotificationType.FRIEND_REQUEST,
                    otherUserId = userId,
                )
            )

            // Broadcast friend request created (to addressee)
            run {
                val req = createdOrExisting
                val payload = FriendRequestData(
                    id = req.key,
                    requesterId = req.requesterId,
                    requesterUsername = req.requesterUsername,
                    addresseeId = req.addresseeId,
                    status = req.status,
                    createdAt = req.createdAt,
                    decidedAt = req.decidedAt,
                )
                val msg = WebSocketMessage(
                    action = WebSocketAction.FRIEND_REQUEST_CREATED,
                    data = utils.json.encodeToString(payload)
                )
                val messageString = utils.json.encodeToString(msg)
                WebSocketUtils.broadcastToUser(toUserId, messageString, activeConnections)
            }

            // Respond idempotently
            call.respond(HttpStatusCode.OK, createdOrExisting)
        }

        // Accept by request id
        post("/friend-requests/{id}/accept") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required"))

            val req = friendRequestRepository.getById(id) ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Request not found"))
            if (req.addresseeId != userId) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not allowed"))
            }
            if (req.status != FriendRequestStatus.Pending) {
                return@post call.respond(HttpStatusCode.OK, req)
            }

            val me = userRepository.getById(req.addresseeId) ?: return@post call.respond(HttpStatusCode.NotFound)
            val other = userRepository.getById(req.requesterId) ?: return@post call.respond(HttpStatusCode.NotFound)

            val f1 = friendRequestRepository.acceptAndUpsertFriendships(
                requesterId = req.requesterId,
                addresseeId = req.addresseeId,
                requesterUsername = other.username,
                addresseeUsername = me.username,
            )

            // Broadcast FRIEND_REQUEST_ACCEPTED to both users
            run {
                val payload = FriendRequestData(
                    id = req.key,
                    requesterId = req.requesterId,
                    requesterUsername = req.requesterUsername,
                    addresseeId = req.addresseeId,
                    status = FriendRequestStatus.Accepted,
                    createdAt = req.createdAt,
                    decidedAt = Clock.System.now(),
                )
                val msg = WebSocketMessage(
                    action = WebSocketAction.FRIEND_REQUEST_ACCEPTED,
                    data = json.encodeToString(payload)
                )
                val messageString = json.encodeToString(msg)
                WebSocketUtils.broadcastToUser(req.addresseeId, messageString, activeConnections)
                WebSocketUtils.broadcastToUser(req.requesterId, messageString, activeConnections)
            }

            // Notify original requester that their friend request was accepted
            notificationRepository.create(
                Notification(
                    userId = req.requesterId,
                    title = "Friend request accepted",
                    description = "${me.username} accepted your friend request",
                    type = NotificationType.FRIEND_REQUEST_ACCEPTED,
                    otherUserId = req.addresseeId,
                )
            )

            call.respond(HttpStatusCode.OK, f1 ?: Friend(
                userId = req.addresseeId,
                friendId = req.requesterId,
                friendUsername = other.username,
                lastActive = Clock.System.now(),
                lastWorldId = "",
                lastWorldName = "",
                friendStatus = other.status,
            ))
        }

        // Decline by request id
        post("/friend-requests/{id}/decline") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required"))
            val req = friendRequestRepository.getById(id) ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Request not found"))
            if (req.addresseeId != userId) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not allowed"))
            }
            if (req.status != FriendRequestStatus.Pending) {
                return@post call.respond(HttpStatusCode.OK, req)
            }
            val updated = friendRequestRepository.updateStatus(id, FriendRequestStatus.Declined)

            // Broadcast FRIEND_REQUEST_DECLINED to requester
            run {
                val r = updated ?: req
                val payload = FriendRequestData(
                    id = r.key,
                    requesterId = r.requesterId,
                    requesterUsername = r.requesterUsername,
                    addresseeId = r.addresseeId,
                    status = FriendRequestStatus.Declined,
                    createdAt = r.createdAt,
                    decidedAt = Clock.System.now(),
                )
                val msg = WebSocketMessage(
                    action = WebSocketAction.FRIEND_REQUEST_DECLINED,
                    data = json.encodeToString(payload)
                )
                val messageString = json.encodeToString(msg)
                WebSocketUtils.broadcastToUser(r.requesterId, messageString, activeConnections)
            }

            call.respond(HttpStatusCode.OK, updated ?: req)
        }

        // Cancel by request id (only requester)
        post("/friend-requests/{id}/cancel") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required"))
            val req = friendRequestRepository.getById(id) ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Request not found"))
            if (req.requesterId != userId) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not allowed"))
            }
            if (req.status != FriendRequestStatus.Pending) {
                return@post call.respond(HttpStatusCode.OK, req)
            }
            val updated = friendRequestRepository.updateStatus(id, FriendRequestStatus.Cancelled)

            // Broadcast FRIEND_REQUEST_CANCELLED to addressee
            run {
                val r = updated ?: req
                val payload = FriendRequestData(
                    id = r.key,
                    requesterId = r.requesterId,
                    requesterUsername = r.requesterUsername,
                    addresseeId = r.addresseeId,
                    status = FriendRequestStatus.Cancelled,
                    createdAt = r.createdAt,
                    decidedAt = Clock.System.now(),
                )
                val msg = WebSocketMessage(
                    action = WebSocketAction.FRIEND_REQUEST_CANCELLED,
                    data = json.encodeToString(payload)
                )
                val messageString = json.encodeToString(msg)
                WebSocketUtils.broadcastToUser(r.addresseeId, messageString, activeConnections)
            }

            call.respond(HttpStatusCode.OK, updated ?: req)
        }

        // Server-driven friendship status
        get("/friendships/status/{otherUserId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
            val otherUserId = call.parameters["otherUserId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("otherUserId is required"))

            if (friendRepository.existsFriendship(userId, otherUserId)) {
                return@get call.respond(FriendshipStatusDto(status = FriendshipState.Friends, requestId = null))
            }

            val outgoing = friendRequestRepository.getPendingFromTo(userId, otherUserId)
            if (outgoing != null) {
                return@get call.respond(FriendshipStatusDto(status = FriendshipState.OutgoingRequest, requestId = outgoing.key))
            }
            val incoming = friendRequestRepository.getPendingFromTo(otherUserId, userId)
            if (incoming != null) {
                return@get call.respond(FriendshipStatusDto(status = FriendshipState.IncomingRequest, requestId = incoming.key))
            }
            call.respond(FriendshipStatusDto(status = FriendshipState.NotFriends, requestId = null))
        }
    }
}
