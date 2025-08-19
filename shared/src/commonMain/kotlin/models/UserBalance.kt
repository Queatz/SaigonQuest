package models

import kotlinx.serialization.Serializable

@Serializable
data class UserBalance(
    var userId: String = "",
    /** VND (Vietnamese Dong) */
    var balance: Long = 0L
) : BaseModel()

@Serializable
enum class UserBalanceChangeType { Deposit, Withdraw, Use }

@Serializable
enum class UserBalanceChangeStatus { Received, Complete, Denied, CancelledByUser }
