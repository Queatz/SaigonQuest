package main.components.player

import main.components.inventory.InventoryState
import main.components.world.CharacterDirection
import main.components.world.PlayerActivity
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager

object WornItemsRefresher {
    fun refresh(
        manager: WorldObjectManager,
        handle: WorldObjectHandle,
        direction: CharacterDirection,
        activity: PlayerActivity?,
    ): WorldObjectHandle? {
        val currentPosition = handle.position
        handle.remove()
        val spriteSheet = SpriteSheet.createPlayerSpriteSheetForBodyType(
            InventoryState.currentUser?.sex,
            InventoryState.currentUser?.bodyType,
        )
        return manager.createCharacter(
            spriteSheet = spriteSheet,
            direction = direction,
            position = currentPosition,
            activity = activity,
            additionalSpriteSheets = emptyList(),
            user = InventoryState.currentUser
        )
    }
}
