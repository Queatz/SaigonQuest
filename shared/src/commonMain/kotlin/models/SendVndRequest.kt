package models

import kotlinx.serialization.Serializable

@Serializable
data class SendVndRequest(
    val toUserId: String,
    /** VND */
    val amount: Long,
    val note: String? = null,
)
