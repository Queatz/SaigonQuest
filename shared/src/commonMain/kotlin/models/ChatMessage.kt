package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import models.chat.Attachment
import models.annotations.CompositeIndex

@Serializable
enum class ChatSenderType {
    USER,
    OBJECT,
    SYSTEM,
}

/**
 * Represents a chat message in a world.
 */
@CompositeIndex(
    fields = [
        "worldId",
        "timestamp",
    ],
)
@Serializable
data class ChatMessage(
    var worldId: String = "",
    var senderType: ChatSenderType = ChatSenderType.USER,
    var senderId: String = "",      // userId, objectId, or empty for SYSTEM
    var senderName: String = "",    // username, object name, or "System"
    var message: String = "",
    @Contextual
    var timestamp: Instant = Clock.System.now(),
    var edited: Boolean = false,
    var deleted: Boolean = false,
    var attachments: List<Attachment> = emptyList(),
    var shouted: Boolean = false,
) : BaseModel()
