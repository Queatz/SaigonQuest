package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import models.ChatMessage
import models.chat.Attachment
import models.ChatReadMarker
import models.ChatMessagePin
import models.chat.CreateChatMessageRequest
import kotlin.time.Clock

import kotlin.time.Instant

/**
 * API client for chat operations
 *
 * This API provides methods for retrieving, updating, and deleting chat messages.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 */
object ChatApi {

    suspend fun getSubscription(worldId: String): Boolean {
        return try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/chat/$worldId/subscription") {
                ApiClient.addAuthHeader(this)
            }
            val map = response.body<Map<String, Boolean>>()
            map["subscribed"] ?: true
        } catch (e: Throwable) {
            console.error("Error fetching chat subscription", e)
            true
        }
    }

    suspend fun subscribe(worldId: String): Boolean {
        return try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/chat/$worldId/subscribe") {
                ApiClient.addAuthHeader(this)
            }
            response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error subscribing to world chat", e)
            false
        }
    }

    suspend fun unsubscribe(worldId: String): Boolean {
        return try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/chat/$worldId/subscribe") {
                ApiClient.addAuthHeader(this)
            }
            response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error unsubscribing from world chat", e)
            false
        }
    }

    suspend fun createMessage(
        worldId: String,
        message: String,
        attachments: List<Attachment> = emptyList(),
        senderType: models.ChatSenderType = models.ChatSenderType.USER,
        senderId: String = "",
    ): ChatMessage {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/chat/$worldId") {
            ApiClient.addAuthHeader(this)
            contentType(ContentType.Application.Json)
            setBody(CreateChatMessageRequest(
                message = message,
                attachments = attachments,
                senderType = senderType,
                senderId = senderId,
            ))
        }
        return response.body()
    }
    
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
        } catch (e: Throwable) {
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
            // Validate worldId to prevent empty value causing URL issues
            if (worldId.isBlank()) {
                console.error("Error: worldId cannot be empty when fetching unread message count")
                return 0
            }
            
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/chat/$worldId/unread-count") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.body<Int>()
        } catch (e: Throwable) {
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
    
    suspend fun markMessagesAsRead(worldId: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        try {
            // Create a ChatReadMarker object
            // We don't need to set userId as it will be determined from the token on the server
            val readMarker = ChatReadMarker(
                worldId = worldId,
                lastReadTimestamp = Instant.fromEpochMilliseconds(timestamp)
            )
            
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/chat/$worldId/mark-read?timestamp=$timestamp") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.status.isSuccess()
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
            console.error("Error deleting chat message", e)
            throw e
        }
    }

    /**
     * List pinned messages for a world
     */
    suspend fun listPins(worldId: String, limit: Int = 50, offset: Int = 0): Array<ChatMessagePin> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/chat/$worldId/pins?limit=$limit&offset=$offset") {
                ApiClient.addAuthHeader(this)
            }
            return response.body<Array<ChatMessagePin>>()
        } catch (e: Throwable) {
            console.error("Error fetching chat pins", e)
            throw e
        }
    }

    /**
     * Pin a message (owner only)
     */
    suspend fun pinMessage(worldId: String, messageId: String): ChatMessagePin {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/chat/$worldId/messages/$messageId/pin") {
                ApiClient.addAuthHeader(this)
            }
            return response.body<ChatMessagePin>()
        } catch (e: Throwable) {
            console.error("Error pinning message", e)
            throw e
        }
    }

    /**
     * Unpin a message (owner only)
     */
    suspend fun unpinMessage(worldId: String, messageId: String): Boolean {
        return try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/chat/$worldId/messages/$messageId/pin") {
                ApiClient.addAuthHeader(this)
            }
            response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error unpinning message", e)
            false
        }
    }
}
