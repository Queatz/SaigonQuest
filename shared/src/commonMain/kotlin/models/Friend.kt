package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant
import models.annotations.Indexed
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes

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

@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["userId", "friendId"]),
    ],
)
@Serializable
data class Friend(
    @field:Indexed
    var userId: String = "",
    @field:Indexed
    var friendId: String = "",
    var friendUsername: String = "",
    @field:Indexed
    var lastActive: Instant = Clock.System.now(),
    @field:Indexed
    var lastWorldId: String = "",
    var lastWorldName: String = "",
    var lastPosition: Position? = null,
    var note: String = "",
    var friendStatus: String = ""
) : BaseModel()
