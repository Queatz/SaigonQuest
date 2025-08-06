package models

import kotlinx.serialization.Serializable

/**
 * Enum representing the different types of assets in the system
 */
@Serializable
enum class AssetType {
    Items,
    SpriteSheet,
    World,
    Objects
}

/**
 * Data model representing an asset in the system
 *
 * @property type The type of asset (Items, SpriteSheet, World, Objects)
 * @property file The filename without extension
 */
@Serializable
data class Asset(
    var type: AssetType = AssetType.Items,
    var file: String = ""
) : BaseModel()
