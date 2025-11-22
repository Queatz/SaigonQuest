package utils

import models.Costs
import models.UserRequestType

/**
 * Centralized costs provider to ensure a single source of truth for pricing.
 * Extend this as new cost fields are introduced.
 */
object CostsProvider {
    // For now, static pricing. In the future, this can be loaded from DB or configuration.
    private val current = Costs(
        worldBasePrice = 10_000_000L,
        objectBasePrice = 1_000_000L,
        itemBasePrice = 100_000L,
        wearableItemBasePrice = 500_000L,
        nodeBasePrice = 2_500_000L,
    )

    fun get(): Costs = current

    /**
     * Fee charged when a user submits a request for a new type.
     * For now we mirror the respective base prices.
     */
    fun userRequestFee(type: UserRequestType): Long = when (type) {
        UserRequestType.Item -> current.itemBasePrice
        UserRequestType.Wearable -> current.wearableItemBasePrice
        UserRequestType.Object -> current.objectBasePrice
        UserRequestType.Node -> current.nodeBasePrice
    }
}
