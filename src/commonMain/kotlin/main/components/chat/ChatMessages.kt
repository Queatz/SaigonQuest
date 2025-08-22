package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ChatApi
import main.api.WorldApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.deleteChatMessage
import main.api.updateChatMessage
import main.components.common.Spinner
import main.components.Translated
import main.theme.Styles
import models.ChatMessage
import models.ChatMessagePin
import models.WebSocketAction
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement
import web.animations.awaitAnimationFrame
import kotlin.js.Date

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
    // Pins state
    var pins by remember { mutableStateOf<List<ChatMessagePin>>(emptyList()) }
    val pinnedIds = remember(pins) { pins.map { it.messageId }.toSet() }
    // Ownership state
    var isOwner by remember { mutableStateOf(false) }
    
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
        } catch (e: Throwable) {
            console.error("Error scrolling to bottom", e)
        }
    }
    
    // Function to smooth scroll to the bottom of the messages container
    fun smoothScrollToBottom() {
        scope.launch {
            awaitAnimationFrame()
            try {
                messagesContainerRef.value?.let {
                    // Use scrollTo with behavior: smooth
                    js("it.scrollTo({top: it.scrollHeight, left: 0, behavior: 'smooth'})")
                }
            } catch (e: Throwable) {
                console.error("Error smooth scrolling to bottom", e)
                // Fallback to regular scrolling if smooth scrolling fails
                scrollToBottom()
            }
        }
    }
    
    // Load historical messages on component initialization
    LaunchedEffect(worldId) {
        isLoading = true
        error = null
        
        try {
            // Determine if current user is owner of this world
            try {
                val me = UserApi.getCurrentUser()
                val world = WorldApi.getWorld(worldId)
                isOwner = me.key == world.userId
            } catch (e: Throwable) {
                console.warn("Unable to determine ownership", e)
                isOwner = false
            }
            val chatMessages = ChatApi.getChatMessages(worldId)
            messages = chatMessages.toList().sortedBy { it.timestamp }
            // Load pins
            try {
                pins = ChatApi.listPins(worldId).toList()
            } catch (e: Throwable) {
                console.error("Failed to load pins", e)
            }
            isLoading = false
            
            // Mark messages as read when chat is opened
            scope.launch {
                try {
                    ChatApi.markMessagesAsRead(worldId)
                } catch (e: Throwable) {
                    console.error("Error marking messages as read", e)
                }
            }
            
            // Scroll to bottom after initial messages are loaded
            awaitAnimationFrame()
            delay(500)
            scrollToBottom()
        } catch (e: Throwable) {
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
            } catch (e: Throwable) {
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
            } catch (e: Throwable) {
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
            } catch (e: Throwable) {
                console.error("Error parsing chat message delete", e)
            }
        }
        
        // Add listeners for pin events
        WebSocketApi.addListener(WebSocketAction.PIN_ADDED) { message ->
            try {
                if (message.isValid()) {
                    val pin = ApiClient.json.decodeFromString<ChatMessagePin>(message.data)
                    if (pin.worldId == worldId) {
                        pins = (pins + pin).distinctBy { it.messageId }.sortedByDescending { it.pinnedAt }
                    }
                }
            } catch (e: Throwable) {
                console.error("Error handling PIN_ADDED", e)
            }
        }
        WebSocketApi.addListener(WebSocketAction.PIN_REMOVED) { message ->
            try {
                if (message.isValid()) {
                    val messageId = ApiClient.json.decodeFromString<String>(message.data)
                    pins = pins.filterNot { it.messageId == messageId }
                }
            } catch (e: Throwable) {
                console.error("Error handling PIN_REMOVED", e)
            }
        }

        // Remove listeners when component is disposed
        onDispose {
            WebSocketApi.removeAllListeners(WebSocketAction.RECEIVE_CHAT_MESSAGE)
            WebSocketApi.removeAllListeners(WebSocketAction.UPDATE_CHAT_MESSAGE)
            WebSocketApi.removeAllListeners(WebSocketAction.DELETE_CHAT_MESSAGE)
            WebSocketApi.removeAllListeners(WebSocketAction.PIN_ADDED)
            WebSocketApi.removeAllListeners(WebSocketAction.PIN_REMOVED)
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
                    } catch (e: Throwable) {
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
                    } catch (e: Throwable) {
                        console.error("Error deleting message in database", e)
                    }
                }
            }
        )
    }
    
    // Display messages
    Div(attrs = { classes(Styles.chatMessagesWrapper) }) {
        // Pinned section (outside scroll container so it stays fixed)
        if (!isLoading && pins.isNotEmpty()) {
            Div(attrs = { classes(Styles.chatPinnedSection) }) {
                Div(attrs = { classes(Styles.chatSectionHeader) }) { Translated("Pinned messages") }
                pins.forEach { pin ->
                    val msg = messages.find { it.key == pin.messageId }
                    Div(attrs = { classes(Styles.chatMessageItem) }) {
                        P(attrs = { classes(Styles.chatMessageText) }) {
                            if (msg != null && !msg.deleted) Text(msg.message) else Text("(message)")
                        }
                        if (isOwner) {
                            Span(attrs = {
                                classes(Styles.chatPinButton)
                                onClick {
                                    // Optimistic update
                                    pins = pins.filterNot { it.messageId == pin.messageId }
                                    scope.launch {
                                        val ok = ChatApi.unpinMessage(worldId, pin.messageId)
                                        if (!ok) {
                                            try { pins = ChatApi.listPins(worldId).toList() } catch (_: Throwable) {}
                                        }
                                    }
                                }
                            }) { Translated("Unpin") }
                        }
                    }
                }
            }
        }

        // Scrollable messages container
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
                    onMessageClick = { message -> selectedMessage = message },
                    isOwner = isOwner,
                    isPinned = pinnedIds.contains(chatMessage.key),
                    onPinToggle = {
                        scope.launch {
                            if (pinnedIds.contains(chatMessage.key)) {
                                pins = pins.filterNot { it.messageId == chatMessage.key }
                                val ok = ChatApi.unpinMessage(worldId, chatMessage.key)
                                if (!ok) { try { pins = ChatApi.listPins(worldId).toList() } catch (_: Throwable) {} }
                            } else {
                                try {
                                    val pin = ChatApi.pinMessage(worldId, chatMessage.key)
                                    pins = (pins + pin).distinctBy { it.messageId }.sortedByDescending { it.pinnedAt }
                                } catch (e: Throwable) { console.error("Pin failed", e) }
                            }
                        }
                    }
                )
            }
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
    onMessageClick: (ChatMessage) -> Unit,
    isOwner: Boolean = false,
    isPinned: Boolean = false,
    onPinToggle: (() -> Unit)? = null,
) {
    // Get current user to determine if this message is from the current user
    val currentUserId = remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        try {
            val user = UserApi.getCurrentUser()
            currentUserId.value = user.key
        } catch (e: Throwable) {
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
                Text(formatTimestamp(message.timestamp.toEpochMilliseconds()))

                // Owner-only pin/unpin control
                if (isOwner && onPinToggle != null) {
                    Span(attrs = {
                        classes(Styles.chatPinButton)
                        onClick {
                            it.stopPropagation()
                            onPinToggle()
                        }
                    }) {
                        Text(" ")
                        if (isPinned) Translated("Unpin") else Translated("Pin")
                    }
                }
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
            val months = arrayOf(
                str("Jan"),
                str("Feb"),
                str("Mar"),
                str("Apr"),
                str("May"),
                str("Jun"),
                str("Jul"),
                str("Aug"),
                str("Sep"),
                str("Oct"),
                str("Nov"),
                str("Dec"),
            )
            val month = months[date.getMonth()]
            val day = date.getDate().toString()
            "$month $day, $timeString"
        }
    }
}
