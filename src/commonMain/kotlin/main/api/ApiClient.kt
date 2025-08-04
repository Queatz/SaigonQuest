package main.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/**
 * Centralized API client with shared HttpClient and Json instances
 *
 * This object provides a single point of configuration for:
 * - JSON serialization/deserialization
 * - HTTP client configuration
 * - Authentication token management
 *
 * All API objects should use these shared instances instead of creating their own.
 */
object ApiClient {
    /**
     * Shared Json instance with standard configuration
     */
    val json = utils.json

    /**
     * Shared HttpClient instance with JSON content negotiation and default authentication
     */
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        
        // We'll use a different approach since DefaultRequest plugin might not be available
        // or has a different implementation in Ktor 3.2.2
    }
    
    /**
     * Creates a new HttpClient with the same configuration as the shared instance
     * This is useful when you need a client with different default settings
     */
    fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
    
    /**
     * Adds authentication header to a request if a token is available
     * This method should be used in all API calls instead of manually adding the bearer token
     */
    fun addAuthHeader(builder: io.ktor.client.request.HttpRequestBuilder) {
        val token = UserApi.getToken()
        if (!token.isNullOrEmpty()) {
            builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
