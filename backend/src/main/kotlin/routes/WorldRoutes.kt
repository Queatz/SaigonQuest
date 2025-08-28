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
import repository.WorldRepository
import repository.UserRepository
import repository.FriendRepository
import repository.InventoryRepository

fun Routing.configureWorldRoutes() {
    val worldRepository = WorldRepository()
    val userRepository = UserRepository()
    val friendRepository = FriendRepository()
    val inventoryRepository = InventoryRepository()

    fun checkAccess(call: ApplicationCall, world: World): ErrorResponse? {
        val principal = call.principal<JWTPrincipal>()
        val requesterUserId = JWTUtils.getUserId(principal)

        // Owner is always allowed
        if (world.userId.isNotEmpty() && requesterUserId == world.userId) {
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

        // Friends of the owner only
        if (world.allowFriendsOfOwners) {
            if (requesterUserId == null) {
                return ErrorResponse(
                    message = "This world is locked. Keep exploring!",
                    code = HttpStatusCode.Forbidden.value,
                    details = "ACCESS_REQUIRES_FRIEND"
                )
            }
            val isFriend = friendRepository.existsFriendship(requesterUserId, world.userId)
            if (isFriend) {
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
                
                // Check if the world belongs to the user
                val existingWorld = worldRepository.getById(id) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )
                if (existingWorld.userId != userId) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden, 
                        ErrorResponse(
                            message = "You don't have permission to update this world",
                            code = HttpStatusCode.Forbidden.value
                        )
                    )
                }
                
                val updatedWorld = worldRepository.update(id, worldUpdate)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Failed to update world", code = HttpStatusCode.NotFound.value)
                    )
                    
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
