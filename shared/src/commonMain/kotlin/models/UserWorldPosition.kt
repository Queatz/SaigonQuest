package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

@Serializable
data class UserWorldPosition(
    @field:Indexed
    var userId: String = "",
    @field:Indexed
    var worldId: String = "",
    var position: Position = Position(),
) : BaseModel()
