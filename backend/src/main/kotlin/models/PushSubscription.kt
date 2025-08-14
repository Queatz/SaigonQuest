package models

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@OptIn(ExperimentalTime::class)
data class PushSubscription(
    var userId: String = "",
    var endpoint: String = "",
    var p256dh: String = "",
    var auth: String = "",
    var userAgent: String = "",
    var lastSeenAt: Instant = Clock.System.now()
) : BaseModel()

@Serializable
data class PushSubscriptionRequest(
    val endpoint: String,
    val keys: Keys,
    val ua: String? = "",
    val appVersion: String? = null
) {
    @Serializable
    data class Keys(val p256dh: String, val auth: String)
}

@Serializable
data class PushPublicKeyResponse(val publicKey: String)
