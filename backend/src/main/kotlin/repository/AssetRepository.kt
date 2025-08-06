package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Asset
import models.AssetType

/**
 * Repository for asset operations
 *
 * This repository provides methods for saving, retrieving, updating, and deleting assets.
 */
class AssetRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.ASSETS_COLLECTION)
    
    /**
     * Save an asset to the database
     *
     * @param asset The asset to save
     * @return The saved asset with its key
     */
    fun saveAsset(asset: Asset): Asset {
        val result: DocumentCreateEntity<Asset> = collection.insertDocument(
            asset,
            DocumentCreateOptions().returnNew(true)
        )
        
        return result.new
    }
    
    /**
     * Get all assets
     *
     * @return A list of all assets
     */
    fun getAllAssets(): List<Asset> {
        val query = """
            FOR a IN @@collection
            RETURN a
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.ASSETS_COLLECTION
        )
        
        return ArangoConfig.db.query(query, Asset::class.java, bindVars).toList()
    }
    
    /**
     * Get all assets by type
     *
     * @param type The type of assets to get
     * @return A list of assets of the specified type
     */
    fun getAssetsByType(type: AssetType): List<Asset> {
        val query = """
            FOR a IN @@collection
            FILTER a.type == @type
            RETURN a
        """.trimIndent()
        
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.ASSETS_COLLECTION,
            "type" to type
        )
        
        return ArangoConfig.db.query(query, Asset::class.java, bindVars).toList()
    }
    
    /**
     * Update an asset
     *
     * @param assetKey The key of the asset to update
     * @param updatedAsset The updated asset data
     * @return The updated asset, or null if the asset was not found
     */
    fun updateAsset(assetKey: String, updatedAsset: Asset): Asset? {
        try {
            // Ensure the key is preserved
            updatedAsset.key = assetKey
            
            val result: DocumentUpdateEntity<Asset> = collection.updateDocument(
                assetKey,
                updatedAsset,
                DocumentUpdateOptions().returnNew(true)
            )
            
            return result.new
        } catch (e: Exception) {
            // Log the error
            println("Error updating asset: ${e.message}")
            return null
        }
    }
    
    /**
     * Delete an asset
     *
     * @param assetKey The key of the asset to delete
     * @return True if the asset was deleted, false otherwise
     */
    fun deleteAsset(assetKey: String): Boolean {
        return try {
            collection.deleteDocument(assetKey)
            true
        } catch (e: Exception) {
            // Log the error
            println("Error deleting asset: ${e.message}")
            false
        }
    }
    
    /**
     * Get an asset by key
     *
     * @param assetKey The key of the asset to get
     * @return The asset, or null if not found
     */
    fun getAssetByKey(assetKey: String): Asset? {
        return try {
            collection.getDocument(assetKey, Asset::class.java)
        } catch (e: Exception) {
            // Log the error
            println("Error getting asset: ${e.message}")
            null
        }
    }
}
