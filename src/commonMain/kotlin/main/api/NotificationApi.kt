package main.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.browser.window
import models.Notification

/**
 * API client for notification operations
 *
 * This API provides methods for retrieving and managing user notifications.
 * It uses the shared ApiClient for HTTP requests and coroutines for asynchronous operations.
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
        val token = UserApi.getToken() ?: ""
        
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/notification") {
                bearerAuth(token)
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
        val token = UserApi.getToken() ?: ""
        
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/notification/$notificationId/read") {
                bearerAuth(token)
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
        val token = UserApi.getToken() ?: ""
        
        try {
            val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/notification/read-all") {
                bearerAuth(token)
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
        val token = UserApi.getToken() ?: ""
        
        try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/notification/unread-count") {
                bearerAuth(token)
            }
            
            return response.body<Int>()
        } catch (e: Exception) {
            console.error("Error fetching unread notification count", e)
            return 0
        }
    }
}
