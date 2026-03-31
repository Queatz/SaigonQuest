package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import models.Friend
import models.Notification
import models.NotificationType
import models.UnreadCountResponse

/**
 * API client for notification operations
 *
 * This API provides methods for retrieving and managing user notifications.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 * It also provides methods for handling friend request notifications.
 */
object NotificationApi {
    @Serializable
    data class SystemNotificationRequest(
        val username: String,
        val title: String,
        val description: String
    )
    
    @Serializable
    data class WorldInvitationRequest(
        val friendId: String,
        val worldId: String,
        val x: Float,
        val y: Float,
        val z: Float
    )
    
    /**
     * Get notifications for the current user
     *
     * This retrieves all notifications for the currently authenticated user.
     *
     * @return An array of Notification objects
     */
    suspend fun getNotifications(): Array<Notification> {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/notification") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.body<Array<Notification>>()
        } catch (e: Throwable) {
            console.error("Error fetching notifications", e)
            return emptyArray()
        }
    }

    /**
     * Send a system notification to a user (admin only)
     */
    suspend fun sendSystemNotification(username: String, title: String, description: String): Boolean {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/notification/system") {
                ApiClient.addAuthHeader(this)
                contentType(ContentType.Application.Json)
                setBody(SystemNotificationRequest(username, title, description))
            }

            return response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error sending system notification", e)
            return false
        }
    }

    /**
     * Mark a notification as read
     *
     * @param notificationId The ID of the notification to mark as read
     * @return True if the operation was successful, false otherwise
     */
    suspend fun markAsRead(notificationId: String): Boolean {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/notification/$notificationId/read") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error marking notification as read", e)
            return false
        }
    }
    
    /**
     * Mark all notifications as read for the current user
     *
     * @return True if the operation was successful, false otherwise
     */
    suspend fun markAllAsRead(): Boolean {
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/notification/read-all") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error marking all notifications as read", e)
            return false
        }
    }

    /**
     * Send a world invitation to a friend
     *
     * @param friendId The ID of the friend to invite
     * @param worldId The ID of the world to invite them to
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param z The Z coordinate
     * @return True if successful
     */
    suspend fun sendWorldInvitation(friendId: String, worldId: String, x: Float, y: Float, z: Float): Boolean {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/friend/invite") {
                ApiClient.addAuthHeader(this)
                contentType(ContentType.Application.Json)
                setBody(WorldInvitationRequest(friendId, worldId, x, y, z))
            }
            return response.status.isSuccess()
        } catch (e: Throwable) {
            console.error("Error sending world invitation", e)
            return false
        }
    }
    
    /**
     * Get the count of unread notifications for the current user
     *
     * @return The number of unread notifications
     */
    suspend fun getUnreadCount(): Int {
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/notification/unread-count") {
                ApiClient.addAuthHeader(this)
            }
            
            return response.body<UnreadCountResponse>().unreadCount
        } catch (e: Throwable) {
            console.error("Error fetching unread notification count", e)
            return 0
        }
    }
    
    /**
     * Get notifications of a specific type
     *
     * @param type The type of notifications to retrieve as an enum
     * @return An array of Notification objects of the specified type
     */
    suspend fun getNotificationsByType(type: NotificationType): Array<Notification> {
        try {
            val allNotifications = getNotifications()
            return allNotifications.filter { it.type == type }.toTypedArray()
        } catch (e: Throwable) {
            console.error("Error fetching notifications by type", e)
            return emptyArray()
        }
    }
    
    /**
     * Accept a friend request notification
     *
     * @param notification The friend request notification to accept
     * @return The created friendship, or null if the request failed
     */
    suspend fun acceptFriendRequest(notification: Notification): Friend? {
        if (notification.type != NotificationType.FRIEND_REQUEST) {
            console.error("Not a friend request notification")
            return null
        }
        try {
            // Find the concrete request id via server-driven status
            val dto = FriendApi.getFriendshipStatusDto(notification.otherUserId)
            val requestId = dto?.requestId
            if (dto == null || requestId == null) {
                console.error("No pending friend request found for notification")
                return null
            }
            val friend = FriendApi.acceptRequest(requestId)
            if (friend != null) {
                // Mark the notification as read only after successful accept
                markAsRead(notification.key)
            }
            return friend
        } catch (e: Throwable) {
            console.error("Error accepting friend request", e)
            return null
        }
    }
    
    /**
     * Reject a friend request notification
     *
     * @param notification The friend request notification to reject
     * @return True if the notification was marked as read, false otherwise
     */
    suspend fun rejectFriendRequest(notification: Notification): Boolean {
        if (notification.type != NotificationType.FRIEND_REQUEST) {
            console.error("Not a friend request notification")
            return false
        }
        try {
            val dto = FriendApi.getFriendshipStatusDto(notification.otherUserId)
            val requestId = dto?.requestId
            if (dto == null || requestId == null) {
                console.error("No pending friend request found for notification")
                return false
            }
            val ok = FriendApi.declineRequest(requestId)
            if (ok) {
                markAsRead(notification.key)
            }
            return ok
        } catch (e: Throwable) {
            console.error("Error rejecting friend request", e)
            return false
        }
    }
}
