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
import models.Inventory
import models.InventoryItem
import repository.InventoryRepository
import repository.UserRepository

fun Routing.configureInventoryRoutes() {
    val inventoryRepository = InventoryRepository()
    val userRepository = UserRepository()
    
    route("/inventory") {
        // Get current user's inventory - requires authentication
        authenticate {
            get {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                // Get or create the user's inventory
                val inventory = inventoryRepository.getByUserId(userId) 
                    ?: inventoryRepository.createInventoryForUser(userId)
                
                call.respond(inventory)
            }
        }
        
        // Get inventory by user ID - requires authentication
        authenticate {
            get("/user/{userId}") {
                val userId = call.parameters["userId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing userId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Check if the user exists
                val user = userRepository.getById(userId) ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                )
                
                // Get or create the user's inventory
                val inventory = inventoryRepository.getByUserId(userId) 
                    ?: inventoryRepository.createInventoryForUser(userId)
                
                call.respond(inventory)
            }
        }
        
        // Add item to inventory - requires authentication
        authenticate {
            post("/item") {
                val inventoryItem = call.receive<InventoryItem>()
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Add the item to the user's inventory
                val updatedInventory = inventoryRepository.addItemToInventory(userId, inventoryItem)
                
                call.respond(HttpStatusCode.Created, updatedInventory)
            }
        }
        
        // Remove item from inventory - requires authentication
        authenticate {
            delete("/item/{itemId}") {
                val itemId = call.parameters["itemId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing itemId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                
                // Remove the item from the user's inventory
                val updatedInventory = inventoryRepository.removeItemFromInventory(userId, itemId)
                
                call.respond(updatedInventory)
            }
        }
        
        // Update item in inventory - requires authentication
        authenticate {
            put("/item/{itemId}") {
                val itemId = call.parameters["itemId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing itemId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                val inventoryItem = call.receive<InventoryItem>()
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Update the item in the user's inventory
                val updatedInventory = inventoryRepository.updateItemInInventory(userId, itemId, inventoryItem)
                
                call.respond(updatedInventory)
            }
        }
        
        // Get inventory items by IDs - requires authentication
        authenticate {
            post("/items/batch") {
                val itemIds = call.receive<List<String>>()
                
                if (itemIds.isEmpty()) {
                    return@post call.respond(emptyList<InventoryItem>())
                }
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get the inventory items
                val inventoryItems = itemIds.mapNotNull { inventoryRepository.getInventoryItemById(it) }
                
                call.respond(inventoryItems)
            }
        }
    }
}
