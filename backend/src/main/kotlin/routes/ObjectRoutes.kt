package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ErrorResponse
import models.Item
import models.Object
import models.WebSocketAction
import repository.InventoryRepository
import repository.ObjectRepository
import repository.WorldRepository
import routes.activeConnections
import utils.json

fun Routing.configureObjectRoutes() {
    val objectRepository = ObjectRepository()
    val worldRepository = WorldRepository()
    
    route("/object") {
        // Create new object - requires authentication
        authenticate {
            post {
                val obj = call.receive<Object>()
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Objects are not required to be related to a world
                // Only validate world ownership if a worldId is provided
                if (obj.worldId.isNotEmpty()) {
                    val world = worldRepository.getById(obj.worldId)
                    
                    // If worldId is provided but world doesn't exist, that's still valid
                    // as objects don't need to be related to worlds
                    
                    // If world exists, check if user has permission
                    if (world != null && world.userId != userId) {
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(
                                message = "You don't have permission to add objects to this world",
                                code = HttpStatusCode.Forbidden.value
                            )
                        )
                    }
                } else {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            message = "World is required",
                            code = HttpStatusCode.Forbidden.value
                        )
                    )
                }
                
                val createdObject = objectRepository.create(obj)

                // Broadcast ADD_OBJECT to all clients in the world
                try {
                    val broadcastMessage = routes.WebSocketMessage(
                        action = WebSocketAction.ADD_OBJECT,
                        data = json.encodeToString(createdObject),
                    )
                    val messageString = json.encodeToString(broadcastMessage)
                    routes.handlers.broadcastToWorldNoSession(obj.worldId, messageString, activeConnections)
                } catch (e: Exception) {
                    // Log but do not fail the request
                    println("[WS_BROADCAST] Failed to broadcast ADD_OBJECT (create): ${e.message}")
                }

                call.respond(HttpStatusCode.Created, createdObject)
            }

            // Drop inventory item into the world (deprecated)
            post("/drop") {
                return@post call.respond(
                    HttpStatusCode.Gone,
                    ErrorResponse(
                        message = "Deprecated endpoint. Use POST /inventory/transfer/drop",
                        code = HttpStatusCode.Gone.value,
                    )
                )
            }
        }
        
        // Get objects by worldId - no authentication required
        get {
            val worldId = call.request.queryParameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing worldId parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            val objects = objectRepository.getByWorldId(worldId)
            call.respond(objects)
        }
        
        // Get object by ID - no authentication required
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            val obj = objectRepository.getById(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Object not found", code = HttpStatusCode.NotFound.value)
                )
                
            call.respond(obj)
        }
        
        // Update object - requires authentication
        authenticate {
            put("/{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                val objUpdate = call.receive<Object>()
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Check if the object exists
                val existingObject = objectRepository.getById(id) ?: return@put call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Object not found", code = HttpStatusCode.NotFound.value)
                )
                
                // Objects are not required to be related to a world
                // Only validate world ownership if a worldId is provided
                if (existingObject.worldId.isNotEmpty()) {
                    val world = worldRepository.getById(existingObject.worldId)
                    
                    // If world exists, check if user has permission
                    if (world != null && world.userId != userId) {
                        return@put call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(
                                message = "You don't have permission to update objects in this world",
                                code = HttpStatusCode.Forbidden.value
                            )
                        )
                    }
                }
                
                val updatedObject = objectRepository.update(id, objUpdate)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Failed to update object", code = HttpStatusCode.NotFound.value)
                    )

                // Broadcast OBJECT_UPDATE to all clients in the world
                try {
                    val broadcastMessage = routes.WebSocketMessage(
                        action = WebSocketAction.OBJECT_UPDATE,
                        data = json.encodeToString(updatedObject),
                    )
                    val messageString = json.encodeToString(broadcastMessage)
                    routes.handlers.broadcastToWorldNoSession(updatedObject.worldId, messageString, activeConnections)
                } catch (e: Exception) {
                    // Log but do not fail the request
                    println("[WS_BROADCAST] Failed to broadcast OBJECT_UPDATE: ${e.message}")
                }
                
                call.respond(updatedObject)
            }
        }
        
        // Delete object - requires authentication
        authenticate {
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                
                // Check if the object exists
                val existingObject = objectRepository.getById(id) ?: return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Object not found", code = HttpStatusCode.NotFound.value)
                )
                
                // Objects are not required to be related to a world
                // Only validate world ownership if a worldId is provided
                if (existingObject.worldId.isNotEmpty()) {
                    val world = worldRepository.getById(existingObject.worldId)
                    
                    // If world exists, check if user has permission
                    if (world != null && world.userId != userId) {
                        return@delete call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(
                                message = "You don't have permission to delete objects in this world",
                                code = HttpStatusCode.Forbidden.value
                            )
                        )
                    }
                }
                
                val success = objectRepository.delete(id)
                if (success) {
                    // Broadcast REMOVE_OBJECT to all clients in the world
                    try {
                        val broadcastMessage = routes.WebSocketMessage(
                            action = WebSocketAction.REMOVE_OBJECT,
                            data = json.encodeToString(id),
                        )
                        val messageString = json.encodeToString(broadcastMessage)
                        routes.handlers.broadcastToWorldNoSession(existingObject.worldId, messageString, activeConnections)
                    } catch (e: Exception) {
                        // Log but do not fail the request
                        println("[WS_BROADCAST] Failed to broadcast REMOVE_OBJECT: ${e.message}")
                    }
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete object", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
    }
}
