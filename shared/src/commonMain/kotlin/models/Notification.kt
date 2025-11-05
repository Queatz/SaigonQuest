package models

import kotlinx.serialization.Serializable
import kotlin.time.Clock

import kotlin.time.Instant
import models.annotations.Indexed
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes

/**
 * Represents a notification for a user.
 *
 * @property userId The ID of the user who should receive the notification
 * @property title The title of the notification
 * @property description The description/content of the notification
 * @property isRead Whether the notification has been read by the user
 * @property type The type of notification
 * @property worldId Optional world ID related to this notification
 * @property objectId Optional object ID related to this notification
 * @property otherUserId Optional user ID related to this notification (e.g., friend request sender)
 * @property timestamp The time when the notification was created (milliseconds since epoch)
 */

@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["userId", "timestamp"]),
        CompositeIndex(fields = ["userId", "isRead"]),
    ],
)
@Serializable
 data class Notification(
    @field:Indexed
    var userId: String = "",
    var title: String = "",
    var description: String = "",
    var isRead: Boolean = false,
    var type: NotificationType = NotificationType.SYSTEM,
    var worldId: String = "",
    var objectId: String = "",
    var otherUserId: String = "",
    @field:Indexed
    var timestamp: Instant = Clock.System.now()
) : BaseModel()
