package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

@Serializable
data class World(
    var title: String = "",
    var subtitle: String = "",
    var description: String = "",
    @field:Indexed
    var url: String = "", // external URL, empty string is valid
    var assetId: String = "", // world asset id so we can get the asset name
    var file: String = "", // world file name without .glb
    @field:Indexed
    var userId: String = "", // to track ownership
    var ownerIds: List<String> = emptyList(), // to track ownership
    var isOutdoors: Boolean = false, // to track ownership
    var latLng: List<Double>? = null, // to track time of day
    var backgroundColor: String = "#ffffff", // background color in hex format
    var backgroundColorIntensity: Float = 1.0f, // background color intensity (used for hemispheric light)
    var directionalLightColor: String = "#ffffff", // directional light color in hex format
    var directionalLightIntensity: Float = 1.0f, // directional light intensity
    var fogDensity: Float = 0.0f, // fog density (0.0 to 1.0), 0.0 means no fog
    var screenshot: String? = null, // URL (PNG) thumbnail of the world scene
    // Music settings
    var musicUrl: String? = null, // background music for the world (loops)
    var musicTitle: String? = null, // title of the background music
    var nightMusicUrl: String? = null, // optional: music to play at night when outdoors
    var nightMusicTitle: String? = null, // title of the night music
    // Access control
    var allowAllVisitors: Boolean = true,
    var allowAllPlayers: Boolean = false,
    var allowFriendsOfOwners: Boolean = false,
    var allowHoldersOfItemId: String? = null,
    // Chat settings
    var chatLongevity: Double = 0.0, // in days; 0.0 means forever
) : BaseModel()

fun World.canUserEdit(userId: String): Boolean {
    return userId == this.userId || ownerIds.contains(userId)
}
