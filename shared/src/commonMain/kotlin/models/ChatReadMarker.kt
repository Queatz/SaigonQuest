package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant
import models.annotations.Indexed
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes

/**
 * Represents a marker for tracking when a user last read messages in a world.
 *
 * @property userId The ID of the user
 * @property worldId The ID of the world
 * @property lastReadTimestamp The time when the user last read messages in the world
 * @property notificationSent Whether a "new chat" notification has been sent since the last visit
 * @property subscribed Whether the user is currently subscribed to the world
 */

@CompositeIndexes(
    value = [
        CompositeIndex(
            fields = [
                "userId",
                "worldId",
            ],
        ),
    ],
)
@Serializable
 data class ChatReadMarker(
    @field:Indexed
    var userId: String = "",
    @field:Indexed
    var worldId: String = "",
    @Contextual
    @field:Indexed
    var lastReadTimestamp: Instant = Clock.System.now(),
    var notificationSent: Boolean = false,
    var subscribed: Boolean = true,
) : BaseModel()
