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

fun Routing.configureWorldRoutes() {
    val worldRepository = WorldRepository()
    
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
        
        // Get world by URL - no authentication required
        get("/url") {
            val url = call.request.queryParameters["url"] ?: ""
            
            val world = worldRepository.getByUrl(url)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "World not found", code = HttpStatusCode.NotFound.value)
                )
                
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
                
            call.respond(world)
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
        }
    }
}
