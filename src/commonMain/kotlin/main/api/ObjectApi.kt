package main.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import models.ErrorResponse
import models.Object
import kotlin.js.Promise
import main.api.ApiConstants

/**
 * API client for object operations
 */
object ObjectApi {
    private val scope = MainScope()
    
    // Create a Ktor HttpClient with JSON serialization
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    /**
     * Gets all objects for a specific world
     */
    fun getObjectsByWorldId(worldId: String): Promise<List<Object>> {
        return scope.promise {
            try {
                val response = client.get("${ApiConstants.API_BASE_URL}/object") {
                    url {
                        parameters.append("worldId", worldId)
                    }
                    bearerAuth(UserApi.getToken() ?: "")
                }
                
                if (response.status.isSuccess()) {
                    response.body<List<Object>>()
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
    }
    
    /**
     * Gets an object by ID
     */
    fun getObject(id: String): Promise<Object> {
        return scope.promise {
            try {
                val response = client.get("${ApiConstants.API_BASE_URL}/object/$id")
                
                if (response.status.isSuccess()) {
                    response.body<Object>()
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
    }
    
    /**
     * Creates a new object
     */
    fun createObject(obj: Object): Promise<Object> {
        return scope.promise {
            try {
                val response = client.post("${ApiConstants.API_BASE_URL}/object") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(UserApi.getToken() ?: "")
                    setBody(obj)
                }
                
                if (response.status.isSuccess()) {
                    response.body<Object>()
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
    }
    
    /**
     * Updates an existing object
     */
    fun updateObject(id: String, obj: Object): Promise<Object> {
        return scope.promise {
            try {
                val response = client.post("${ApiConstants.API_BASE_URL}/object/$id") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(UserApi.getToken() ?: "")
                    setBody(obj)
                }
                
                if (response.status.isSuccess()) {
                    response.body<Object>()
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
    }
    
    /**
     * Deletes an object
     */
    fun deleteObject(id: String): Promise<Unit> {
        return scope.promise {
            try {
                val response = client.delete("${ApiConstants.API_BASE_URL}/object/$id") {
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
}
