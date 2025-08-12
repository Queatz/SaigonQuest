package main.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import models.ErrorResponse
import models.LogicNode
import models.NodeCategory
import models.Object
import models.getAllNodeTypes

/**
 * API client for object operations
 * 
 * This API provides methods for managing objects in the world.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object ObjectApi {
    
    /**
     * Gets all objects for a specific world
     */
    suspend fun getObjectsByWorldId(worldId: String): List<Object> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/object") {
                url {
                    parameters.append("worldId", worldId)
                }
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<Object>>()
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
            console.error("Error fetching objects", e)
            throw e
        }
    }
    
    /**
     * Gets an object by ID
     */
    suspend fun getObject(id: String): Object {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/object/$id")
            
            if (response.status.isSuccess()) {
                return response.body<Object>()
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
            console.error("Error fetching object", e)
            throw e
        }
    }
    
    /**
     * Creates a new object
     */
    suspend fun createObject(obj: Object): Object {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/object") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(obj)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Object>()
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
            console.error("Error creating object", e)
            throw e
        }
    }
    
    /**
     * Updates an existing object
     */
    suspend fun updateObject(id: String, obj: Object): Object {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/object/$id") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(obj)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Object>()
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
            console.error("Error updating object", e)
            throw e
        }
    }
    
    /**
     * Deletes an object
     */
    suspend fun deleteObject(id: String) {
        try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/object/$id") {
                ApiClient.addAuthHeader(this)
            }
            
            if (!response.status.isSuccess()) {
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
            console.error("Error deleting object", e)
            throw e
        }
    }
    
    /**
     * Gets all available node types
     * 
     * In a real implementation, this would fetch from the backend.
     * For now, we're returning the predefined node types.
     */
    suspend fun getNodeTypes(): List<LogicNode> {
        return getAllNodeTypes()
    }
    
    /**
     * Gets all available node types grouped by category
     * 
     * In a real implementation, this would fetch from the backend.
     * For now, we're returning the predefined node types grouped by category.
     */
    suspend fun getNodeTypesByCategory(): Map<NodeCategory, List<LogicNode>> {
        return models.getNodeTypesByCategory()
    }
    
    /**
     * Collects an inventory item from the world
     * 
     * This method is used to collect dropped inventory items from the world.
     * It makes a POST request to the collect endpoint and returns a boolean
     * indicating whether the collection was successful.
     * 
     * @param objectId The ID of the object to collect
     * @return true if the collection was successful, false otherwise
     */
    suspend fun collectObject(objectId: String): Boolean {
        return try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/object/collect/$objectId") {
                ApiClient.addAuthHeader(this)
            }
            
            response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error collecting object", e)
            false
        }
    }
}
