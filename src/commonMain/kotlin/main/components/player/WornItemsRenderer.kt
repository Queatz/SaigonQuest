package main.components.player

import main.components.inventory.InventoryState
import main.components.world.SpriteSheet
import models.Item
import models.User

/**
 * Utility class for rendering worn items on a character
 */
object WornItemsRenderer {
    /**
     * Gets the list of sprite sheets for a specific user's worn items
     * 
     * @param user The user whose worn items to get
     * @return A list of sprite sheets for the user's worn items
     */
    fun getWornItemSpriteSheetsForUser(user: User?): List<SpriteSheet> {
        // If user is null, return empty list
        if (user == null) return emptyList()
        
        // Get the list of worn items from the user
        val wornItemIds = user.wornItems
        if (wornItemIds.isEmpty()) {
            return listOf(createDefaultApparelSpriteSheet())
        }
        
        // Get the corresponding Item objects for each worn item
        val wornItems = wornItemIds.mapNotNull { itemId ->
            // Find the item in the items map
            InventoryState.items[itemId]
        }
        
        // Filter for wearable items with valid wearableFile
        val wearableItems = wornItems.filter { item ->
            item.wearable && item.wearableFile != null
        }
        
        // Create sprite sheets for each wearable item
        val sheets = wearableItems.mapNotNull { item ->
            createSpriteSheetForWearableItem(item)
        }

        // If no valid wearable sheets resolved, fallback to default apparel
        return if (sheets.isEmpty()) {
            listOf(createDefaultApparelSpriteSheet())
        } else {
            sheets
        }
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
        
        // Create a sprite sheet for the wearable item
        return SpriteSheet(
            imageUrl = "/raw/data/spritesheets/${item.wearableFile}.png",
            cellWidthPx = 32,
            cellHeightPx = 48,
            columns = 8,
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
