package main.components.player

import kotlinx.coroutines.sync.Mutex
import lib.Vector3
import main.components.world.CharacterDirection
import main.components.world.PlayerActivity
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import models.User

data class OtherPlayer(
    val userId: String,
    var username: String = "Player",
    var isPlayer: Boolean = false,
    var handle: WorldObjectHandle? = null,
    var activity: PlayerActivity? = null,
    var direction: CharacterDirection = CharacterDirection.S,
    var status: String = "",
    var isTyping: Boolean = false,
    // Movement
    var targetPos: Vector3? = null,
    var lastPos: Vector3 = Vector3(0f, 0f, 0f),
    // Appearance
    var profile: User? = null,
    var appearanceRevision: Int = 0,
    var additionalSpriteSheets: List<SpriteSheet> = emptyList(),
    var skinTone: Float = 0.5f,
    var lastRippleTime: Double = 0.0,
    var isOnWater: Boolean = false,
    var lastEnvCheckFrame: Int = -1,
            var shouldSnap: Boolean = false,
    var isSpawning: Boolean = false,
    var spawnJob: kotlinx.coroutines.Job? = null,
) {
    // Serialize mutations per player
    val mutex = Mutex()
}
