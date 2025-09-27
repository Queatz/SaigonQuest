package models.chat

import kotlinx.serialization.Serializable
import models.ChatSenderType

@Serializable
data class CreateChatMessageRequest(
    val message: String = "",
    val attachments: List<Attachment> = emptyList(),
    val senderType: ChatSenderType = ChatSenderType.USER,
    val senderId: String = "", // required for OBJECT; ignored for SYSTEM
)
