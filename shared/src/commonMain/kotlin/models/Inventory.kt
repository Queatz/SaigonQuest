package models

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

/**
 * Represents an inventory that belongs to a player.
 * Contains a list of items that the player has collected.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Inventory(
    var userId: String = "", // Reference to the user this inventory belongs to
    var itemOrder: List<String> = mutableListOf()
) : BaseModel()

/**
 * Represents an item type.
 * Contains a list of items that the player has collected.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Item(
    var title: String = "",
    var description: String = "",
    var file: String = "",
    var assetIndex: Int = 0, // All asset files are 320x320 pixels, this index maps to the correct 32x32 cell
    var wearable: Boolean = false,
    var wearableFile: String? = null,
) : BaseModel()

/**
 * Represents an item in a player's inventory.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class InventoryItem(
    var inventoryId: String = "",
    var quantity: Int = 1,
    var item: String = "",
) : BaseModel()
