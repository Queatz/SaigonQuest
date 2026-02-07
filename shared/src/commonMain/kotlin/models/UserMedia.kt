package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

@Serializable
enum class MediaType { Image, Video, Audio }

@Serializable
data class UserMedia(
    @field:Indexed
    var userId: String = "",
    var type: MediaType = MediaType.Image,
    var path: String = "",
    var favorite: Boolean = false,
    var originalFileName: String = ""
) : BaseModel()
