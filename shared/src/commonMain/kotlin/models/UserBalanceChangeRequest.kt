package models

import kotlinx.serialization.Serializable

@Serializable
data class UserBalanceChangeRequest(
    var userId: String = "",
    var type: UserBalanceChangeType = UserBalanceChangeType.Deposit,
    var status: UserBalanceChangeStatus = UserBalanceChangeStatus.Received,
    var notes: String = "",
    /** VND */
    var amount: Long = 0L,
    /** Image URLs (from content server) */
    var attachments: List<String> = emptyList()
) : BaseModel()

