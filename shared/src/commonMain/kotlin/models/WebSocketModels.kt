package models

import kotlinx.serialization.Serializable

/**
 * Player position data model
 */
@Serializable
data class PlayerPositionData(
    val position: Position,
    val isPlayer: Boolean,
    val userId: String
)

/**
 * Player interaction data model
 */
@Serializable
data class PlayerInteractData(
    val objectId: String,
    val isPlayer: Boolean,
    val userId: String
)

/**
 * Trigger event data model
 */
@Serializable
data class TriggerEventData(
    val objectId: String,
    val eventType: String,
    val userId: String
)

/**
 * User join data model
 * 
 * This model is sent when a user joins a world.
 */
@Serializable
data class UserJoinData(
    val userId: String,
    val username: String,
    val isPlayer: Boolean,
    val position: Position,
    val worldId: String
)

/**
 * User leave data model
 * 
 * This model is sent when a user leaves a world.
 */
@Serializable
data class UserLeaveData(
    val userId: String,
    val worldId: String
)
