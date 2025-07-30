package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import main.api.ApiClient
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import models.ChatMessage
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Date
import kotlin.time.Duration.Companion.milliseconds

/**
 * A component that displays chat messages and handles sending new messages.
 *
 * @param worldId The ID of the world to get messages for
 */
@Composable
fun ChatMessages(worldId: String) {
    // State for chat messages
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Load historical messages on component initialization
    LaunchedEffect(worldId) {
        isLoading = true
        error = null
        
        try {
            val chatMessages = ChatApi.getChatMessages(worldId)
            messages = chatMessages.toList().sortedBy { it.timestamp }
            isLoading = false
        } catch (e: Exception) {
            console.error("Error loading chat messages", e)
            error = "Failed to load messages"
            isLoading = false
        }
    }
    
    // Set up WebSocket listener for new messages
    DisposableEffect(worldId) {
        // Add listener for chat messages
        WebSocketApi.addListener("receiveChatMessage") { message ->
            try {
                val chatMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                messages = (messages + chatMessage).sortedBy { msg -> msg.timestamp }
            } catch (e: Exception) {
                console.error("Error parsing chat message", e)
            }
        }
        
        // Remove listener when component is disposed
        onDispose {
            WebSocketApi.removeAllListeners("receiveChatMessage")
        }
    }
    
    // Display messages
    Div(
        attrs = {
            style {
                padding(16.px)
                property("overflow-y", "auto")
                property("flex-grow", "1")
                display(DisplayStyle.Flex)
                property("flex-direction", "column")
            }
        }
    ) {
        if (isLoading) {
            // Loading indicator with spinner
            Div(
                attrs = {
                    style {
                        // Spinner container
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        gap(16.px)
                        width(100.percent)
                    }
                }
            ) {
                // Spinner element
                Div(
                    attrs = {
                        style {
                            width(40.px)
                            height(40.px)
                            property("border", "4px solid rgba(255, 255, 255, 0.1)")
                            property("border-top", "4px solid rgba(255, 255, 255, 0.8)")
                            property("border-radius", "50%")
                            property("animation", "spin 1s linear infinite")
                            // Define the animation
                            property("@keyframes spin", "{0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); }}")
                        }
                    }
                )
                
                // Loading text
                P(
                    attrs = {
                        style {
                            color(rgba(255, 255, 255, 0.5))
                            property("text-align", "center")
                            margin(0.px)
                        }
                    }
                ) {
                    Text("Loading messages...")
                }
            }
        } else if (error != null) {
            // Error message
            P(
                attrs = {
                    style {
                        color(rgba(255, 0, 0, 0.7))
                        property("text-align", "center")
                    }
                }
            ) {
                Text(error ?: "Unknown error")
            }
        } else if (messages.isEmpty()) {
            // Empty state
            P(
                attrs = {
                    style {
                        color(rgba(255, 255, 255, 0.5))
                        property("text-align", "center")
                    }
                }
            ) {
                Text("No messages yet")
            }
        } else {
            // Display messages
            messages.forEach { chatMessage ->
                ChatMessageItem(chatMessage)
            }
        }
    }
}

/**
 * A component that displays a single chat message.
 *
 * @param message The chat message to display
 */
@Composable
fun ChatMessageItem(message: ChatMessage) {
    // Get current user to determine if this message is from the current user
    val currentUserId = remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        try {
            val user = UserApi.getCurrentUser()
            currentUserId.value = user.key
        } catch (e: Exception) {
            console.error("Error getting current user", e)
        }
    }
    
    val isCurrentUser = message.userId == currentUserId.value
    
    Div(
        attrs = {
            style {
                marginBottom(12.px)
                padding(8.px)
                property("border-radius", "4px")
                backgroundColor(if (isCurrentUser) rgba(0, 100, 200, 0.1) else rgba(50, 50, 50, 0.2))
            }
        }
    ) {
        // Username and timestamp
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    property("justify-content", "space-between")
                    marginBottom(4.px)
                }
            }
        ) {
            // Username
            Span(
                attrs = {
                    style {
                        fontWeight("bold")
                        color(if (isCurrentUser) rgba(100, 200, 255, 1.0) else rgba(200, 200, 200, 1.0))
                    }
                }
            ) {
                Text(message.username)
            }
            
            // Timestamp
            Span(
                attrs = {
                    style {
                        fontSize(12.px)
                        color(rgba(150, 150, 150, 0.8))
                    }
                }
            ) {
                Text(formatTimestamp(message.timestamp))
            }
        }
        
        // Message content
        P(
            attrs = {
                style {
                    margin(0.px)
                    color(Color.white)
                }
            }
        ) {
            Text(message.message)
        }
    }
}

/**
 * Formats a timestamp as a human-readable string.
 *
 * @param timestamp The timestamp in milliseconds
 * @return A formatted string like "Today, 2:30 PM" or "Yesterday, 3:45 PM" or "Jul 15, 4:20 PM"
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val today = Date(now.getFullYear(), now.getMonth(), now.getDate())
    val yesterday = Date(today.getTime() - 86400000) // 24 hours in milliseconds
    
    val messageDate = Date(date.getFullYear(), date.getMonth(), date.getDate())
    
    val timeString = date.toLocaleTimeString(arrayOf("hour", "minute"))
    
    return when {
        messageDate.getTime() == today.getTime() -> "Today, $timeString"
        messageDate.getTime() == yesterday.getTime() -> "Yesterday, $timeString"
        else -> "${date.toLocaleDateString(arrayOf("month", "day"))}, $timeString"
    }
}
