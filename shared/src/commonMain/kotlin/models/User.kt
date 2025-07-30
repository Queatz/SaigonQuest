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
    var visitorId: String = "",
    var passwordHash: String = "",
    @Contextual
    var lastActive: Instant = Clock.System.now(),
    var yearOfBirth: Int? = null,
    var sex: String? = null
) : BaseModel() {
    // Transient fields that won't be serialized
    @Transient
    var token: String = ""
}
