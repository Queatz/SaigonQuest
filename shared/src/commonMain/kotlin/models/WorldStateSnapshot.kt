package models

import kotlinx.serialization.Serializable
import main.components.world.PlayerActivity

@Serializable
data class PlayerSnapshot(
    val userId: String,
    val username: String,
    val isPlayer: Boolean,
    val position: Position,
    val activity: PlayerActivity? = null,
    val status: String? = null,
    val isTyping: Boolean? = null,
)

@Serializable
data class WorldStateSnapshot(
    val worldId: String,
    val version: Long = 0L,
    val serverTime: Long = 0L,
    val players: List<PlayerSnapshot> = emptyList(),
)
