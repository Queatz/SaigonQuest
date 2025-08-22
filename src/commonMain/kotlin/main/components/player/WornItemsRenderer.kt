package main.components.player

import main.components.world.SpriteSheet
import models.Item
import models.User

/**
 * Utility class for rendering worn items on a character
 */
object WornItemsRenderer {
    /**
     * Gets the list of sprite sheets for a specific user's worn items using a map of items.
     */
    fun getWornItemSpriteSheetsForUser(
        user: User?,
        itemsById: Map<String, Item>,
    ): List<SpriteSheet> = getWornItemSpriteSheetsForUser(user) { id -> itemsById[id] }

    /**
     * Gets the list of sprite sheets for a specific user's worn items using a custom resolver.
     * The resolver should return the Item for a given itemId or null if not available.
     */
    fun getWornItemSpriteSheetsForUser(
        user: User?,
        resolver: (String) -> Item?,
    ): List<SpriteSheet> {
        if (user == null) return emptyList()
        val wornItemIds = user.wornItems
        if (wornItemIds.isEmpty()) return listOf(createDefaultApparelSpriteSheet())

        val wornItems: List<Item> = wornItemIds.mapNotNull { itemId -> resolver(itemId) }

        val wearableItems = wornItems.filter { item -> item.wearable && item.wearableFile != null }
        val sheets = wearableItems.mapNotNull { item -> createSpriteSheetForWearableItem(item) }

        return sheets.ifEmpty { listOf(createDefaultApparelSpriteSheet()) }
    }
    
    /**
     * Creates a sprite sheet for a wearable item
     * 
     * @param item The wearable item
     * @return A sprite sheet for the wearable item, or null if the item is not wearable or has no wearableFile
     */
    private fun createSpriteSheetForWearableItem(item: Item): SpriteSheet? {
        // Check if the item is wearable and has a wearableFile
        if (!item.wearable || item.wearableFile == null) return null

        // Parse optional wind weights from the item (CSV of 25 numbers, row-major)
        fun parseWeights(input: String?): FloatArray? {
            if (input.isNullOrBlank()) return null
            val parts = input.split(',',' ',';','\n','\t').mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            if (parts.size < 25) return null
            val nums = parts.take(25).mapNotNull { it.toDoubleOrNull()?.toFloat() }
            if (nums.size != 25) return null
            return FloatArray(25) { idx -> nums[idx] }
        }
        val weights = parseWeights(item.wearableWeights)
        
        // Create a sprite sheet for the wearable item
        return SpriteSheet(
            imageUrl = "/raw/data/spritesheets/${item.wearableFile}.png",
            cellWidthPx = 32,
            cellHeightPx = 48,
            columns = 8,
            windEnabled = true,
            windWeights5x5 = weights,
            // Typical apparel (dresses, capes): pinned near top, free at bottom
            windFlipY = true,
            windIntensityScale = 0.05f,
            directionOrder = listOf(
                main.components.world.CharacterDirection.S,
                main.components.world.CharacterDirection.SE,
                main.components.world.CharacterDirection.E,
                main.components.world.CharacterDirection.NE,
                main.components.world.CharacterDirection.N,
                main.components.world.CharacterDirection.NW,
                main.components.world.CharacterDirection.W,
                main.components.world.CharacterDirection.SW
            )
        )
    }

    // Default apparel layer used when no worn items are specified or resolved
    private fun createDefaultApparelSpriteSheet(): SpriteSheet {
        return SpriteSheet(
            imageUrl = "/raw/data/spritesheets/Base Apparel Sprite Sheet.png",
            cellWidthPx = 32,
            cellHeightPx = 48,
            columns = 8,
            windEnabled = true,
            windFlipY = true,
            windIntensityScale = 0.05f,
            directionOrder = listOf(
                main.components.world.CharacterDirection.S,
                main.components.world.CharacterDirection.SE,
                main.components.world.CharacterDirection.E,
                main.components.world.CharacterDirection.NE,
                main.components.world.CharacterDirection.N,
                main.components.world.CharacterDirection.NW,
                main.components.world.CharacterDirection.W,
                main.components.world.CharacterDirection.SW
            )
        )
    }
}
