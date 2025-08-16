package models

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a pin pointing to a chat message in a world.
 * Stored separately from chat messages to keep messages lightweight.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class ChatMessagePin(
    var worldId: String = "",
    var messageId: String = "",
    var pinnedByUserId: String = "",
    var pinnedAt: Long = Clock.System.now().toEpochMilliseconds()
) : BaseModel()

