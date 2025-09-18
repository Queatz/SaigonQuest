package utils

import models.Costs

/**
 * Centralized costs provider to ensure a single source of truth for pricing.
 * Extend this as new cost fields are introduced.
 */
object CostsProvider {
    // For now, static pricing. In the future, this can be loaded from DB or configuration.
    private val current = Costs(
        worldBasePrice = 8_000_000L,
    )

    fun get(): Costs = current
}
