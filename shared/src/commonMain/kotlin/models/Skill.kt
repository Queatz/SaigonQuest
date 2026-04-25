package models

import kotlinx.serialization.Serializable

@Serializable
data class Skill(
    var title: String = "",
    var description: String = "",
    var imageUrl: String = "",
) : BaseModel()
