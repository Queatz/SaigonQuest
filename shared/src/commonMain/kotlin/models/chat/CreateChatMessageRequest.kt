package models.chat

import kotlinx.serialization.Serializable

@Serializable
data class CreateChatMessageRequest(
    val message: String = "",
    val attachments: List<Attachment> = emptyList(),
)
