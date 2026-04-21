package main.components.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import lib.CreateBoxOptions
import lib.Mesh
import lib.MeshBuilder
import lib.Scene
import lib.Vector3
import main.api.UserApi
import main.components.inventory.InventoryState
import main.components.world.setIncullable
import main.api.WebSocketApi
import main.api.addInventoryChangedListener
import main.components.world.CharacterDirection
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager

/**
 * Sets up the local player: character, collider, HUD username, and inventory hooks.
 */
object PlayerInitializer {
    fun create(
        scene: Scene,
        startPosition: Vector3,
        worldObjectManager: WorldObjectManager,
        playerDirection: CharacterDirection,
        hud: SelfHudManager,
        coroutineScope: CoroutineScope,
        onRefreshWornItems: () -> Unit,
        onSetCollider: (Mesh) -> Unit,
        onSetPlayerHandle: (WorldObjectHandle?) -> Unit,
        onRegisterInventoryCallback: ((() -> Unit) -> Unit),
    ) {
        // Create character
        val spriteSheet = SpriteSheet.createPlayerSpriteSheetForBodyType(
            InventoryState.currentUser?.sex,
            InventoryState.currentUser?.bodyType,
        )

        val playerHandle = worldObjectManager.createCharacter(
            spriteSheet = spriteSheet,
            direction = playerDirection,
            position = startPosition,
            additionalSpriteSheets = emptyList(),
            user = InventoryState.currentUser
        )
        
        // Exclude the player from being culled by the performance engine
        playerHandle?.node?.setIncullable(true)
        
        onSetPlayerHandle(playerHandle)

        // Create invisible collider box
        val collider = MeshBuilder.CreateBox(
            "player_collision",
            object : CreateBoxOptions {
                override val width = 0.05f
                override val height = 1.0f
                override val depth = 0.25f
            },
            scene
        )
        collider.isVisible = false
        collider.position = startPosition
        onSetCollider(collider)

        // Initialize HUD username using the live player position (not the potentially stale startPosition)
        val initialPos = playerHandle?.position ?: collider.position
        hud.updateUsername(initialPos, "Player")
        coroutineScope.launch {
            try {
                val user = UserApi.getCurrentUser()
                // Use the current player position to place the username correctly even if we've snapped or moved
                val currentPos = playerHandle?.position ?: collider.position
                hud.updateUsername(currentPos, user.username)
            } catch (_: Throwable) {}
        }

        // Inventory: register refresh callback and lazy-load if necessary
        val refreshCallback = { onRefreshWornItems() }
        onRegisterInventoryCallback(refreshCallback)
        InventoryState.addOnRefreshCallback(refreshCallback)
        if (!(InventoryState.currentUser != null && InventoryState.items.isNotEmpty())) {
            coroutineScope.launch { InventoryState.loadInventory(coroutineScope) { onRefreshWornItems() } }
        }

        // Listen for inventory-changed notifications for this user
        WebSocketApi.addInventoryChangedListener { evt ->
            val userId = InventoryState.currentUser?.key
            if (userId != null && evt.userId == userId) {
                coroutineScope.launch { InventoryState.refreshInventory(coroutineScope) { onRefreshWornItems() } }
            }
        }
    }
}
