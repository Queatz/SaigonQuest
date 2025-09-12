package models.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Attachment

@Serializable
@SerialName("Image")
data class ImageAttachment(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
) : Attachment
