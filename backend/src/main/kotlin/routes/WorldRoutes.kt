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
import models.World
import models.canUserEdit
import models.Notification
import models.NotificationType
import models.UserBalanceHistory
import models.UserBalanceHistoryEventType
import repository.WorldRepository
import repository.UserRepository
import repository.FriendRepository
import repository.InventoryRepository
import repository.NotificationRepository

fun Routing.configureWorldRoutes() {
    val worldRepository = WorldRepository()
    val userRepository = UserRepository()
    val friendRepository = FriendRepository()
    val inventoryRepository = InventoryRepository()
    val balances = repository.UserBalanceRepository()
    val balanceHistory = repository.UserBalanceHistoryRepository()
    val notificationRepository = NotificationRepository()
    val userWorldPositionRepository = repository.UserWorldPositionRepository()

    fun checkAccess(call: ApplicationCall, world: World): ErrorResponse? {
        val principal = call.principal<JWTPrincipal>()
        val requesterUserId = JWTUtils.getUserId(principal)

        // Owner or co-owner is always allowed
        if (requesterUserId != null && world.canUserEdit(requesterUserId)) {
            return null
        }

        // All visitors allowed
        if (world.allowAllVisitors) {
            return null
        }

        // Players only
        if (world.allowAllPlayers) {
            if (requesterUserId == null) {
                return ErrorResponse(
                    message = "This world is locked. Keep exploring!",
                    code = HttpStatusCode.Forbidden.value,
                    details = "ACCESS_REQUIRES_PLAYER"
                )
            }
            val user = userRepository.getById(requesterUserId)
            if (user?.isPlayer == true) {
                return null
            }
            return ErrorResponse(
                message = "This world is locked. Keep exploring!",
                code = HttpStatusCode.Forbidden.value,
                details = "ACCESS_REQUIRES_PLAYER"
            )
        }

        // Friends of the owner(s) only
        if (world.allowFriendsOfOwners) {
            if (requesterUserId == null) {
                return ErrorResponse(
                    message = "This world is locked. Keep exploring!",
                    code = HttpStatusCode.Forbidden.value,
                    details = "ACCESS_REQUIRES_FRIEND"
                )
            }
            val ownerIds = buildList {
                if (world.userId.isNotBlank()) add(world.userId)
                addAll(world.ownerIds)
            }
            val isFriendOfAnyOwner = ownerIds.any { ownerId ->
                try { friendRepository.existsFriendship(requesterUserId, ownerId) } catch (_: Throwable) { false }
            }
            if (isFriendOfAnyOwner) {
                return null
            }
            return ErrorResponse(
                message = "This world is locked. Keep exploring!",
                code = HttpStatusCode.Forbidden.value,
                details = "ACCESS_REQUIRES_FRIEND"
            )
        }

        // Holders of a specific item only
        val requiredItemId = world.allowHoldersOfItemId
        if (!requiredItemId.isNullOrBlank()) {
            if (requesterUserId == null) {
                return ErrorResponse(
                    message = "This world is locked. Keep exploring!",
                    code = HttpStatusCode.Forbidden.value,
                    details = "ACCESS_REQUIRES_ITEM"
                )
            }
            val hasItem = inventoryRepository.userHasItem(requesterUserId, requiredItemId)
            if (hasItem) {
                return null
            }
            return ErrorResponse(
                message = "This world is locked. Keep exploring!",
                code = HttpStatusCode.Forbidden.value,
                details = "ACCESS_REQUIRES_ITEM"
            )
        }

        // Default deny
        return ErrorResponse(
            message = "Access denied",
            code = HttpStatusCode.Forbidden.value,
            details = "ACCESS_DENIED"
        )
    }
    
    route("/world") {
        // Create new world - requires authentication
        authenticate {
            post {
                val world = call.receive<World>()
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Set the user ID in the world object
                world.userId = userId

                // World base cost (VND) - centralized via CostsProvider
                val cost = utils.CostsProvider.get().worldBasePrice
                world.url = world.url.sanitizeWorldUrl()
                
                // Ensure user has enough balance and deduct
                val before = balances.getByUserId(userId)?.balance ?: 0L
                if (before < cost) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Insufficient funds", code = HttpStatusCode.BadRequest.value)
                    )
                }
                val after = before - cost
                val savedBal = balances.upsert(userId) { it.balance = after }
                
                // Record balance history as a Use event
                if (cost != 0L) try {
                    balanceHistory.add(
                        UserBalanceHistory(
                            userId = userId,
                            eventType = UserBalanceHistoryEventType.Use,
                            notes = "Bought world: ${world.title}".trim(),
                            changeAmount = -cost,
                            startBalance = before,
                            endBalance = savedBal.balance,
                            attachments = emptyList()
                        )
                    )
                } catch (_: Throwable) {
                    // Non-fatal: failure to record history shouldn't block world creation
                }
                
                val createdWorld = worldRepository.create(world)
                call.respond(HttpStatusCode.Created, createdWorld)
            }
        }
        
        // Get all worlds for current user - requires authentication
        authenticate {
            get {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                val worlds = worldRepository.getByUserId(userId)
                call.respond(worlds)
            }

            // Get all worlds from friends - requires authentication
            get("/friends") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                val worlds = worldRepository.getFriendsWorlds(userId)
                call.respond(worlds)
            }
        }
        
        // Get world by URL - optional authentication (for access checks)
        authenticate(optional = true) {
            get("/url") {
            val url = call.request.queryParameters["url"] ?: ""

            val world = worldRepository.getByUrl(url)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )

            val denial = checkAccess(call, world)
            if (denial != null) {
                return@get call.respond(HttpStatusCode.Forbidden, denial)
            }

            // Populate last known position for the current user
            val userId = call.principal<JWTPrincipal>()?.let { JWTUtils.getUserId(it) }
            if (userId != null) {
                world.lastPosition = userWorldPositionRepository.get(userId, world.key)?.position
            }

            call.respond(world)
        }
        
        // Get world by ID - no authentication required
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
            )

            val world = worldRepository.getById(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )

            val denial = checkAccess(call, world)
            if (denial != null) {
                return@get call.respond(HttpStatusCode.Forbidden, denial)
            }

            // Populate last known position for the current user
            val userId = call.principal<JWTPrincipal>()?.let { JWTUtils.getUserId(it) }
            if (userId != null) {
                world.lastPosition = userWorldPositionRepository.get(userId, world.key)?.position
            }

            call.respond(world)
        }
        
        // Get authoritative world state snapshot - no authentication required
        get("/{id}/state") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
            )
            // Use the same key as WebSocket presence (worldId passed to /ws)
            // This ensures snapshot aligns with state updated by WS handlers
            val snapshot = state.WorldStateStore.snapshot(id)
            call.respond(snapshot)
        }
        }
        
        // Update world - requires authentication
        authenticate {
            post("/{id}") {
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                val worldUpdate = call.receive<World>()
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Check if the world exists
                val existingWorld = worldRepository.getById(id) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )
                // Allow updates by any owner/co-owner
                if (!existingWorld.canUserEdit(userId)) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            message = "You don't have permission to update this world",
                            code = HttpStatusCode.Forbidden.value
                        )
                    )
                }
                // Only the primary owner can change ownerIds
                val ownerIdsChanging = try { existingWorld.ownerIds.toSet() != worldUpdate.ownerIds.toSet() } catch (_: Throwable) { false }
                if (ownerIdsChanging && userId != existingWorld.userId) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            message = "Only the primary owner can change owners",
                            code = HttpStatusCode.Forbidden.value
                        )
                    )
                }
                // Compute newly added owners for notifications
                val addedOwnerIds: List<String> = if (ownerIdsChanging) {
                    val before = existingWorld.ownerIds.toSet()
                    val after = worldUpdate.ownerIds.toSet()
                    (after - before).filter { it.isNotBlank() }
                } else emptyList()
                
                val updatedWorld = worldRepository.update(id, worldUpdate)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Failed to update world", code = HttpStatusCode.NotFound.value)
                    )
                
                // Notify newly added owners
                if (addedOwnerIds.isNotEmpty()) {
                    try {
                        addedOwnerIds.forEach { newOwnerId ->
                            try {
                                val note = Notification(
                                    userId = newOwnerId,
                                    title = "You're now an owner of \"${updatedWorld.title}\"",
                                    description = "You can now edit this world.",
                                    isRead = false,
                                    type = NotificationType.WORLD_UPDATE,
                                    worldId = updatedWorld.key,
                                    otherUserId = existingWorld.userId,
                                )
                                notificationRepository.create(note)
                            } catch (_: Throwable) { /* per-user best-effort */ }
                        }
                    } catch (_: Throwable) { /* ignore */ }
                }
                
                call.respond(updatedWorld)
            }

            // Delete world - requires authentication and ownership
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )

                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete

                val existingWorld = worldRepository.getById(id) ?: return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )

                if (existingWorld.userId != userId) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            message = "You don't have permission to delete this world",
                            code = HttpStatusCode.Forbidden.value
                        )
                    )
                }

                val deleted = worldRepository.delete(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete world", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
    }
}

private fun String.sanitizeWorldUrl(): String {
    return substringAfterLast('/')
}