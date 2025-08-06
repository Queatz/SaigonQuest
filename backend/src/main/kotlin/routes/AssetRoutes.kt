package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.Asset
import models.AssetType
import models.ErrorResponse
import repository.AssetRepository
import repository.UserRepository

/**
 * Configure routes for asset operations
 */
fun Routing.configureAssetRoutes() {
    val assetRepository = AssetRepository()
    val userRepository = UserRepository()
    
    route("/assets") {
        // Get all assets - no authentication required
        get {
            val assets = assetRepository.getAllAssets()
            call.respond(assets)
        }
        
        // Get assets by type - no authentication required
        get("/type/{type}") {
            val typeParam = call.parameters["type"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing type parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            // Convert string to AssetType enum
            val assetType = try {
                AssetType.valueOf(typeParam)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Invalid asset type", code = HttpStatusCode.BadRequest.value)
                )
            }
            
            val assets = assetRepository.getAssetsByType(assetType)
            call.respond(assets)
        }
        
        // Get asset by ID - no authentication required
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            val asset = assetRepository.getAssetByKey(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Asset not found", code = HttpStatusCode.NotFound.value)
                )
                
            call.respond(asset)
        }
        
        // Admin-only routes - require authentication
        authenticate {
            // Create new asset - admin only
            post {
                // Get user from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get user from database and check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                val asset = call.receive<Asset>()
                val createdAsset = assetRepository.saveAsset(asset)
                call.respond(HttpStatusCode.Created, createdAsset)
            }
            
            // Update asset - admin only
            put("/{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put
                
                // Get user from database and check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                // Check if the asset exists
                val existingAsset = assetRepository.getAssetByKey(id) ?: return@put call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Asset not found", code = HttpStatusCode.NotFound.value)
                )
                
                val assetUpdate = call.receive<Asset>()
                val updatedAsset = assetRepository.updateAsset(id, assetUpdate)
                    ?: return@put call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to update asset", code = HttpStatusCode.InternalServerError.value)
                    )
                    
                call.respond(updatedAsset)
            }
            
            // Delete asset - admin only
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                
                // Get user from database and check if admin
                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                // Check if the asset exists
                val existingAsset = assetRepository.getAssetByKey(id) ?: return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Asset not found", code = HttpStatusCode.NotFound.value)
                )
                
                val success = assetRepository.deleteAsset(id)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete asset", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }
    }
}
