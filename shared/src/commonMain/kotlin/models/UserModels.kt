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
data class UnreadCountResponse(
    val unreadCount: Int = 0
)
