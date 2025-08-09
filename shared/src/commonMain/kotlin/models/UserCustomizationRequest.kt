package models

import kotlinx.serialization.Serializable

@Serializable
data class UserCustomizationRequest(
    val bodyType: String,
    val skinTone: Float,
    val eyeColor: String,
    val hairStyle: String,
)
