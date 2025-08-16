package models

import kotlinx.serialization.Serializable

@Serializable
data class ValidateTokenResponse(
    val valid: Boolean,
    val userId: String? = null
)

