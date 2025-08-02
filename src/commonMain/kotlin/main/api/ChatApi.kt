package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import models.ChatMessage

/**
 * API client for chat operations
 *
 * This API provides methods for retrieving, updating, and deleting chat messages.
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
    
    /**
     * Update a chat message
     *
     * This updates the content of a chat message and marks it as edited.
     *
     * @param message The chat message to update
     * @param newText The new text for the message
     * @return The updated ChatMessage object
     */
    suspend fun updateChatMessage(message: ChatMessage, newText: String): ChatMessage {
        val token = UserApi.getToken() ?: ""
        
        try {
            // Create a copy of the message with updated text and edited flag
            val updatedMessage = message.copy(
                message = newText,
                edited = true
            )
            
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/chat/${message.key}") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ApiClient.json.encodeToString(updatedMessage))
            }
            
            return response.body<ChatMessage>()
        } catch (e: Exception) {
            console.error("Error updating chat message", e)
            throw e
        }
    }
    
    /**
     * Delete a chat message
     *
     * This marks a chat message as deleted and clears its content.
     *
     * @param message The chat message to delete
     * @return The updated ChatMessage object with deleted flag set to true
     */
    suspend fun deleteChatMessage(message: ChatMessage): ChatMessage {
        val token = UserApi.getToken() ?: ""
        
        try {
            // Create a copy of the message with deleted flag set to true and empty message
            val deletedMessage = message.copy(
                message = "",
                deleted = true,
                edited = false
            )
            
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/chat/${message.key}") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ApiClient.json.encodeToString(deletedMessage))
            }
            
            return response.body<ChatMessage>()
        } catch (e: Exception) {
            console.error("Error deleting chat message", e)
            throw e
        }
    }
}
