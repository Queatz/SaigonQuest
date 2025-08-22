package models

import kotlinx.serialization.Serializable

@Serializable
data class AssetSearchResult(
    var type: AssetType? = null,
    var asset: Asset? = null,
    var title: String? = null,
    var category: String? = null,
    var index: Int? = null,
    var collection: String? = null,
)
