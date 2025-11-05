package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed
import models.annotations.CompositeIndex

@Serializable
enum class UserBalanceHistoryEventType { Use, Withdraw, Deposit, Send, Receive }

@CompositeIndex(
    fields = [
        "userId",
        "createdAt",
    ],
)
@Serializable
data class UserBalanceHistory(
    @field:Indexed
    var userId: String = "",
    @field:Indexed
    var eventType: UserBalanceHistoryEventType = UserBalanceHistoryEventType.Deposit,
    var notes: String = "",
    /** VND */
    var changeAmount: Long = 0L,
    /** VND */
    var startBalance: Long = 0L,
    /** VND */
    var endBalance: Long = 0L,
    var attachments: List<String> = emptyList()
) : BaseModel()
