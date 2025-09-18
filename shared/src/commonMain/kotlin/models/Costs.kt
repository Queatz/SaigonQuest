package models

import kotlinx.serialization.Serializable

@Serializable
data class Costs(
    /** VND */
    val worldBasePrice: Long = 0L,
)
