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
import models.Inventory
import models.InventoryItem
import models.Object
import models.Position
import models.WebSocketAction
import repository.InventoryRepository
import repository.ObjectRepository
import repository.UserRepository
import repository.WorldRepository
import routes.activeConnections
import routes.handlers.WebSocketUtils
import utils.json

fun Routing.configureInventoryRoutes() {
    val inventoryRepository = InventoryRepository()
    val userRepository = UserRepository()
    val objectRepository = ObjectRepository()
    val worldRepository = WorldRepository()

    @Serializable
    data class TransferDropRequest(
        val itemKey: String,
        val quantity: Int,
        val worldId: String,
        val position: Position,
        val title: String? = null,
    )

    @Serializable
    data class TransferPickupRequest(
        val objectId: String,
        val quantity: Int? = null,
    )

    @Serializable
    data class TransferPickupResponse(
        val objectDeleted: Boolean,
        val obj: Object? = null,
    )

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

        // Update item order in inventory - requires authentication
        authenticate {
            put("/item-order") {
                // Parse the request body to get the itemOrder list
                val requestBody = call.receive<Map<String, List<String>>>()
                val itemOrder = requestBody["itemOrder"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing itemOrder in request body", code = HttpStatusCode.BadRequest.value)
                )

                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put

                // Update the item order in the user's inventory
                val updatedInventory = inventoryRepository.updateInventoryItemOrder(userId, itemOrder)

                call.respond(updatedInventory)
            }
        }

        // Get or create object inventory - requires authentication
        authenticate {
            get("/object/{objectId}") {
                val objectId = call.parameters["objectId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing objectId parameter", code = HttpStatusCode.BadRequest.value)
                )

                val inventory = inventoryRepository.getByOwner(models.InventoryOwnerType.OBJECT, objectId)
                    ?: inventoryRepository.createInventoryForObject(objectId)

                call.respond(inventory)
            }
        }

        // Server-authoritative transfer endpoints
        route("/transfer") {
            // Drop from USER to world OBJECT
            authenticate {
                post("/drop") {
                    val req = call.receive<TransferDropRequest>()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                    if (req.quantity <= 0) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(message = "Quantity must be positive", code = HttpStatusCode.BadRequest.value)
                        )
                    }
                    if (req.itemKey.isBlank() || req.worldId.isBlank()) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(message = "Missing itemKey or worldId", code = HttpStatusCode.BadRequest.value)
                        )
                    }

                    // Permission check: user must own the world if it exists
                    val world = worldRepository.getById(req.worldId)
                    if (world != null && world.userId != userId) {
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(
                                message = "You don't have permission to add objects to this world",
                                code = HttpStatusCode.Forbidden.value,
                            )
                        )
                    }

                    // Create the container object first using inventories as the sole source of truth
                    val obj = Object(
                        title = req.title ?: req.itemKey,
                        data = "",
                        worldId = req.worldId,
                        position = req.position,
                        collection = "",
                        file = "",
                    )

                    val created = objectRepository.create(obj)

                    // Perform transfer from USER -> OBJECT using inventories
                    try {
                        inventoryRepository.transfer(
                            fromType = models.InventoryOwnerType.USER,
                            fromId = userId,
                            toType = models.InventoryOwnerType.OBJECT,
                            toId = created.key,
                            itemKey = req.itemKey,
                            quantity = req.quantity,
                        )
                    } catch (e: Throwable) {
                        // Clean up the created object on failure
                        objectRepository.delete(created.key)
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(message = e.message ?: "Failed to transfer item to object", code = HttpStatusCode.BadRequest.value)
                        )
                    }

                    // Broadcast ADD_OBJECT to all clients in the world (canonical key only)
                    try {
                        val broadcastMessage = routes.WebSocketMessage(
                            action = WebSocketAction.ADD_OBJECT,
                            data = json.encodeToString(created),
                        )
                        val messageString = json.encodeToString(broadcastMessage)
                        routes.handlers.broadcastToWorldNoSession(created.worldId, messageString, activeConnections)
                    } catch (e: Throwable) {
                        // Log but do not fail the request
                        println("[WS_BROADCAST] Failed to broadcast ADD_OBJECT: ${e.message}")
                    }

                    call.respond(HttpStatusCode.Created, created)
                }
            }

            // Pickup from OBJECT to USER
            authenticate {
                post("/pickup") {
                    val principal = call.principal<JWTPrincipal>()
                    if (principal == null) {
                        return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse(
                                message = "Log in to pick up items",
                                code = HttpStatusCode.Unauthorized.value,
                            )
                        )
                    }
                    val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                    val req = call.receive<TransferPickupRequest>()

                    val obj = objectRepository.getById(req.objectId) ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Object not found", code = HttpStatusCode.NotFound.value)
                    )

                    // Use object's inventory as the sole source of truth
                    val objInventory = inventoryRepository.getByOwner(models.InventoryOwnerType.OBJECT, obj.key)
                        ?: inventoryRepository.createInventoryForObject(obj.key)

                    val items = inventoryRepository.getInventoryItemsByInventoryId(objInventory.key)
                    if (items.isEmpty()) {
                        // No items remain; delete the object
                        objectRepository.delete(obj.key)
                        try {
                            val broadcastMessage = routes.WebSocketMessage(
                                action = WebSocketAction.REMOVE_OBJECT,
                                data = json.encodeToString(obj.key),
                            )
                            val messageString = json.encodeToString(broadcastMessage)

                            // Broadcast only to the canonical world key
                            routes.handlers.broadcastToWorldNoSession(obj.worldId, messageString, activeConnections)
                        } catch (e: Throwable) {
                            println("[WS_BROADCAST] Failed to broadcast REMOVE_OBJECT (empty inventory): ${e.message}")
                        }
                        return@post call.respond(TransferPickupResponse(objectDeleted = true, obj = null))
                    }

                    // Derive itemKey from inventory contents (first item type)
                    val itemKey = items.first().item
                    val available = items.filter { it.item == itemKey }.sumOf { it.quantity }
                    val qty = req.quantity?.takeIf { it > 0 }?.coerceAtMost(available) ?: available

                    try {
                        inventoryRepository.transfer(
                            fromType = models.InventoryOwnerType.OBJECT,
                            fromId = obj.key,
                            toType = models.InventoryOwnerType.USER,
                            toId = userId,
                            itemKey = itemKey,
                            quantity = qty,
                        )
                    } catch (e: Throwable) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(message = e.message ?: "Failed to transfer from object", code = HttpStatusCode.BadRequest.value)
                        )
                    }

                    // Re-evaluate object inventory after transfer
                    val itemsAfter = inventoryRepository.getInventoryItemsByInventoryId(objInventory.key)
                    if (itemsAfter.isEmpty()) {
                        objectRepository.delete(obj.key)
                        try {
                            val broadcastMessage = routes.WebSocketMessage(
                                action = WebSocketAction.REMOVE_OBJECT,
                                data = json.encodeToString(obj.key),
                            )
                            val messageString = json.encodeToString(broadcastMessage)
                            // Broadcast only to the canonical world key
                            routes.handlers.broadcastToWorldNoSession(obj.worldId, messageString, activeConnections)
                        } catch (e: Throwable) {
                            println("[WS_BROADCAST] Failed to broadcast REMOVE_OBJECT: ${e.message}")
                        }
                        return@post call.respond(TransferPickupResponse(objectDeleted = true, obj = null))
                    } else {
                        // Broadcast OBJECT_UPDATE (clients render from inventories)
                        try {
                            val broadcastMessage = routes.WebSocketMessage(
                                action = WebSocketAction.OBJECT_UPDATE,
                                data = json.encodeToString(obj),
                            )
                            val messageString = json.encodeToString(broadcastMessage)
                            // Broadcast only to the canonical world key
                            routes.handlers.broadcastToWorldNoSession(obj.worldId, messageString, activeConnections)
                        } catch (e: Throwable) {
                            println("[WS_BROADCAST] Failed to broadcast OBJECT_UPDATE: ${e.message}")
                        }
                        return@post call.respond(TransferPickupResponse(objectDeleted = false, obj = obj))
                    }
                }
            }
        }
    }
}
