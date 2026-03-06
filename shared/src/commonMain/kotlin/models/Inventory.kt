package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed
import models.annotations.CompositeIndex


/**
 * Inventory owners can be users or objects (containers, ground piles, etc.).
 */
@Serializable
enum class InventoryOwnerType {
    USER,
    OBJECT,
}

@Serializable
enum class ItemWearableLocation {
    Hair,
    Eyes,
    Ears,
    Neck,
    Body,
    Legs,
    Feet

}

/**
 * Represents an inventory that belongs to an owner (user or object).
 * Contains a list of InventoryItem keys in display order.
 */

@Serializable
data class Inventory(
    @field:Indexed(group = "owner", order = 0)
    var ownerType: InventoryOwnerType = InventoryOwnerType.USER,
    @field:Indexed(group = "owner", order = 1)
    var ownerId: String = "",
    var itemOrder: List<String> = mutableListOf(),
) : BaseModel()

/**
 * Represents an item type.
 * Contains definition metadata used by InventoryItems.
 */

@Serializable
data class Item(
    @field:Indexed
    var title: String = "",
    var description: String = "",
    var file: String = "",
    @field:Indexed
    var cost: Long = 0L,
    var assetIndex: Int = 0, // All asset files are 320x320 pixels, this index maps to the correct 32x32 cell
    var wearable: Boolean = false,
    var wearableSex: Sex? = null,
    var wearableLocation: ItemWearableLocation? = null,
    var wearableFile: String? = null,
    var wearableWeights: String? = null,
) : BaseModel()

/**
 * Represents an item entry within an inventory.
 */

@CompositeIndex(
    fields = [
        "inventoryId",
        "item",
    ],
)
@Serializable
data class InventoryItem(
    @field:Indexed
    var inventoryId: String = "",
    var quantity: Int = 1,
    var item: String = "",
) : BaseModel()
