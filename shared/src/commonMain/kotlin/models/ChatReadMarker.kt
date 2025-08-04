package models

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a marker for tracking when a user last read messages in a world.
 *
 * @property userId The ID of the user
 * @property worldId The ID of the world
 * @property lastReadTimestamp The timestamp when the user last read messages in the world
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class ChatReadMarker(
    var userId: String = "",
    var worldId: String = "",
    var lastReadTimestamp: Long = Clock.System.now().toEpochMilliseconds()
) : BaseModel()
