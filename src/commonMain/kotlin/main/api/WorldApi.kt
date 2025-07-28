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
import models.World
import kotlin.js.Promise
import main.api.ApiConstants

/**
 * API client for world operations
 */
object WorldApi {
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
     * Gets all worlds for the current user
     */
    fun getWorlds(): Promise<List<World>> {
        return scope.promise {
            try {
                val response = client.get("${ApiConstants.API_BASE_URL}/world") {
                    bearerAuth(UserApi.getToken() ?: "")
                }
                
                if (response.status.isSuccess()) {
                    response.body<List<World>>()
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
    }
    
    /**
     * Gets a world by ID
     */
    fun getWorld(id: String): Promise<World> {
        return scope.promise {
            try {
                val response = client.get("${ApiConstants.API_BASE_URL}/world/$id")
                
                if (response.status.isSuccess()) {
                    response.body<World>()
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
    }
    
    /**
     * Creates a new world
     */
    fun createWorld(world: World): Promise<World> {
        return scope.promise {
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
                
                val response = client.post("${ApiConstants.API_BASE_URL}/world") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(UserApi.getToken() ?: "")
                    setBody(world)
                }
                
                if (response.status.isSuccess()) {
                    response.body<World>()
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
    }
    
    /**
     * Updates an existing world
     */
    fun updateWorld(id: String, world: World): Promise<World> {
        return scope.promise {
            try {
                val response = client.post("${ApiConstants.API_BASE_URL}/world/$id") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(UserApi.getToken() ?: "")
                    setBody(world)
                }
                
                if (response.status.isSuccess()) {
                    response.body<World>()
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
    }
    
    /**
     * Gets a world by URL
     * @param url The URL of the world to get. Empty string is valid for Home World.
     * @return A Promise that resolves to the world, or rejects if the world is not found
     */
    fun getWorldByUrl(url: String): Promise<World> {
        return scope.promise {
            try {
                // Validate URL - only allow alphanumeric characters, hyphens, and underscores
                if (url.isNotEmpty() && !url.matches(Regex("^[a-zA-Z0-9_-]*$"))) {
                    throw Error("Invalid URL format. URLs can only contain letters, numbers, hyphens, and underscores.")
                }
                
                val response = client.get("${ApiConstants.API_BASE_URL}/world/url") {
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
                    
                    world
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
}
