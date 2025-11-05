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
data class UserBalance(
    @field:Indexed
    var userId: String = "",
    /** VND (Vietnamese Dong) */
    var balance: Long = 0L
) : BaseModel()

@Serializable
enum class UserBalanceChangeType { Deposit, Withdraw, Use }

@Serializable
enum class UserBalanceChangeStatus { Received, Complete, Denied, CancelledByUser }
