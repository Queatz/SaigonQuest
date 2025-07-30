package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.browser.window
import models.ChatMessage

/**
 * API client for chat operations
 *
 * This API provides methods for retrieving chat messages.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object ChatApi {
    
    /**
     * Get chat messages for a world
     *
     * This retrieves chat messages for a specific world, up to 7 days old and a maximum of 100 messages.
     *
     * @param worldId The ID of the world to get messages for
     * @return An array of ChatMessage objects
     */
    suspend fun getChatMessages(worldId: String): Array<ChatMessage> {
        val token = UserApi.getToken() ?: ""
        
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/chat/$worldId") {
                bearerAuth(token)
            }
            
            return response.body<Array<ChatMessage>>()
        } catch (e: Exception) {
            console.error("Error fetching chat messages", e)
            throw e
        }
    }
}
