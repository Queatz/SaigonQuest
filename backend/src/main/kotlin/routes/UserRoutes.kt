package routes

import auth.JwtConfig
import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.AuthResponse
import models.ErrorResponse
import models.LanguageUpdateRequest
import models.LoginRequest
import models.ChangePasswordRequest
import models.StatusUpdateRequest
import models.UpgradeRequest
import models.User
import models.VisitorRequest
import models.WornItemsRequest
import models.UserCustomizationRequest
import repository.UserRepository

fun Routing.configureUserRoutes() {
    val userRepository = UserRepository()
    
    route("/user") {
        // Create a new visitor or get existing visitor by ID
        post("/visitor") {
            val request = call.receive<VisitorRequest>()
            val visitorId = request.visitorId
            
            val user = if (visitorId.isNotEmpty()) {
                // Try to get existing visitor
                userRepository.getByVisitorId(visitorId) ?: userRepository.createVisitor()
            } else {
                // Create new visitor
                userRepository.createVisitor()
            }
            
            // Generate JWT token
            val token = JwtConfig.generateToken(user.key)
            
            // Add token to response (not stored in DB)
            user.token = token
            
            call.respond(HttpStatusCode.OK, AuthResponse(user, token))
        }
        
        // Upgrade visitor to player
        authenticate {
            post("/upgrade") {
                val request = call.receive<UpgradeRequest>()
                
                // Validate password length
                if (request.password.length < 8) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Password must be at least 8 characters long"
                    )
                }
                
                // Check if username is already taken
                if (userRepository.getByUsername(request.username) != null) {
                    return@post call.respond(
                        HttpStatusCode.Conflict,
                        "Username is already taken"
                    )
                }
                
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Upgrade user
                val updatedUser = userRepository.upgradeToPlayer(
                    id = userId,
                    username = request.username,
                    password = request.password,
                    yearOfBirth = request.yearOfBirth,
                    sex = request.sex,
                    language = request.language
                )
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )
                
                // Generate new JWT token
                val token = JwtConfig.generateToken(updatedUser.key)
                
                // Add token to response (not stored in DB)
                updatedUser.token = token
                
                call.respond(HttpStatusCode.OK, AuthResponse(updatedUser, token))
            }
        }
        
        // Login with username and password
        post("/login") {
            val request = call.receive<LoginRequest>()
            
            // Authenticate user
            val user = userRepository.authenticate(request.username, request.password)
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Invalid username or password"
                )
            
            // Generate JWT token
            val token = JwtConfig.generateToken(user.key)
            
            // Add token to response (not stored in DB)
            user.token = token
            
            call.respond(HttpStatusCode.OK, AuthResponse(user, token))
        }
        
        // Get current user - requires authentication
        authenticate {
            get("/me") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                
                // Get user
                val user = userRepository.getById(userId) ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                )
                
                call.respond(HttpStatusCode.OK, user)
            }
            
            // Get user by ID - requires authentication
            get("/{userId}") {
                // Get the requested user ID from the path parameter
                val userId = call.parameters["userId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "User ID is required", code = HttpStatusCode.BadRequest.value)
                )
                
                // Get user
                val user = userRepository.getById(userId) ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                )
                
                // Remove sensitive information
                user.passwordHash = ""
                
                call.respond(HttpStatusCode.OK, user)
            }
            
            // Update worn items - requires authentication
            post("/worn-items") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get request body
                val request = call.receive<WornItemsRequest>()
                
                // Update worn items
                val updatedUser = userRepository.updateWornItems(userId, request.wornItems)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )
                
                call.respond(HttpStatusCode.OK, updatedUser)
            }
            
            // Update language preference - requires authentication
            post("/language") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get request body
                val request = call.receive<LanguageUpdateRequest>()
                
                // Update language
                val updatedUser = userRepository.updateLanguage(userId, request.language)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )
                
                call.respond(HttpStatusCode.OK, updatedUser)
            }
            
            // Update character customization - requires authentication
            post("/customization") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get request body
                val request = call.receive<UserCustomizationRequest>()
                
                // Update customization
                val updatedUser = userRepository.updateCustomization(
                    userId,
                    request.bodyType,
                    request.skinTone,
                    request.eyeColor,
                    request.hairStyle,
                )
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )
                
                call.respond(HttpStatusCode.OK, updatedUser)
            }
            
            // Update user status - requires authentication
            post("/status") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                
                // Get request body
                val request = call.receive<StatusUpdateRequest>()
                
                // Get the user
                val user = userRepository.getById(userId)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )
                
                // Update status
                user.status = request.status
                
                // Save the updated user
                val updatedUser = userRepository.update(userId, user)
                    ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to update user", code = HttpStatusCode.InternalServerError.value)
                    )
                
                call.respond(HttpStatusCode.OK, updatedUser)
            }

            // Update user status bars - requires authentication
            post("/status-bars") {
                // Get user ID from JWT token
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                // Get request body
                val request = call.receive<models.StatusBarsUpdateRequest>()

                // Get the user
                val user = userRepository.getById(userId)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )

                var updated = false
                val now = kotlin.time.Clock.System.now()

                request.health?.let { v ->
                    user.health = v.coerceIn(0, 10)
                    user.healthUpdatedAt = now
                    updated = true
                }
                request.mind?.let { v ->
                    user.mind = v.coerceIn(0, 10)
                    user.mindUpdatedAt = now
                    updated = true
                }
                request.spirit?.let { v ->
                    user.spirit = v.coerceIn(0, 10)
                    user.spiritUpdatedAt = now
                    updated = true
                }

                if (!updated) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "No fields to update", code = HttpStatusCode.BadRequest.value)
                    )
                }

                val updatedUser = userRepository.update(userId, user)
                    ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to update user", code = HttpStatusCode.InternalServerError.value)
                    )

                call.respond(HttpStatusCode.OK, updatedUser)
            }

            // Change password - requires authentication
            post("/change-password") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val request = call.receive<ChangePasswordRequest>()

                if (request.newPassword.length < 8) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Password must be at least 8 characters", code = HttpStatusCode.BadRequest.value)
                    )
                }

                // Verify current password
                val ok = userRepository.verifyPassword(userId, request.currentPassword)
                if (!ok) {
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(message = "Current password is incorrect", code = HttpStatusCode.Unauthorized.value)
                    )
                }

                val updatedUser = userRepository.updatePassword(userId, request.newPassword)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )

                // Generate new JWT token
                val token = JwtConfig.generateToken(updatedUser.key)
                updatedUser.token = token
                call.respond(HttpStatusCode.OK, AuthResponse(updatedUser, token))
            }
        }
    }
}
