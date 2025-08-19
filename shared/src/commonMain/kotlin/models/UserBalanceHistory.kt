package models

import kotlinx.serialization.Serializable

@Serializable
enum class UserBalanceHistoryEventType { Use, Withdraw, Deposit, Give }

@Serializable
data class UserBalanceHistory(
    var userId: String = "",
    var eventType: UserBalanceHistoryEventType = UserBalanceHistoryEventType.Deposit,
    var notes: String = "",
    /** VND */
    var changeAmount: Long = 0L,
    /** VND */
    var startBalance: Long = 0L,
    /** VND */
    var endBalance: Long = 0L
) : BaseModel()
