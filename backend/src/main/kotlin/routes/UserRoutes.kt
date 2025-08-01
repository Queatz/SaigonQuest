package routes

import auth.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import models.User
import repository.UserRepository

// Data classes for request/response
@Serializable
data class VisitorRequest(val visitorId: String = "")

@Serializable
data class UpgradeRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val user: User, val token: String)

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
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Invalid token"
                )
                
                // Upgrade user
                val updatedUser = userRepository.upgradeToPlayer(userId, request.username, request.password)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        "User not found"
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
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Invalid token"
                )
                
                // Get user
                val user = userRepository.getById(userId) ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    "User not found"
                )
                
                call.respond(HttpStatusCode.OK, user)
            }
        }
    }
}
