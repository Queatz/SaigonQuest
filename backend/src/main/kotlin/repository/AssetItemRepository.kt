

package repository

import com.arangodb.ArangoCollection
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.AssetSearchResult
import models.AssetType
import models.ItemAssetItem
import models.ObjectAssetItem
import models.SpriteSheetAssetItem
import models.WorldAssetItem


/**
 * Repository for asset items of different types. Provides search, retrieval by assetId,
 * and bulk replace (delete and recreate) operations.
 */
class AssetItemRepository {
    private fun collectionFor(type: AssetType): ArangoCollection = when (type) {
        AssetType.Items -> ArangoConfig.db.collection(CollectionsConfig.ITEM_ASSET_ITEMS_COLLECTION)
        AssetType.SpriteSheet -> ArangoConfig.db.collection(CollectionsConfig.SPRITESHEET_ASSET_ITEMS_COLLECTION)
        AssetType.World -> ArangoConfig.db.collection(CollectionsConfig.WORLD_ASSET_ITEMS_COLLECTION)
        AssetType.Objects -> ArangoConfig.db.collection(CollectionsConfig.OBJECT_ASSET_ITEMS_COLLECTION)
    }

    // Search
    fun searchItems(type: AssetType, title: String?, category: String?): List<Any> = when (type) {
        AssetType.Items -> searchItemsInternal(type, title, category, ItemAssetItem::class.java)
        AssetType.SpriteSheet -> searchItemsInternal(type, title, category, SpriteSheetAssetItem::class.java)
        AssetType.World -> searchItemsInternal(type, title, category, WorldAssetItem::class.java)
        AssetType.Objects -> searchItemsInternal(type, title, category, ObjectAssetItem::class.java)
    }

    private fun <T> searchItemsInternal(type: AssetType, title: String?, category: String?, clazz: Class<T>): List<T> {
        val query = buildString {
            append("FOR x IN @@collection ")
            val filters = mutableListOf<String>()
            if (title != null && title.isNotBlank()) {
                filters += "LIKE(LOWER(x.title), LOWER(CONCAT('%', @title, '%')))"
            }
            if (category != null && category.isNotBlank()) {
                filters += "LIKE(LOWER(x.category), LOWER(CONCAT('%', @category, '%')))"
            }
            if (filters.isNotEmpty()) {
                append("FILTER ")
                append(filters.joinToString(" AND "))
                append(' ')
            }
            append("RETURN x")
        }
        val bindVars = mutableMapOf<String, Any>("@collection" to collectionName(type))
        if (!title.isNullOrBlank()) bindVars["title"] = title
        if (!category.isNullOrBlank()) bindVars["category"] = category
        return ArangoConfig.db.query(query, clazz, bindVars).toList()
    }

    // Search combined with Asset document
    fun searchWithAsset(type: AssetType, title: String?, category: String?): List<AssetSearchResult> {
        val query = buildString {
            append("FOR x IN @@collection ")
            val filters = mutableListOf<String>()
            if (title != null && title.isNotBlank()) {
                filters += "LIKE(LOWER(x.title), LOWER(CONCAT('%', @title, '%')))"
            }
            if (category != null && category.isNotBlank()) {
                filters += "LIKE(LOWER(x.category), LOWER(CONCAT('%', @category, '%')))"
            }
            if (filters.isNotEmpty()) {
                append("FILTER ")
                append(filters.joinToString(" AND "))
                append(' ')
            }
            append("LET a = DOCUMENT(@@assets, x.assetId) ")
            append("RETURN { type: @type, asset: a, title: x.title, category: x.category, index: x.index, collection: x.collection }")
        }
        val bindVars = mutableMapOf<String, Any>(
            "@collection" to collectionName(type),
            "@assets" to CollectionsConfig.ASSETS_COLLECTION,
            "type" to type
        )
        if (!title.isNullOrBlank()) bindVars["title"] = title
        if (!category.isNullOrBlank()) bindVars["category"] = category
        return ArangoConfig.db.query(query, AssetSearchResult::class.java, bindVars).toList()
    }

    // By asset
    fun getByAssetId(type: AssetType, assetId: String): List<Any> = when (type) {
        AssetType.Items -> getByAssetIdInternal(type, assetId, ItemAssetItem::class.java)
        AssetType.SpriteSheet -> getByAssetIdInternal(type, assetId, SpriteSheetAssetItem::class.java)
        AssetType.World -> getByAssetIdInternal(type, assetId, WorldAssetItem::class.java)
        AssetType.Objects -> getByAssetIdInternal(type, assetId, ObjectAssetItem::class.java)
    }

    private fun <T> getByAssetIdInternal(type: AssetType, assetId: String, clazz: Class<T>): List<T> {
        val query = "FOR x IN @@collection FILTER x.assetId == @assetId RETURN x"
        val bindVars = mapOf(
            "@collection" to collectionName(type),
            "assetId" to assetId
        )
        return ArangoConfig.db.query(query, clazz, bindVars).toList()
    }

    // Replace all for asset
    fun replaceForAsset(type: AssetType, assetId: String, items: List<Any>) {
        // Delete existing
        val deleteQuery = "FOR x IN @@collection FILTER x.assetId == @assetId REMOVE x IN @@collection"
        ArangoConfig.db.query(
            deleteQuery,
            Void::class.java,
            mapOf("@collection" to collectionName(type), "assetId" to assetId)
        )
        // Insert new
        val coll = collectionFor(type)
        val options = DocumentCreateOptions().returnNew(true)
        when (type) {
            AssetType.Items -> items.filterIsInstance<ItemAssetItem>().forEach { coll.insertDocument(it.copy(assetId = assetId), options) }
            AssetType.SpriteSheet -> items.filterIsInstance<SpriteSheetAssetItem>().forEach { coll.insertDocument(it.copy(assetId = assetId), options) }
            AssetType.World -> items.filterIsInstance<WorldAssetItem>().forEach { coll.insertDocument(it.copy(assetId = assetId), options) }
            AssetType.Objects -> items.filterIsInstance<ObjectAssetItem>().forEach { coll.insertDocument(it.copy(assetId = assetId), options) }
        }
    }

    private fun collectionName(type: AssetType): String = when (type) {
        AssetType.Items -> CollectionsConfig.ITEM_ASSET_ITEMS_COLLECTION
        AssetType.SpriteSheet -> CollectionsConfig.SPRITESHEET_ASSET_ITEMS_COLLECTION
        AssetType.World -> CollectionsConfig.WORLD_ASSET_ITEMS_COLLECTION
        AssetType.Objects -> CollectionsConfig.OBJECT_ASSET_ITEMS_COLLECTION
    }
}
