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
import repository.InventoryRepository
import repository.ObjectRepository
import repository.WorldRepository
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
                call.respond(HttpStatusCode.Created, createdObject)
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
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete object", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
        
        // Collect inventory item - requires authentication
        authenticate {
            post("/collect/{id}") {
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get the object
                val obj = objectRepository.getById(id) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Object not found", code = HttpStatusCode.NotFound.value)
                )
                
                // Check if this is an inventory item
                if (!obj.isItem) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Object is not a collectible item", code = HttpStatusCode.BadRequest.value)
                    )
                }
                
                // Parse the item data
                val itemData = obj.data
                val item = try {
                    json.decodeFromString(Item.serializer(), itemData)
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Invalid item data", code = HttpStatusCode.InternalServerError.value)
                    )
                }
                
                // Add the item to the user's inventory
                val inventoryRepository = InventoryRepository()
                
                // Create an InventoryItem object with the item key
                val inventoryItem = models.InventoryItem(
                    item = item.key,
                    quantity = 1
                )
                
                try {
                    // Add the item to the inventory
                    val updatedInventory = inventoryRepository.addItemToInventory(userId, inventoryItem)
                    
                    // If we get here, the item was added successfully
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to add item to inventory: ${e.message}", code = HttpStatusCode.InternalServerError.value)
                    )
                }
                
                // Delete the object
                val deleted = objectRepository.delete(id)
                
                if (!deleted) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete object", code = HttpStatusCode.InternalServerError.value)
                    )
                }
                
                // Return success
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
