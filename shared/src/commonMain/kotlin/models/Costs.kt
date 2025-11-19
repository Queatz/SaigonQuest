package models

import kotlinx.serialization.Serializable

@Serializable
data class Costs(
    /** VND */
    val worldBasePrice: Long = 0L,
    /** VND */
    val objectBasePrice: Long = 0L,
    /** VND */
    val itemBasePrice: Long = 0L,
    /** VND */
    val wearableItemBasePrice: Long = 0L,
    /** VND */
    val nodeBasePrice: Long = 0L,
)
