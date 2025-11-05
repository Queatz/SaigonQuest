package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

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
    @field:Indexed
    var type: AssetType = AssetType.Items,
    var file: String = "",
) : BaseModel()

@Serializable
data class ItemAssetItem(
    @field:Indexed
    var assetId: String = "",
    var title: String = "",
    var category: String = "",
    var index: Int = 0,
) : BaseModel()

@Serializable
data class ObjectAssetItem(
    @field:Indexed
    var assetId: String = "",
    var title: String = "",
    var category: String = "",
    var collection: String = "",
) : BaseModel()

@Serializable
data class WorldAssetItem(
    @field:Indexed
    var assetId: String = "",
    var title: String = "",
    var category: String = "",
) : BaseModel()

@Serializable
data class SpriteSheetAssetItem(
    @field:Indexed
    var assetId: String = "",
    var title: String = "",
    var category: String = "",
    var customWeights: Boolean = false,
    var weightsCsv: String? = null,
) : BaseModel()
