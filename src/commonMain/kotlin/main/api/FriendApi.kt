package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import main.components.player.FriendshipStatus
import models.Friend
import models.FriendRequest
import models.FriendshipState
import models.FriendshipStatusDto
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
        } catch (e: Throwable) {
            console.error("Error fetching friends", e)
            return emptyArray()
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
            console.error("Error fetching friends online", e)
            emptyMap()
        }
    }

    suspend fun getFriendshipStatus(friendId: String): FriendshipStatus {
        return try {
            val dto: FriendshipStatusDto = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/friendships/status/$friendId") {
                ApiClient.addAuthHeader(this)
            }.body()
            when (dto.status) {
                FriendshipState.Friends -> FriendshipStatus.FRIENDS
                FriendshipState.IncomingRequest -> FriendshipStatus.REQUEST_RECEIVED
                FriendshipState.OutgoingRequest -> FriendshipStatus.REQUEST_SENT
                FriendshipState.NotFriends -> FriendshipStatus.NOT_FRIENDS
            }
        } catch (e: Throwable) {
            console.error("Error getting friendship status", e)
            FriendshipStatus.UNKNOWN
        }
    }

    suspend fun getFriendshipStatusDto(friendId: String): FriendshipStatusDto? {
        return try {
            ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/friendships/status/$friendId") {
                ApiClient.addAuthHeader(this)
            }.body()
        } catch (e: Throwable) {
            console.error("Error getting friendship status dto", e)
            null
        }
    }

    // New Friend Requests API (server-driven)
    suspend fun getPendingIncoming(): List<FriendRequest> {
        return try {
            ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/friend-requests?direction=in") {
                ApiClient.addAuthHeader(this)
            }.body()
        } catch (e: Throwable) {
            console.error("Error fetching incoming friend requests", e)
            emptyList()
        }
    }

    suspend fun getPendingOutgoing(): List<FriendRequest> {
        return try {
            ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/friend-requests?direction=out") {
                ApiClient.addAuthHeader(this)
            }.body()
        } catch (e: Throwable) {
            console.error("Error fetching outgoing friend requests", e)
            emptyList()
        }
    }

    suspend fun sendFriendRequestNew(toUserId: String): FriendRequest? {
        return try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend-requests") {
                ApiClient.addAuthHeader(this)
                contentType(ContentType.Application.Json)
                setBody(mapOf("toUserId" to toUserId))
            }
            if (response.status == HttpStatusCode.NoContent) return null
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Throwable) {
            console.error("Error sending friend request (new endpoint)", e)
            null
        }
    }

    suspend fun acceptRequest(id: String): Friend? {
        return try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend-requests/$id/accept") {
                ApiClient.addAuthHeader(this)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Throwable) {
            console.error("Error accepting friend request", e)
            null
        }
    }

    suspend fun declineRequest(id: String): Boolean {
        return try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend-requests/$id/decline") {
                ApiClient.addAuthHeader(this)
            }
            response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error declining friend request", e)
            false
        }
    }

    suspend fun cancelRequest(id: String): Boolean {
        return try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend-requests/$id/cancel") {
                ApiClient.addAuthHeader(this)
            }
            response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error cancelling friend request", e)
            false
        }
    }
}

