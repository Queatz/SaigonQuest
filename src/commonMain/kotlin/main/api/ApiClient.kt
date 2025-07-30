package main.api

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Centralized API client with shared HttpClient and Json instances
 *
 * This object provides a single point of configuration for:
 * - JSON serialization/deserialization
 * - HTTP client configuration
 *
 * All API objects should use these shared instances instead of creating their own.
 */
object ApiClient {
    /**
     * Shared Json instance with standard configuration
     */
    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * Shared HttpClient instance with JSON content negotiation
     */
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }
}
