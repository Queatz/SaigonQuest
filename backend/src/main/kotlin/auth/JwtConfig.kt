package auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

object JwtConfig {
    // Secret should be stored in environment variables in production
    private const val SECRET = "saigon-quest-secret-key"
    private const val ISSUER = "saigon-quest"
    private const val AUDIENCE = "saigon-quest-users"
    private const val VALIDITY_IN_MS = 36_000_00 * 24 // 24 hours
    
    private val algorithm = Algorithm.HMAC256(SECRET)
    
    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()
    
    /**
     * Generates a JWT token for a specific user
     */
    fun generateToken(userId: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_IN_MS))
        .sign(algorithm)
    
    /**
     * Configures JWT authentication for Ktor
     */
    fun configureKtorAuth(config: AuthenticationConfig) {
        config.jwt {
            verifier(verifier)
            realm = "saigon-quest"
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId.isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
