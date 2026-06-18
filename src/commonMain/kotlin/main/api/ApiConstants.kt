package main.api

/**
 * Constants for API operations
 * 
 * This file contains constants used across all API files to ensure consistency
 * and avoid duplication.
 */
object ApiConstants {
    val API_BASE_URL: String by lazy {
        val fromEnv = try {
            js("process.env.API_SERVER_URL") as String?
        } catch (t: Throwable) {
            null
        }
        fromEnv?.trimEnd('/').orEmpty().ifBlank { "http://0.0.0.0:8080" }
    }
}
