package models

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class FriendRequest(
    var requesterId: String = "",
    var requesterUsername: String = "",
    var addresseeId: String = "",
    var status: FriendRequestStatus = FriendRequestStatus.Pending,
    var decidedAt: Instant? = null,
) : BaseModel()
