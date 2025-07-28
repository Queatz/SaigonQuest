package models

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val alive: Boolean = true
)
