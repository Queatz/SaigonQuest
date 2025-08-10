package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import main.components.player.FriendshipStatus
import models.Friend
import models.Notification
import models.NotificationType
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
    
    /**
     * Check if a friend request has been sent to another user
     *
     * @param friendId The ID of the other user
     * @return True if a friend request has been sent, false otherwise
     */
    private suspend fun checkRequestSent(friendId: String): Boolean {
        try {
            // Get notifications of type FRIEND_REQUEST
            val notifications = NotificationApi.getNotificationsByType(NotificationType.FRIEND_REQUEST)
            
            // Check if any of them have the specified friendId as otherUserId
            return notifications.any { it.otherUserId == friendId }
        } catch (e: Exception) {
            console.error("Error checking sent friend requests", e)
            return false
        }
    }
    
    /**
     * Check if a friend request has been received from another user
     *
     * @param friendId The ID of the other user
     * @return True if a friend request has been received, false otherwise
     */
    private suspend fun checkRequestReceived(friendId: String): Boolean {
        try {
            // Get notifications of type FRIEND_REQUEST
            val notifications = NotificationApi.getNotificationsByType(NotificationType.FRIEND_REQUEST)
            
            // Check if any of them have the specified friendId as otherUserId
            // and are not marked as read (a read notification means the request was handled)
            return notifications.any { it.otherUserId == friendId && !it.isRead }
        } catch (e: Exception) {
            console.error("Error checking received friend requests", e)
            return false
        }
    }
    
    /**
     * Get the friendship status with another user
     *
     * @param friendId The ID of the other user
     * @return The current friendship status
     */
    /**
     * Get online status for a list of friend IDs
     *
     * @param ids The list of friend user IDs to check
     * @return A map of friendId -> isOnline
     */
    suspend fun getFriendsOnline(ids: List<String>): Map<String, Boolean> {
        return try {
            if (ids.isEmpty()) return emptyMap()
            val idsParam = ids.joinToString(",")
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/friend/online") {
                ApiClient.addAuthHeader(this)
                url {
                    parameters.append("ids", idsParam)
                }
            }
            response.body<Map<String, Boolean>>()
        } catch (e: Exception) {
            console.error("Error fetching friends online", e)
            emptyMap()
        }
    }

    suspend fun getFriendshipStatus(friendId: String): FriendshipStatus {
        try {
            // Check if they are already friends
            if (checkFriendship(friendId)) {
                return FriendshipStatus.FRIENDS
            }
            
            // Check if a friend request has been sent
            if (checkRequestSent(friendId)) {
                return FriendshipStatus.REQUEST_SENT
            }
            
            // Check if a friend request has been received
            if (checkRequestReceived(friendId)) {
                return FriendshipStatus.REQUEST_RECEIVED
            }
            
            // If none of the above, they are not friends
            return FriendshipStatus.NOT_FRIENDS
        } catch (e: Exception) {
            console.error("Error getting friendship status", e)
            return FriendshipStatus.UNKNOWN
        }
    }
}

