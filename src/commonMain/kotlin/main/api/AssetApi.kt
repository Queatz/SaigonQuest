package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import models.Asset
import models.AssetType

/**
 * API for asset operations
 * 
 * This API provides methods for managing assets in the system.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object AssetApi {
    private const val ASSETS_ENDPOINT = "/assets"
    
    /**
     * Get all assets
     *
     * @return List of all assets
     */
    suspend fun getAllAssets(): List<Asset> = withContext(Dispatchers.Default) {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ASSETS_ENDPOINT")
            
            if (response.status.isSuccess()) {
                response.body()
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Exception) {
            console.error("Error getting all assets: ${e.message}")
            throw e
        }
    }
    
    /**
     * Get assets by type
     *
     * @param type The type of assets to get
     * @return List of assets of the specified type
     */
    suspend fun getAssetsByType(type: AssetType): List<Asset> = withContext(Dispatchers.Default) {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ASSETS_ENDPOINT/type/${type.name}")
            
            if (response.status.isSuccess()) {
                response.body()
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Exception) {
            console.error("Error getting assets by type: ${e.message}")
            throw e
        }
    }
    
    /**
     * Get asset by ID
     *
     * @param id The ID of the asset to get
     * @return The asset with the specified ID
     */
    suspend fun getAssetById(id: String): Asset = withContext(Dispatchers.Default) {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ASSETS_ENDPOINT/$id")
            
            if (response.status.isSuccess()) {
                response.body()
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Exception) {
            console.error("Error getting asset by ID: ${e.message}")
            throw e
        }
    }
    
    /**
     * Create a new asset (admin only)
     *
     * @param asset The asset to create
     * @return The created asset
     */
    suspend fun createAsset(asset: Asset): Asset = withContext(Dispatchers.Default) {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}$ASSETS_ENDPOINT") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(asset)
            }
            
            if (response.status.isSuccess()) {
                response.body()
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Exception) {
            console.error("Error creating asset: ${e.message}")
            throw e
        }
    }
    
    /**
     * Update an asset (admin only)
     *
     * @param id The ID of the asset to update
     * @param asset The updated asset data
     * @return The updated asset
     */
    suspend fun updateAsset(id: String, asset: Asset): Asset = withContext(Dispatchers.Default) {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}$ASSETS_ENDPOINT/$id") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(asset)
            }
            
            if (response.status.isSuccess()) {
                response.body()
            } else {
                throw Error(response.bodyAsText())
            }
        } catch (e: Exception) {
            console.error("Error updating asset: ${e.message}")
            throw e
        }
    }
    
    /**
     * Delete an asset (admin only)
     *
     * @param id The ID of the asset to delete
     * @return True if the asset was deleted successfully
     */
    suspend fun deleteAsset(id: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}$ASSETS_ENDPOINT/$id") {
                ApiClient.addAuthHeader(this)
            }
            
            response.status.isSuccess()
        } catch (e: Exception) {
            console.error("Error deleting asset: ${e.message}")
            throw e
        }
    }
}
