package models

import kotlinx.serialization.Serializable

@Serializable
data class UserEmojiHistory(
    var userId: String = "",
    var emojis: List<String> = emptyList(),
) : BaseModel()

@Serializable
data class RecordEmojiRequest(
    val emoji: String
)
