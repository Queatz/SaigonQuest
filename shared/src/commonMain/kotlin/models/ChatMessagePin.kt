package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant
import models.annotations.Indexed
import models.annotations.CompositeIndex

/**
 * Represents a pin pointing to a chat message in a world.
 * Stored separately from chat messages to keep messages lightweight.
 */

@CompositeIndex(
    fields = [
        "worldId",
        "messageId",
    ],
)
@Serializable
data class ChatMessagePin(
    @field:Indexed
    var worldId: String = "",
    var messageId: String = "",
    var pinnedByUserId: String = "",
    @Contextual
    @field:Indexed
    var pinnedAt: Instant = Clock.System.now()
) : BaseModel()

