package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant

/**
 * Represents a pin pointing to a chat message in a world.
 * Stored separately from chat messages to keep messages lightweight.
 */

@Serializable
data class ChatMessagePin(
    var worldId: String = "",
    var messageId: String = "",
    var pinnedByUserId: String = "",
    @Contextual
    var pinnedAt: Instant = Clock.System.now()
) : BaseModel()

