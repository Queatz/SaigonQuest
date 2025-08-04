package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import main.components.common.Spinner
import models.ChatMessage
import models.WebSocketAction
import kotlin.js.js
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontStyle
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.opacity
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
import main.theme.Styles
import org.w3c.dom.HTMLDivElement

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
    
    // State for selected message (for editing/deleting)
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()
    
    // Reference to the messages container for scrolling
    val messagesContainerRef = remember { mutableStateOf<HTMLDivElement?>(null) }
    
    // Function to scroll to the bottom of the messages container with smooth scrolling
    fun scrollToBottom() {
        try {
            messagesContainerRef.value?.let {
                it.scrollTo(0.0, it.scrollHeight.toDouble())
            }
        } catch (e: Exception) {
            console.error("Error scrolling to bottom", e)
        }
    }
    
    // Function to smooth scroll to the bottom of the messages container
    fun smoothScrollToBottom() {
        try {
            messagesContainerRef.value?.let {
                // Use scrollTo with behavior: smooth
                js("it.scrollTo({top: it.scrollHeight, left: 0, behavior: 'smooth'})")
            }
        } catch (e: Exception) {
            console.error("Error smooth scrolling to bottom", e)
            // Fallback to regular scrolling if smooth scrolling fails
            scrollToBottom()
        }
    }
    
    // Load historical messages on component initialization
    LaunchedEffect(worldId) {
        isLoading = true
        error = null
        
        try {
            val chatMessages = ChatApi.getChatMessages(worldId)
            messages = chatMessages.toList().sortedBy { it.timestamp }
            isLoading = false
            
            // Mark messages as read when chat is opened
            scope.launch {
                try {
                    ChatApi.markMessagesAsRead(worldId)
                } catch (e: Exception) {
                    console.error("Error marking messages as read", e)
                }
            }
            
            // Scroll to bottom after initial messages are loaded
            // Add a small delay to ensure DOM is updated before scrolling
            scope.launch {
                try {
                    // Wait for the next frame to ensure DOM is updated
                    kotlinx.coroutines.delay(50)
                    scrollToBottom()
                    
                    // Add a second attempt with a longer delay as a fallback
                    kotlinx.coroutines.delay(100)
                    scrollToBottom()
                } catch (e: Exception) {
                    console.error("Error in delayed scroll to bottom", e)
                }
            }
        } catch (e: Exception) {
            console.error("Error loading chat messages", e)
            error = "Failed to load messages"
            isLoading = false
        }
    }
    
    // Set up WebSocket listener for new messages
    DisposableEffect(worldId) {
        // Add listener for new chat messages
        WebSocketApi.addListener(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                // Check if the message is valid
                if (message.isValid()) {
                    val chatMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    messages = (messages + chatMessage).sortedBy { msg -> msg.timestamp }
                    
                    // Smooth scroll to bottom when a new message is received
                    smoothScrollToBottom()
                } else {
                    console.error("Invalid chat message received: ${message.action}, ${message.data}")
                }
            } catch (e: Exception) {
                console.error("Error parsing chat message", e)
            }
        }
        
        // Add listener for updated chat messages
        WebSocketApi.addListener(WebSocketAction.UPDATE_CHAT_MESSAGE) { message ->
            try {
                // Check if the message is valid
                if (message.isValid()) {
                    val updatedMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    
                    // Find and replace the message in the list
                    messages = messages.map { msg ->
                        if (msg.key == updatedMessage.key) updatedMessage else msg
                    }
                } else {
                    console.error("Invalid chat message update received: ${message.action}, ${message.data}")
                }
            } catch (e: Exception) {
                console.error("Error parsing chat message update", e)
            }
        }
        
        // Add listener for deleted chat messages
        WebSocketApi.addListener(WebSocketAction.DELETE_CHAT_MESSAGE) { message ->
            try {
                // Check if the message is valid
                if (message.isValid()) {
                    val deletedMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    
                    // Find and replace the message in the list
                    messages = messages.map { msg ->
                        if (msg.key == deletedMessage.key) deletedMessage else msg
                    }
                } else {
                    console.error("Invalid chat message delete received: ${message.action}, ${message.data}")
                }
            } catch (e: Exception) {
                console.error("Error parsing chat message delete", e)
            }
        }
        
        // Remove listeners when component is disposed
        onDispose {
            WebSocketApi.removeAllListeners(WebSocketAction.RECEIVE_CHAT_MESSAGE)
            WebSocketApi.removeAllListeners(WebSocketAction.UPDATE_CHAT_MESSAGE)
            WebSocketApi.removeAllListeners(WebSocketAction.DELETE_CHAT_MESSAGE)
        }
    }
    
    // Smooth scroll to bottom whenever messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isLoading) {
            smoothScrollToBottom()
        }
    }
    
    // Show dialog for editing/deleting a message
    selectedMessage?.let { currentMessage ->
        ChatMessageDialog(
            message = currentMessage,
            onClose = { selectedMessage = null },
            onEdit = { newText ->
                // Update the message using WebSocketApi
                WebSocketApi.updateChatMessage(currentMessage, newText) { success ->
                    if (success) {
                        console.log("Message updated successfully")
                    } else {
                        console.error("Failed to update message: ${WebSocketApi.getLastError() ?: "Unknown error"}")
                    }
                }
                
                // Also update via ChatApi for persistence
                scope.launch {
                    try {
                        val updatedMessage = ChatApi.updateChatMessage(currentMessage, newText)
                        console.log("Message updated in database")
                        
                        // Update the message in the UI with the server response
                        messages = messages.map { msg ->
                            if (msg.key == updatedMessage.key) updatedMessage else msg
                        }
                    } catch (e: Exception) {
                        console.error("Error updating message in database", e)
                    }
                }
            },
            onDelete = {
                // Delete the message using WebSocketApi
                WebSocketApi.deleteChatMessage(currentMessage) { success ->
                    if (success) {
                        console.log("Message deleted successfully")
                    } else {
                        console.error("Failed to delete message: ${WebSocketApi.getLastError() ?: "Unknown error"}")
                    }
                }
                
                // Also delete via ChatApi for persistence
                scope.launch {
                    try {
                        val deletedMessage = ChatApi.deleteChatMessage(currentMessage)
                        console.log("Message deleted in database")
                        
                        // Update the message in the UI with the server response
                        messages = messages.map { msg ->
                            if (msg.key == deletedMessage.key) deletedMessage else msg
                        }
                    } catch (e: Exception) {
                        console.error("Error deleting message in database", e)
                    }
                }
            }
        )
    }
    
    // Display messages
    Div(
        attrs = {
            classes(Styles.chatMessagesContainer)
            ref { element -> 
                messagesContainerRef.value = element
                onDispose { }
            }
        }
    ) {
        if (isLoading) {
            // Use the reusable Spinner component
            Spinner(message = "Loading messages...")
        } else if (error != null) {
            // Error message
            P(
                attrs = {
                    classes(Styles.chatErrorText)
                }
            ) {
                Text(error ?: "Unknown error")
            }
        } else if (messages.isEmpty()) {
            // Empty state
            P(
                attrs = {
                    classes(Styles.chatEmptyText)
                }
            ) {
                Text("No messages yet")
            }
        } else {
            // Display messages
            messages.forEach { chatMessage ->
                ChatMessageItem(
                    message = chatMessage,
                    onMessageClick = { message ->
                        selectedMessage = message
                    }
                )
            }
        }
    }
}

/**
 * A component that displays a single chat message.
 *
 * @param message The chat message to display
 * @param onMessageClick Callback when the message is clicked (only for user's own messages)
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onMessageClick: (ChatMessage) -> Unit
) {
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
            classes(Styles.chatMessageItem)
            classes(if (isCurrentUser) Styles.chatMessageItemCurrentUser else Styles.chatMessageItemOtherUser)
            
            // Add click handler for user's own messages
            if (isCurrentUser) {
                onClick { onMessageClick(message) }
            }
        }
    ) {
        // Username and timestamp
        Div(
            attrs = {
                classes(Styles.chatMessageHeader)
            }
        ) {
            // Username
            Span(
                attrs = {
                    classes(Styles.chatMessageUsername)
                    classes(if (isCurrentUser) Styles.chatMessageUsernameCurrentUser else Styles.chatMessageUsernameOtherUser)
                }
            ) {
                Text(message.username)
            }
            
            // Timestamp with edited indicator
            Span(
                attrs = {
                    classes(Styles.chatMessageTimestamp)
                }
            ) {
                // Show edited indicator if the message has been edited
                if (message.edited) {
                    Span(
                        attrs = {
                            classes(Styles.chatMessageEditedIndicator)
                        }
                    ) {
                        Text("•")
                    }
                }
                
                Text(formatTimestamp(message.timestamp))
            }
        }
        
        // Message content
        P(
            attrs = {
                classes(Styles.chatMessageContent)
                if (message.deleted) {
                    classes(Styles.chatMessageContentDeleted)
                }
            }
        ) {
            if (message.deleted) {
                Text("Deleted")
            } else {
                Text(message.message)
            }
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
    
    // Format time as HH:MM using standard JavaScript methods
    val hours = date.getHours().toString().padStart(2, '0')
    val minutes = date.getMinutes().toString().padStart(2, '0')
    val timeString = "$hours:$minutes"
    
    return when {
        messageDate.getTime() == today.getTime() -> "Today, $timeString"
        messageDate.getTime() == yesterday.getTime() -> "Yesterday, $timeString"
        else -> {
            // Format date as MMM DD using standard JavaScript methods
            val month = when(date.getMonth()) {
                0 -> "Jan"; 1 -> "Feb"; 2 -> "Mar"; 3 -> "Apr"; 4 -> "May"; 5 -> "Jun"
                6 -> "Jul"; 7 -> "Aug"; 8 -> "Sep"; 9 -> "Oct"; 10 -> "Nov"; else -> "Dec"
            }
            val day = date.getDate().toString()
            "$month $day, $timeString"
        }
    }
}
