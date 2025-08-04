package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import models.ErrorResponse
import models.World

/**
 * API client for world operations
 * 
 * This API provides methods for managing worlds.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object WorldApi {
    
    /**
     * Gets all worlds for the current user
     */
    suspend fun getWorlds(): List<World> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/world") {
                ApiClient.addAuthHeader(this)
            }
            
            if (response.status.isSuccess()) {
                return response.body<List<World>>()
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
            console.error("Error fetching worlds", e)
            throw e
        }
    }
    
    /**
     * Gets a world by ID
     */
    suspend fun getWorld(id: String): World {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/world/$id")
            
            if (response.status.isSuccess()) {
                return response.body<World>()
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
            console.error("Error fetching world", e)
            throw e
        }
    }
    
    /**
     * Creates a new world
     */
    suspend fun createWorld(world: World): World {
        try {
            // Validate required fields
            if (world.title.isEmpty()) {
                throw Error("Title is required")
            }
            
            if (world.file.isEmpty()) {
                throw Error("File is required")
            }
            
            // Validate URL format if provided
            if (world.url.isNotEmpty() && !world.url.matches(Regex("^[a-zA-Z0-9_-]*$"))) {
                throw Error("Invalid URL format. URLs can only contain letters, numbers, hyphens, and underscores.")
            }
            
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/world") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(world)
            }
            
            if (response.status.isSuccess()) {
                return response.body<World>()
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    if (response.status == HttpStatusCode.Unauthorized) {
                        ErrorResponse(message = "You must be logged in to create a world")
                    } else {
                        ErrorResponse(message = response.bodyAsText())
                    }
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
            console.error("Error creating world", e)
            throw e
        }
    }
    
    /**
     * Updates an existing world
     */
    suspend fun updateWorld(id: String, world: World): World {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/world/$id") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(world)
            }
            
            if (response.status.isSuccess()) {
                return response.body<World>()
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
            console.error("Error updating world", e)
            throw e
        }
    }
    
    /**
     * Gets a world by URL
     * @param url The URL of the world to get. Empty string is valid for Home World.
     * @return The world, or throws an error if the world is not found
     */
    suspend fun getWorldByUrl(url: String): World {
        try {
            // Validate URL - only allow alphanumeric characters, hyphens, and underscores
            if (url.isNotEmpty() && !url.matches(Regex("^[a-zA-Z0-9_-]*$"))) {
                throw Error("Invalid URL format. URLs can only contain letters, numbers, hyphens, and underscores.")
            }
            
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/world/url") {
                url {
                    parameters.append("url", url)
                }
            }
            
            if (response.status.isSuccess()) {
                val world = response.body<World>()
                
                // Validate world data
                if (world.key.isEmpty()) {
                    throw Error("Invalid world data: Missing key")
                }
                
                return world
            } else {
                // Parse error response
                val errorResponse = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    // Fallback if response is not a valid ErrorResponse
                    if (response.status == HttpStatusCode.NotFound) {
                        ErrorResponse(message = "World not found")
                    } else {
                        ErrorResponse(message = response.bodyAsText())
                    }
                }
                throw Error(errorResponse.message)
            }
        } catch (e: Exception) {
            console.error("Error fetching world by URL", e)
            throw e
        }
    }
}
