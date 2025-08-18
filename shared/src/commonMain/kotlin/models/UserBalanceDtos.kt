package models

import kotlinx.serialization.Serializable

@Serializable
data class CreateBalanceChangeRequest(
    val type: UserBalanceChangeType,
    val amount: Long,
    val notes: String? = null,
    val attachments: List<String>? = null
)

@Serializable
data class AdminDecisionRequest(
    val note: String? = null
)

