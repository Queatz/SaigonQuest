package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import models.Friend
import models.Notification
import models.Position

/**
 * API client for friend operations
 *
 * This API provides methods for managing friendships, including sending and accepting friend requests,
 * retrieving friends, and removing friends.
 */
object FriendApi {
    
    /**
     * Get all friends for the current user
     *
     * @return An array of Friend objects
     */
    suspend fun getFriends(): Array<Friend> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/friend") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.body<Array<Friend>>()
        } catch (e: Exception) {
            console.error("Error fetching friends", e)
            return emptyArray()
        }
    }
    
    /**
     * Send a friend request to another user
     *
     * @param friendId The ID of the user to send the request to
     * @return The created notification, or null if the request failed
     */
    suspend fun sendFriendRequest(friendId: String): Notification? {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend/request") {
                ApiClient.addAuthHeader(this)
                contentType(ContentType.Application.Json)
                setBody(mapOf("friendId" to friendId))
            }
            
            if (response.status.isSuccess()) {
                return response.body<Notification>()
            }
            
            return null
        } catch (e: Exception) {
            console.error("Error sending friend request", e)
            return null
        }
    }
    
    /**
     * Accept a friend request
     *
     * @param friendId The ID of the user who sent the request
     * @return The created friendship, or null if the request failed
     */
    suspend fun acceptFriendRequest(friendId: String): Friend? {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend/accept") {
                ApiClient.addAuthHeader(this)
                contentType(ContentType.Application.Json)
                setBody(mapOf("friendId" to friendId))
            }
            
            if (response.status.isSuccess()) {
                return response.body<Friend>()
            }
            
            return null
        } catch (e: Exception) {
            console.error("Error accepting friend request", e)
            return null
        }
    }
    
    /**
     * Remove a friend
     *
     * @param friendId The ID of the friend to remove
     * @return True if the friend was removed, false otherwise
     */
    suspend fun removeFriend(friendId: String): Boolean {
        try {
            val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/friend/$friendId") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.status.isSuccess()
        } catch (e: Exception) {
            console.error("Error removing friend", e)
            return false
        }
    }
    
    /**
     * Update the current user's status (world and position)
     *
     * @param worldId The ID of the world
     * @param worldName The name of the world
     * @param position The position in the world
     * @return True if the status was updated, false otherwise
     */
    suspend fun updateStatus(worldId: String, worldName: String, position: Position?): Boolean {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/friend/status") {
                ApiClient.addAuthHeader(this)
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "worldId" to worldId,
                    "worldName" to worldName,
                    "position" to position
                ))
            }
            
            return response.status.isSuccess()
        } catch (e: Exception) {
            console.error("Error updating friend status", e)
            return false
        }
    }
    
    /**
     * Check if a friendship exists with another user
     *
     * @param friendId The ID of the other user
     * @return True if a friendship exists, false otherwise
     */
    suspend fun checkFriendship(friendId: String): Boolean {
        try {
            val friends = getFriends()
            return friends.any { it.friendId == friendId }
        } catch (e: Exception) {
            console.error("Error checking friendship", e)
            return false
        }
    }
}
