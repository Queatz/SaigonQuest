package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant

/**
 * Represents a friendship between two users.
 *
 * @property userId The ID of the user who owns this friendship record
 * @property friendId The ID of the friend
 * @property friendUsername The username of the friend
 * @property lastActive When the friend was last active
 * @property lastWorldId The ID of the world where the friend was last seen
 * @property lastWorldName The name of the world where the friend was last seen
 * @property lastPosition The position where the friend was last seen
 */

@Serializable
data class Friend(
    var userId: String = "",
    var friendId: String = "",
    var friendUsername: String = "",
    var lastActive: Instant = Clock.System.now(),
    var lastWorldId: String = "",
    var lastWorldName: String = "",
    var lastPosition: Position? = null
) : BaseModel()
