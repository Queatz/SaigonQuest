package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ValidateTokenResponse

/**
 * Routes related to authentication helpers (e.g., token validation for other services).
 */
fun Routing.configureAuthRoutes() {
    route("/auth") {
        authenticate {
            // Returns the authenticated user's id if token is valid
            get("/validate") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                if (userId.isNullOrBlank()) {
                    return@get call.respond(HttpStatusCode.Unauthorized, ValidateTokenResponse(valid = false))
                }
                call.respond(HttpStatusCode.OK, ValidateTokenResponse(valid = true, userId = userId))
            }
        }
    }
}

