package models

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType { Image, Video }

@Serializable
data class UserMedia(
    var userId: String = "",
    var type: MediaType = MediaType.Image,
    var path: String = "",
    var originalFileName: String = ""
) : BaseModel()

