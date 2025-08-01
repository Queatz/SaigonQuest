package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import main.api.NotificationApi
import models.Notification
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

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
        } catch (e: Exception) {
            error = "Failed to load notifications"
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
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                padding(16.px)
                property("flex-grow", "1")
                overflowY("auto")
            }
        }
    ) {
        // Mark all as read button (only show if there are unread notifications)
        if (notifications.any { !it.isRead }) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.FlexEnd)
                        marginBottom(12.px)
                    }
                }
            ) {
                Button(
                    attrs = {
                        style {
                            backgroundColor(rgba(60, 60, 60, 0.8))
                            color(Color.white)
                            padding(8.px, 12.px)
                            borderRadius(4.px)
                            cursor("pointer")
                            property("border", "none")
                            property("transition", "background-color 0.2s")
                            property("&:hover", "{ background-color: rgba(80, 80, 80, 0.8); }")
                        }
                        onClick { markAllAsRead() }
                    }
                ) {
                    Text("Mark all as read")
                }
            }
        }
        
        // Loading state
        if (isLoading) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        padding(32.px)
                        color(rgba(255, 255, 255, 0.7))
                    }
                }
            ) {
                Text("Loading notifications...")
            }
        }
        // Error state
        else if (error != null) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        padding(32.px)
                        color(rgba(255, 0, 0, 0.7))
                    }
                }
            ) {
                Text(error ?: "An error occurred")
            }
        }
        // Empty state
        else if (notifications.isEmpty()) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        padding(32.px)
                        color(rgba(255, 255, 255, 0.7))
                    }
                }
            ) {
                Text("No notifications")
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
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                padding(12.px)
                marginBottom(8.px)
                backgroundColor(
                    if (notification.isRead) 
                        rgba(40, 40, 40, 0.6) 
                    else 
                        rgba(60, 60, 60, 0.8)
                )
                borderRadius(4.px)
                cursor("pointer")
                // Add hover effect
                property("transition", "background-color 0.2s")
                property("&:hover", "{ background-color: rgba(80, 80, 80, 0.8); }")
            }
            onClick { onClick() }
        }
    ) {
        // Notification header with title and timestamp
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
                    marginBottom(4.px)
                }
            }
        ) {
            // Notification title
            H4(
                attrs = {
                    style {
                        margin(0.px)
                        color(Color.white)
                        fontSize(16.px)
                    }
                }
            ) {
                Text(notification.title)
            }
            
            // Timestamp
            P(
                attrs = {
                    style {
                        margin(0.px)
                        color(rgba(255, 255, 255, 0.5))
                        fontSize(12.px)
                    }
                }
            ) {
                Text(formatTimestamp(notification.timestamp))
            }
        }
        
        // Notification description
        P(
            attrs = {
                style {
                    margin(0.px)
                    color(rgba(255, 255, 255, 0.8))
                    fontSize(14.px)
                }
            }
        ) {
            Text(notification.description)
        }
        
        // Notification type badge (if not "system")
        if (notification.type != "system") {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.InlineBlock)
                        marginTop(8.px)
                        padding(2.px, 6.px)
                        backgroundColor(rgba(80, 80, 80, 0.6))
                        borderRadius(4.px)
                        fontSize(12.px)
                        color(rgba(255, 255, 255, 0.7))
                    }
                }
            ) {
                Text(notification.type.replaceFirstChar { it.uppercase() })
            }
        }
    }
}

/**
 * Format a timestamp into a human-readable string
 * 
 * @param timestamp The timestamp in milliseconds
 * @return A human-readable string (e.g., "2 hours ago", "Yesterday", "Jul 29")
 */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun formatTimestamp(timestamp: Long): String {
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diff = now - timestamp
    
    // Less than a minute
    if (diff < 60 * 1000) {
        return "Just now"
    }
    
    // Less than an hour
    if (diff < 60 * 60 * 1000) {
        val minutes = diff / (60 * 1000)
        return "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
    }
    
    // Less than a day
    if (diff < 24 * 60 * 60 * 1000) {
        val hours = diff / (60 * 60 * 1000)
        return "$hours ${if (hours == 1L) "hour" else "hours"} ago"
    }
    
    // Less than 2 days
    if (diff < 2 * 24 * 60 * 60 * 1000) {
        return "Yesterday"
    }
    
    // Less than a week
    if (diff < 7 * 24 * 60 * 60 * 1000) {
        val days = diff / (24 * 60 * 60 * 1000)
        return "$days ${if (days == 1L) "day" else "days"} ago"
    }
    
    // For older dates, just return a simple date string
    // Create a Kotlin datetime object from the timestamp
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    
    // Get month name (abbreviated)
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val month = months[localDateTime.month.ordinal]
    
    // Get day of month
    val day = localDateTime.dayOfMonth
    
    return "$month $day"
}
