package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.ErrorResponse
import models.Item

/**
 * API client for item operations
 * 
 * This API provides methods for fetching and managing items.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object ItemApi {
    
    @kotlinx.serialization.Serializable
    data class BuyItemRequest(
        val itemId: String,
        val quantity: Int,
    )

    @kotlinx.serialization.Serializable
    data class BuyItemResponse(
        val balance: models.UserBalance,
        val inventory: models.Inventory,
    )
    
    /**
     * Gets all items (admin protected)
     */
    suspend fun getAllItemsAdmin(): List<Item> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/items/admin") {
                ApiClient.addAuthHeader(this)
            }
            if (response.status.isSuccess()) {
                return response.body<List<Item>>()
            } else {
                val errorResponse = try { response.body<ErrorResponse>() } catch (e: Throwable) { ErrorResponse(message = response.bodyAsText()) }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching items (admin)", e)
            throw e
        }
    }
    
    /**
     * Gets an item by its ID
     */
    suspend fun getItemById(itemId: String): Item {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/items/$itemId") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Item>()
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching item", e)
            throw e
        }
    }
    
    /**
     * Gets all available items
     */
    suspend fun getAllItems(): List<Item> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/items") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<Item>>()
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching items", e)
            throw e
        }
    }
    
    /**
     * Gets items by their IDs
     */
    suspend fun getItemsByIds(itemIds: List<String>): List<Item> {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/items/batch") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(itemIds)
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<Item>>()
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error fetching items by IDs", e)
            throw e
        }
    }
    
    /**
     * Creates a new item
     */
    suspend fun createItem(item: Item): Item {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/items") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(item)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Item>()
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Throwable) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error creating item", e)
            throw e
        }
    }

    /**
     * Updates an existing item (admin)
     */
    suspend fun updateItem(itemId: String, item: Item): Item {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/items/$itemId") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(item)
            }

            if (response.status.isSuccess()) {
                return response.body<Item>()
            } else {
                val errorResponse = try { response.body<ErrorResponse>() } catch (e: Throwable) { ErrorResponse(message = response.bodyAsText()) }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error updating item", e)
            throw e
        }
    }

    /**
     * Deletes an item (admin)
     */
    suspend fun deleteItem(itemId: String) {
        try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/items/$itemId") {
                ApiClient.addAuthHeader(this)
            }
            if (!response.status.isSuccess()) {
                val errorResponse = try { response.body<ErrorResponse>() } catch (e: Throwable) { ErrorResponse(message = response.bodyAsText()) }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error deleting item", e)
            throw e
        }
    }

    /**
     * Buy an item
     */
    suspend fun buyItem(itemId: String, quantity: Int): BuyItemResponse {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/items/buy") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(BuyItemRequest(itemId = itemId, quantity = quantity))
            }
            if (response.status.isSuccess()) {
                return response.body<BuyItemResponse>()
            } else {
                val errorResponse = try { response.body<ErrorResponse>() } catch (e: Throwable) { ErrorResponse(message = response.bodyAsText()) }
                throw Error(errorResponse.message)
            }
        } catch (e: Throwable) {
            console.error("Error buying item", e)
            throw e
        }
    }
}
