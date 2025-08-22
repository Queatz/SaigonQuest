package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import models.AssetType
import models.ItemAssetItem
import models.ObjectAssetItem
import models.SpriteSheetAssetItem
import models.WorldAssetItem

object AssetItemApi {
    private const val ENDPOINT = "/asset-items"

    // Search
    suspend fun searchItemAssetItems(
        title: String? = null,
        category: String? = null,
    ): List<ItemAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/search") {
            parameter("type", AssetType.Items.name)
            if (!title.isNullOrBlank()) parameter("title", title)
            if (!category.isNullOrBlank()) parameter("category", category)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun searchSpriteSheetAssetItems(
        title: String? = null,
        category: String? = null,
    ): List<SpriteSheetAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/search") {
            parameter("type", AssetType.SpriteSheet.name)
            if (!title.isNullOrBlank()) parameter("title", title)
            if (!category.isNullOrBlank()) parameter("category", category)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun searchWorldAssetItems(
        title: String? = null,
        category: String? = null,
    ): List<WorldAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/search") {
            parameter("type", AssetType.World.name)
            if (!title.isNullOrBlank()) parameter("title", title)
            if (!category.isNullOrBlank()) parameter("category", category)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun searchObjectAssetItems(
        title: String? = null,
        category: String? = null,
    ): List<ObjectAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/search") {
            parameter("type", AssetType.Objects.name)
            if (!title.isNullOrBlank()) parameter("title", title)
            if (!category.isNullOrBlank()) parameter("category", category)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    // Search including Asset document for previews/browsing
    suspend fun searchWithAsset(
        type: AssetType,
        title: String? = null,
        category: String? = null,
    ): List<models.AssetSearchResult> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/search-with-asset") {
            parameter("type", type.name)
            if (!title.isNullOrBlank()) parameter("title", title)
            if (!category.isNullOrBlank()) parameter("category", category)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    // By asset id
    suspend fun getItemAssetItemsByAsset(assetId: String): List<ItemAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.Items.name}/by-asset/$assetId")
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun getSpriteSheetAssetItemsByAsset(assetId: String): List<SpriteSheetAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.SpriteSheet.name}/by-asset/$assetId")
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun getWorldAssetItemsByAsset(assetId: String): List<WorldAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.World.name}/by-asset/$assetId")
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun getObjectAssetItemsByAsset(assetId: String): List<ObjectAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.Objects.name}/by-asset/$assetId")
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    // Replace for asset (admin)
    suspend fun replaceItemAssetItems(assetId: String, items: List<ItemAssetItem>): List<ItemAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.Items.name}/by-asset/$assetId") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(items)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun replaceSpriteSheetAssetItems(assetId: String, items: List<SpriteSheetAssetItem>): List<SpriteSheetAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.SpriteSheet.name}/by-asset/$assetId") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(items)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun replaceWorldAssetItems(assetId: String, items: List<WorldAssetItem>): List<WorldAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.World.name}/by-asset/$assetId") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(items)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

    suspend fun replaceObjectAssetItems(assetId: String, items: List<ObjectAssetItem>): List<ObjectAssetItem> = withContext(Dispatchers.Default) {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}$ENDPOINT/${AssetType.Objects.name}/by-asset/$assetId") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(items)
        }
        if (response.status.isSuccess()) response.body() else throw Error(response.bodyAsText())
    }

}
