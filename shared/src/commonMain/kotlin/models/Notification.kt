package models

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a notification for a user.
 *
 * @property userId The ID of the user who should receive the notification
 * @property title The title of the notification
 * @property description The description/content of the notification
 * @property isRead Whether the notification has been read by the user
 * @property type The type of notification (e.g., "system", "friend_request", "achievement")
 * @property relatedId Optional ID related to this notification (e.g., world ID, user ID)
 * @property timestamp The time when the notification was created (milliseconds since epoch)
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Notification(
    var userId: String = "",
    var title: String = "",
    var description: String = "",
    var isRead: Boolean = false,
    var type: String = "system",
    var relatedId: String = "",
    var timestamp: Long = Clock.System.now().toEpochMilliseconds()
) : BaseModel()
