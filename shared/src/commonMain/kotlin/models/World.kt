package models

import kotlinx.serialization.Serializable

@Serializable
data class World(
    var title: String = "",
    var subtitle: String = "",
    var description: String = "",
    var url: String = "", // external URL, empty string is valid
    var file: String = "", // world file name without .glb
    var userId: String = "", // to track ownership
    var backgroundColor: String = "#000000" // background color in hex format
) : BaseModel()
