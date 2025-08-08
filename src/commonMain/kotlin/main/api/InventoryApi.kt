package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.ErrorResponse
import models.Inventory
import models.InventoryItem

/**
 * API client for inventory operations
 * 
 * This API provides methods for managing user inventories.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object InventoryApi {
    
    /**
     * Gets the inventory for the current user
     */
    suspend fun getUserInventory(): Inventory {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/inventory") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Inventory>()
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
            console.error("Error fetching inventory", e)
            throw e
        }
    }
    
    /**
     * Gets an inventory by user ID
     */
    suspend fun getInventoryByUserId(userId: String): Inventory {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/inventory/user/$userId") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Inventory>()
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
            console.error("Error fetching inventory", e)
            throw e
        }
    }
    
    /**
     * Adds an item to the user's inventory
     */
    suspend fun addItemToInventory(item: InventoryItem): Inventory {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/inventory/item") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(item)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Inventory>()
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
            console.error("Error adding item to inventory", e)
            throw e
        }
    }
    
    /**
     * Removes an item from the user's inventory
     */
    suspend fun removeItemFromInventory(itemId: String): Inventory {
        try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/inventory/item/$itemId") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Inventory>()
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
            console.error("Error removing item from inventory", e)
            throw e
        }
    }
    
    /**
     * Updates an item in the user's inventory
     */
    suspend fun updateInventoryItem(itemId: String, item: InventoryItem): Inventory {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/inventory/item/$itemId") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(item)
            }
        
            if (response.status.isSuccess()) {
                return response.body<Inventory>()
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
            console.error("Error updating inventory item", e)
            throw e
        }
    }

    /**
     * Gets inventory items by their IDs
     */
    suspend fun getInventoryItemsByIds(itemIds: List<String>): List<InventoryItem> {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/inventory/items/batch") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(itemIds)
            }
        
            if (response.status.isSuccess()) {
                return response.body<List<InventoryItem>>()
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
            console.error("Error fetching inventory items by IDs", e)
            throw e
        }
    }
    
    /**
     * Updates the item order in the user's inventory
     */
    suspend fun updateInventoryItemOrder(itemOrder: List<String>): Inventory {
        try {
            // Filter out any empty strings from the itemOrder list
            val validItemOrder = itemOrder.filter { it.isNotEmpty() }
            
            // Log if any items were filtered out
            if (validItemOrder.size != itemOrder.size) {
                console.warn("[DEBUG_LOG] Filtered out ${itemOrder.size - validItemOrder.size} empty strings from inventory item order")
            }
            
            console.log("[DEBUG_LOG] Updating inventory item order: $validItemOrder")
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/inventory/item-order") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(mapOf("itemOrder" to validItemOrder))
            }
            
            if (response.status.isSuccess()) {
                console.log("[DEBUG_LOG] Successfully updated inventory item order")
                return response.body<Inventory>()
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                console.error("[DEBUG_LOG] Error updating inventory item order", errorResponse.message)
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
            console.error("[DEBUG_LOG] Exception updating inventory item order", e)
            throw e
        }
    }
}
