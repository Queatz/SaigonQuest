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
    var backgroundColor: String = "#000000", // background color in hex format
    var directionalLightColor: String = "#ffffff", // directional light color in hex format
    var fogDensity: Float = 0.0f // fog density (0.0 to 1.0), 0.0 means no fog
) : BaseModel()
