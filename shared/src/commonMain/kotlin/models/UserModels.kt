package models

import kotlinx.serialization.Serializable

/**
 * Data classes for user-related API requests and responses
 */

@Serializable
data class VisitorRequest(val visitorId: String = "")

@Serializable
data class UpgradeRequest(
    val username: String,
    val password: String,
    val yearOfBirth: Int? = null,
    val sex: Sex? = null,
    val language: String? = null
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val user: User, val token: String)

@Serializable
data class WornItemsRequest(val wornItems: List<String>)

@Serializable
data class LanguageUpdateRequest(val language: String)

@Serializable
data class StatusUpdateRequest(val status: String)

@Serializable
data class StatusBarsUpdateRequest(
    val health: Int? = null,
    val mind: Int? = null,
    val spirit: Int? = null,
)

@Serializable
data class UnreadCountResponse(
    val unreadCount: Int = 0
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/**
 * Validates a username: no spaces, no special characters.
 * Allowed: a-z, A-Z, 0-9, and underscores.
 */
fun isValidUsername(username: String): Boolean {
    if (username.isBlank()) return false
    val regex = Regex("^[a-zA-Z0-9_]+$")
    return regex.matches(username)
}
