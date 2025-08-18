package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
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
