package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant

/**
 * Represents a chat message in a world.
 *
 * @property worldId The ID of the world where the message was sent
 * @property userId The ID of the user who sent the message
 * @property username The username of the user who sent the message
 * @property message The content of the message
 * @property timestamp The time when the message was sent
 * @property edited Whether the message has been edited
 * @property deleted Whether the message has been deleted
 */
@Serializable
data class ChatMessage(
    var worldId: String = "",
    var userId: String = "",
    var username: String = "",
    var message: String = "",
    @Contextual
    var timestamp: Instant = Clock.System.now(),
    var edited: Boolean = false,
    var deleted: Boolean = false
) : BaseModel()
