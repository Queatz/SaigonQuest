package models

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

/**
 * Inventory owners can be users or objects (containers, ground piles, etc.).
 */
@Serializable
enum class InventoryOwnerType {
    USER,
    OBJECT,
}

/**
 * Represents an inventory that belongs to an owner (user or object).
 * Contains a list of InventoryItem keys in display order.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Inventory(
    var ownerType: InventoryOwnerType = InventoryOwnerType.USER,
    var ownerId: String = "",
    var itemOrder: List<String> = mutableListOf(),
) : BaseModel()

/**
 * Represents an item type.
 * Contains definition metadata used by InventoryItems.
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
 * Represents an item entry within an inventory.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class InventoryItem(
    var inventoryId: String = "",
    var quantity: Int = 1,
    var item: String = "",
) : BaseModel()
