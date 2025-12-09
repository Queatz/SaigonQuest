package utils

import models.Costs
import models.UserRequestType
import repository.WorldRepository

/**
 * Centralized costs provider to ensure a single source of truth for pricing.
 * Extend this as new cost fields are introduced.
 */
object CostsProvider {
    // For now, static pricing. In the future, this can be loaded from DB or configuration.
    private val base = Costs(
        worldBasePrice = 10_000_000L,
        objectBasePrice = 1_000_000L,
        itemBasePrice = 100_000L,
        wearableItemBasePrice = 500_000L,
        nodeBasePrice = 2_500_000L,
    )

    private val worldRepository = WorldRepository()

    fun get(): Costs {
        // First world is free: if there are no worlds yet, worldBasePrice should be 0
        val hasAnyWorld: Boolean = try {
            worldRepository.existsAny()
        } catch (t: Throwable) {
            // On error, fall back to paid to avoid unintended free creation
            true
        }
        return if (!hasAnyWorld) base.copy(worldBasePrice = 0L) else base
    }

    /**
     * Fee charged when a user submits a request for a new type.
     * For now we mirror the respective base prices.
     */
    fun userRequestFee(type: UserRequestType): Long = when (type) {
        UserRequestType.Item -> base.itemBasePrice
        UserRequestType.Wearable -> base.wearableItemBasePrice
        UserRequestType.Object -> base.objectBasePrice
        UserRequestType.Node -> base.nodeBasePrice
    }
}
