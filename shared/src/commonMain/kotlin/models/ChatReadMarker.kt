package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant

/**
 * Represents a marker for tracking when a user last read messages in a world.
 *
 * @property userId The ID of the user
 * @property worldId The ID of the world
 * @property lastReadTimestamp The time when the user last read messages in the world
 * @property notificationSent Whether a "new chat" notification has been sent since the last visit
 */

@Serializable
 data class ChatReadMarker(
    var userId: String = "",
    var worldId: String = "",
    @Contextual
    var lastReadTimestamp: Instant = Clock.System.now(),
    var notificationSent: Boolean = false,
) : BaseModel()
