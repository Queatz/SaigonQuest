package models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import models.annotations.Indexed
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes

@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["requesterId", "addresseeId"]),
        CompositeIndex(fields = ["addresseeId", "status"]),
    ],
)
@Serializable
data class FriendRequest(
    @field:Indexed
    var requesterId: String = "",
    var requesterUsername: String = "",
    @field:Indexed
    var addresseeId: String = "",
    @field:Indexed
    var status: FriendRequestStatus = FriendRequestStatus.Pending,
    @field:Indexed(sparse = true)
    var decidedAt: Instant? = null,
) : BaseModel()
