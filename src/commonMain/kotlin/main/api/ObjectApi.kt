package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.ErrorResponse
import models.Object

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
                bearerAuth(UserApi.getToken() ?: "")
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<Object>>()
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
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    ErrorResponse(message = response.bodyAsText())
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
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
                bearerAuth(UserApi.getToken() ?: "")
                setBody(obj)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Object>()
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
            console.error("Error creating object", e)
            throw e
        }
    }
    
    /**
     * Updates an existing object
     */
    suspend fun updateObject(id: String, obj: Object): Object {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/object/$id") {
                contentType(ContentType.Application.Json)
                bearerAuth(UserApi.getToken() ?: "")
                setBody(obj)
            }
            
            if (response.status.isSuccess()) {
                return response.body<Object>()
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
                bearerAuth(UserApi.getToken() ?: "")
            }
            
            if (!response.status.isSuccess()) {
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
            console.error("Error deleting object", e)
            throw e
        }
    }
}
