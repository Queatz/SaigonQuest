package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

@Serializable
data class SavedPosition(
    var name: String = "",
    @field:Indexed
    var worldId: String = "",
    var position: Position = Position(),
) : BaseModel()
