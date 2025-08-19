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
import repository.ItemRepository
import repository.UserRepository

fun Routing.configureItemRoutes() {
    val itemRepository = ItemRepository()
    val userRepository = UserRepository()
    
    route("/items") {
        // Get all items - no authentication required
        get {
            val items = itemRepository.getAllItems()
            call.respond(items)
        }
        
        // Get all items (admin) - requires authentication
        authenticate {
            get("/admin") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get

                // Check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val items = itemRepository.getAllItems()
                call.respond(items)
            }
        }
        
        // Get item by ID - no authentication required
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            val item = itemRepository.getItemById(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Item not found", code = HttpStatusCode.NotFound.value)
                )
                
            call.respond(item)
        }
        
        // Get items by IDs - no authentication required
        post("/batch") {
            val itemIds = call.receive<List<String>>()
            
            if (itemIds.isEmpty()) {
                return@post call.respond(emptyList<Item>())
            }
            
            val items = itemRepository.getItemsByIds(itemIds)
            call.respond(items)
        }
        
        // Create new item - requires authentication
        authenticate {
            post {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                // Check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val item = call.receive<Item>()
                val createdItem = itemRepository.create(item)
                call.respond(HttpStatusCode.Created, createdItem)
            }
        }
        
        // Update item - requires authentication
        authenticate {
            put("/{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )

                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put

                // Check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }

                // Check if the item exists
                val existingItem = itemRepository.getItemById(id) ?: return@put call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Item not found", code = HttpStatusCode.NotFound.value)
                )

                val itemUpdate = call.receive<Item>()
                val updatedItem = itemRepository.update(id, itemUpdate)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Failed to update item", code = HttpStatusCode.NotFound.value)
                    )

                call.respond(updatedItem)
            }
        }
        
        // Delete item - requires authentication
        authenticate {
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete

                // Check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                // Check if the item exists
                val existingItem = itemRepository.getItemById(id) ?: return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Item not found", code = HttpStatusCode.NotFound.value)
                )
                
                val success = itemRepository.delete(id)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete item", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
    }
}
