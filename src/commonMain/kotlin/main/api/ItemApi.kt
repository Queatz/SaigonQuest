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
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
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
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
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
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
            console.error("Error fetching items by IDs", e)
            throw e
        }
    }
}
