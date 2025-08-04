package auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import models.ErrorResponse

/**
 * Utility functions for JWT token operations
 */
object JWTUtils {
    /**
     * Extract userId from JWT token without error handling
     * 
     * @param principal The JWT principal from which to extract the userId
     * @return The userId as a String, or null if not present or invalid
     */
    fun getUserId(principal: JWTPrincipal?): String? {
        return principal?.payload?.getClaim("userId")?.asString()
    }

    /**
     * Extract userId from JWT token with error handling
     * 
     * @param call The ApplicationCall to use for responding with an error if needed
     * @param principal The JWT principal from which to extract the userId
     * @param errorMessage Custom error message (optional)
     * @return The userId as a String, or null if extraction failed (after responding with error)
     */
    suspend fun getUserIdOrRespondWithError(
        call: ApplicationCall,
        principal: JWTPrincipal?,
        errorMessage: String = "Invalid token"
    ): String? {
        val userId = principal?.payload?.getClaim("userId")?.asString()
        if (userId == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(message = errorMessage, code = HttpStatusCode.Unauthorized.value)
            )
            return null
        }
        return userId
    }
}
