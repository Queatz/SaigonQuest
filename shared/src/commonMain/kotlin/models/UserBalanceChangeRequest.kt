package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed
import models.annotations.CompositeIndex

@CompositeIndex(
    fields = [
        "userId",
        "createdAt",
    ],
)
@Serializable
data class UserBalanceChangeRequest(
    @field:Indexed
    var userId: String = "",
    var type: UserBalanceChangeType = UserBalanceChangeType.Deposit,
    @field:Indexed
    var status: UserBalanceChangeStatus = UserBalanceChangeStatus.Received,
    var notes: String = "",
    /** VND */
    var amount: Long = 0L,
    /** Image URLs (from content server) */
    var attachments: List<String> = emptyList(),
    /** Whether this request has been resolved (approved/denied). */
    @field:Indexed
    var resolved: Boolean = false
) : BaseModel()
