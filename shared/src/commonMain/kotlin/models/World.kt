package models

import kotlinx.serialization.Serializable

@Serializable
data class World(
    var title: String = "",
    var subtitle: String = "",
    var description: String = "",
    var url: String = "", // external URL, empty string is valid
    var assetId: String = "", // world asset id so we can get the asset name
    var file: String = "", // world file name without .glb
    var userId: String = "", // to track ownership
    var isOutdoors: Boolean = false, // to track ownership
    var latLng: List<Double>? = null, // to track time of day
    var backgroundColor: String = "#ffffff", // background color in hex format
    var directionalLightColor: String = "#ffffff", // directional light color in hex format
    var fogDensity: Float = 0.0f, // fog density (0.0 to 1.0), 0.0 means no fog
    var screenshot: String? = null, // URL (PNG) thumbnail of the world scene
    // Access control
    var allowAllVisitors: Boolean = true,
    var allowAllPlayers: Boolean = false,
    var allowFriendsOfOwners: Boolean = false,
    var allowHoldersOfItemId: String? = null,
    // Chat settings
    var chatLongevity: Double = 0.0, // in days; 0.0 means forever
) : BaseModel()
