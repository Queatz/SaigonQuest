package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import models.ChatMessage
import models.ChatReadMarker
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/chat/$worldId") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.body<Array<ChatMessage>>()
        } catch (e: Exception) {
            console.error("Error fetching chat messages", e)
            throw e
        }
    }
    
    /**
     * Get unread message count for a world
     *
     * @param worldId The ID of the world to get unread count for
     * @return The number of unread messages
     */
    suspend fun getUnreadMessageCount(worldId: String): Int {
        try {
            // Create a ChatReadMarker object to represent the query
            // We don't need to set userId as it will be determined from the token on the server
            val readMarker = ChatReadMarker(
                worldId = worldId
            )
            
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/chat/$worldId/unread-count") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.body<Int>()
        } catch (e: Exception) {
            console.error("Error fetching unread message count", e)
            return 0 // Return 0 on error to avoid UI issues
        }
    }
    
    /**
     * Mark messages as read in a world
     *
     * @param worldId The ID of the world to mark messages as read
     * @param timestamp Optional timestamp to use (defaults to current time)
     * @return True if successful, false otherwise
     */
    @OptIn(ExperimentalTime::class)
    suspend fun markMessagesAsRead(worldId: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        try {
            // Create a ChatReadMarker object
            // We don't need to set userId as it will be determined from the token on the server
            val readMarker = ChatReadMarker(
                worldId = worldId,
                lastReadTimestamp = timestamp
            )
            
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/chat/$worldId/mark-read?timestamp=$timestamp") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.status.isSuccess()
        } catch (e: Exception) {
            console.error("Error marking messages as read", e)
            return false
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
        try {
            // Create a copy of the message with updated text and edited flag
            val updatedMessage = message.copy(
                message = newText,
                edited = true
            )
            
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/chat/${message.key}") {
                ApiClient.addAuthHeader(this)
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
        try {
            // Create a copy of the message with deleted flag set to true and empty message
            val deletedMessage = message.copy(
                message = "",
                deleted = true,
                edited = false
            )
            
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/chat/${message.key}") {
                ApiClient.addAuthHeader(this)
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
