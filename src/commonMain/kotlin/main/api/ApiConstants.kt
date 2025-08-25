package main.api

/**
 * Constants for API operations
 * 
 * This file contains constants used across all API files to ensure consistency
 * and avoid duplication.
 */
@Suppress("UnsafeCastFromDynamic")
object ApiConstants {
    /**
     * Base URL for all API requests
     */
    val API_BASE_URL: String by lazy {
        val fromEnv = try {
            js("typeof process !== 'undefined' && process.env ? process.env.API_BASE_URL : null") as String?
        } catch (t: Throwable) {
            null
        }
        (fromEnv?.trimEnd('/') ?: "https://api.saigon.quest").ifBlank { "https://api.saigon.quest" }
    }
}
