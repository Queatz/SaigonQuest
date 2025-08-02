package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents an inventory that belongs to a player.
 * Contains a list of items that the player has collected.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Inventory(
    var userId: String = "", // Reference to the user this inventory belongs to
    var items: MutableList<InventoryItem> = mutableListOf()
) : BaseModel()

/**
 * Represents an item in a player's inventory.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class InventoryItem(
    var name: String = "",
    var description: String = "",
    var imageUrl: String = "",
    var quantity: Int = 1,
    var type: String = "", // Type of item (e.g., "weapon", "consumable", "quest")
    var properties: Map<String, String> = mapOf(), // Additional properties specific to the item type
    @Contextual
    var acquiredAt: Instant = Clock.System.now()
) : BaseModel()

