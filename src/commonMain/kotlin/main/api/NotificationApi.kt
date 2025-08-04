package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import models.Friend
import models.Notification

/**
 * API client for notification operations
 *
 * This API provides methods for retrieving and managing user notifications.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
 * It also provides methods for handling friend request notifications.
 */
object NotificationApi {
    
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
        } catch (e: Exception) {
            console.error("Error fetching notifications", e)
            return emptyArray()
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            console.error("Error marking all notifications as read", e)
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
            
            return response.body<Int>()
        } catch (e: Exception) {
            console.error("Error fetching unread notification count", e)
            return 0
        }
    }
    
    /**
     * Get notifications of a specific type
     *
     * @param type The type of notifications to retrieve (e.g., "friend_request")
     * @return An array of Notification objects of the specified type
     */
    suspend fun getNotificationsByType(type: String): Array<Notification> {
        try {
            val allNotifications = getNotifications()
            return allNotifications.filter { it.type == type }.toTypedArray()
        } catch (e: Exception) {
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
        if (notification.type != "friend_request") {
            console.error("Not a friend request notification")
            return null
        }
        
        try {
            // Mark the notification as read
            markAsRead(notification.key)
            
            // Accept the friend request
            return FriendApi.acceptFriendRequest(notification.relatedId)
        } catch (e: Exception) {
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
        if (notification.type != "friend_request") {
            console.error("Not a friend request notification")
            return false
        }
        
        try {
            // Just mark the notification as read to reject it
            return markAsRead(notification.key)
        } catch (e: Exception) {
            console.error("Error rejecting friend request", e)
            return false
        }
    }
}
