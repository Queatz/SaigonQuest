package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.NotificationApi
import main.theme.NotificationStyles
import main.utils.TimeUtils
import models.Notification
import models.NotificationType
import main.components.Translated
import Translations.str
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Span

/**
 * Content for the notifications panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 * 
 * @param onNotificationClick Callback when a notification is clicked
 */
@Composable
fun NotificationsContent(
    onNotificationClick: (Notification) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Load notifications when the panel is opened
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            error = null
            val result = NotificationApi.getNotifications()
            notifications = result.toList().sortedByDescending { it.timestamp }
            isLoading = false
        } catch (e: Throwable) {
            error = str("Failed to load notifications")
            isLoading = false
        }
    }
    
    // Function to mark a notification as read
    fun markAsRead(notification: Notification) {
        if (!notification.isRead) {
            scope.launch {
                val success = NotificationApi.markAsRead(notification.key)
                if (success) {
                    // Update the local state to show the notification as read
                    notifications = notifications.map {
                        if (it.key == notification.key) it.copy(isRead = true) else it
                    }
                }
                // Call the click handler regardless of read status
                onNotificationClick(notification)
            }
        } else {
            // If already read, just call the click handler
            onNotificationClick(notification)
        }
    }
    
    // Function to mark all notifications as read
    fun markAllAsRead() {
        scope.launch {
            val success = NotificationApi.markAllAsRead()
            if (success) {
                // Update all notifications to be marked as read
                notifications = notifications.map { it.copy(isRead = true) }
            }
        }
    }

    // Panel content
    Div(attrs = { classes(NotificationStyles.notificationsContainer) }) {
        // Mark all as read button (only show if there are unread notifications)
        if (notifications.any { !it.isRead }) {
            Div(attrs = { classes(NotificationStyles.markAllAsReadContainer) }) {
                Button(
                    attrs = {
                        classes(NotificationStyles.markAllAsReadButton)
                        onClick { markAllAsRead() }
                    }
                ) {
                    Translated("Mark all as read")
                }
            }
        }
        
        // Loading state
        if (isLoading) {
            Div(attrs = { classes(NotificationStyles.loadingContainer) }) {
                Translated("Loading notifications...")
            }
        }
        // Error state
        else if (error != null) {
            Div(attrs = { classes(NotificationStyles.errorContainer) }) {
                Text(error ?: str("An error occurred"))
            }
        }
        // Empty state
        else if (notifications.isEmpty()) {
            Div(attrs = { classes(NotificationStyles.emptyContainer) }) {
                Translated("No notifications")
            }
        }
        // Display notifications
        else {
            notifications.forEach { notification ->
                NotificationItem(
                    notification = notification,
                    onClick = { markAsRead(notification) }
                )
            }
        }
    }
}

/**
 * A single notification item
 * 
 * @param notification The notification to display
 * @param onClick Callback when the notification is clicked
 */
@Composable
private fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit
) {
    // Track if the notification status has changed
    var refreshKey by remember { mutableStateOf(0) }
    
    Div(
        attrs = {
            classes(
                NotificationStyles.notificationItem,
                if (notification.isRead)
                    NotificationStyles.notificationItemRead
                else
                    NotificationStyles.notificationItemUnread,
            )

            when (notification.type) {
                NotificationType.SYSTEM -> {
                    classes(NotificationStyles.notificationItemSystem)
                }
                NotificationType.CHAT_NEW -> {
                    classes(NotificationStyles.notificationItemChatNew)
                }
                NotificationType.FRIEND_REQUEST, NotificationType.FRIEND_REQUEST_ACCEPTED -> {
                    classes(NotificationStyles.notificationItemFriend)
                }
                else -> Unit
            }

            onClick { onClick() }
        }
    ) {
        // Notification header with title and timestamp
        Div(attrs = { classes(NotificationStyles.notificationHeader) }) {
            // Notification title
            H4(attrs = { classes(NotificationStyles.notificationTitle) }) {
                Text(notification.title)
            }
            
            // Timestamp
            P(attrs = { classes(NotificationStyles.notificationTimestamp) }) {
                when (notification.type) {
                    NotificationType.SYSTEM -> {
                        Span(attrs = { classes(NotificationStyles.systemBadge) }) {
                            Translated("System")
                        }
                    }
                    NotificationType.CHAT_NEW -> {
                        Span(attrs = { classes(NotificationStyles.chatBadge) }) {
                            Translated("Chat")
                        }
                    }
                    NotificationType.FRIEND_REQUEST, NotificationType.FRIEND_REQUEST_ACCEPTED -> {
                        Span(attrs = { classes(NotificationStyles.friendBadge) }) {
                            Translated("Friends")
                        }
                    }
                    else -> Unit
                }
                Text(TimeUtils.formatTimestamp(notification.timestamp))
            }
        }
        
        // Notification description
        P(attrs = { classes(NotificationStyles.notificationDescription) }) {
            Text(notification.description)
        }
        
        // Notification type-specific content
        when (notification.type) {
            NotificationType.FRIEND_REQUEST -> {
                FriendRequestNotificationContent(
                    notification = notification,
                    onStatusChange = { refreshKey++ }
                )
            }
            else -> {
                // No special content for other notification types
            }
        }
    }
}
