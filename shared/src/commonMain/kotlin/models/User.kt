package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Clock

import kotlin.time.Instant


@Serializable
data class User(
    var username: String = "",
    var isPlayer: Boolean = false,
    var isAdmin: Boolean = false,
    var visitorId: String = "",
    @Transient
    var passwordHash: String = "",
    @Contextual
    var lastActive: Instant = Clock.System.now(),
    var yearOfBirth: Int? = null,
    var sex: Sex? = null,
    var language: String? = null,
    var status: String = "",
    // Status bars (0..10 representing 0 to 5 icons with half-steps)
    var health: Int = 10,
    var mind: Int = 10,
    var spirit: Int = 10,
    @Contextual
    var healthUpdatedAt: Instant = Clock.System.now(),
    @Contextual
    var mindUpdatedAt: Instant = Clock.System.now(),
    @Contextual
    var spiritUpdatedAt: Instant = Clock.System.now(),
    // Character customization properties
    var bodyType: String = "Normal", // Skinny, Normal, Fat
    var skinTone: Float = 0.5f,      // 0-1 tone slider
    var eyeColor: String = "Brown",  // Black, Blue, Brown, Green, Gray
    var hairStyle: String = "Short", // Short, Medium, Long
    // Worn items - list of item IDs that the user is currently wearing
    var wornItems: List<String> = mutableListOf()
) : BaseModel() {
    // Transient fields that won't be serialized
    @Transient
    var token: String = ""
}
