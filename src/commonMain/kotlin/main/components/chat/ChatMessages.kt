package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WorldApi
import main.api.deleteChatMessage
import main.api.updateChatMessage
import main.components.ImageDialog
import main.components.Md
import main.components.Translated
import main.components.common.Spinner
import main.theme.Styles
import models.ChatMessage
import models.ChatSenderType
import models.World
import models.canUserEdit
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
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
fun ChatMessages(
    worldId: String,
) {
    // Ensure data is loaded
    LaunchedEffect(worldId) {
        ChatStore.preload(worldId)
    }

    // Chat state from ChatStore
    val chatState = ChatStore.getState(worldId)
    val messages = chatState.messages
    val pins = chatState.pins
    val isLoading = chatState.isLoading.value
    val isLoadingMore = chatState.isLoadingMore.value
    val hasMore = chatState.hasMore.value
    val error = chatState.error.value
    val isOwner = chatState.isOwner.value
    val lastMutation = chatState.lastMutation.value
    val pinnedIds = remember(pins) { pins.map { it.messageId }.toSet() }

    // State for selected message (for editing/deleting)
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    // State for image dialog (selected image URL)
    var imageDialogUrl by remember { mutableStateOf<String?>(null) }

    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()

    // Reference to the messages container for scrolling
    val messagesContainerRef = remember { mutableStateOf<HTMLDivElement?>(null) }

    // Get current user to determine if this message is from the current user
    val currentUserId = remember { mutableStateOf("") }
    val world = remember { mutableStateOf<World?>(null) }

    LaunchedEffect(worldId) {
        try {
            val user = UserApi.getCurrentUser()
            currentUserId.value = user.key
            try {
                world.value = WorldApi.getWorld(worldId)
            } catch (e: Throwable) {
                console.error("[ChatMessages] Error getting world for permissions", e)
            }
        } catch (e: Throwable) {
            console.error("[ChatMessages] Error getting current user", e)
        }
    }

    // Function to scroll to the bottom of the messages container with smooth scrolling
    fun scrollToBottom() {
        try {
            messagesContainerRef.value?.let {
                it.scrollTo(0.0, it.scrollHeight.toDouble())
            }
        } catch (e: Throwable) {
            console.error("[ChatMessages] Error scrolling to bottom", e)
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

    // Helper: check if user is near the bottom to respect their reading position
    fun isNearBottom(thresholdPx: Double = 120.0): Boolean {
        return try {
            val el = messagesContainerRef.value ?: return false
            val remaining = el.scrollHeight - el.scrollTop - el.clientHeight
            remaining <= thresholdPx
        } catch (e: Throwable) {
            console.error("Error computing isNearBottom", e)
            false
        }
    }

    // Scroll to bottom when messages finish initial loading
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            scrollToBottom()
            awaitAnimationFrame()
            scrollToBottom()
        }
    }

    // Smooth scroll only for newly appended messages (not when loading older history)
    var lastCount by remember { mutableStateOf(0) }
    LaunchedEffect(messages.size, lastMutation) {
        if (lastCount != 0 && messages.size > lastCount) {
            val isAppend = (lastMutation == ChatStore.ChatMutationType.AppendNew)
            lastCount = messages.size
            if (isAppend && isNearBottom()) {
                smoothScrollToBottom()
            }
        } else {
            lastCount = messages.size
        }
    }

    // WebSocket listeners are handled globally by ChatStore

    // Show dialog for editing/deleting a message
    selectedMessage?.let { currentMessage ->
        ChatMessageDialog(
            message = currentMessage,
            isOwner = isOwner,
            isCurrentUser = (currentMessage.senderType == ChatSenderType.USER && currentMessage.senderId == currentUserId.value),
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

                        // Update the message in the store with the server response
                        ChatStore.replaceMessage(worldId, updatedMessage)
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

                        // Update the message in the store with the server response
                        ChatStore.replaceMessage(worldId, deletedMessage)
                    } catch (e: Throwable) {
                        console.error("Error deleting message in database", e)
                    }
                }
            },
            worldId = worldId,
            canEditWorld = world.value?.canUserEdit(currentUserId.value) ?: false,
        )
    }

    // Image preview dialog
    imageDialogUrl?.let { url ->
        ImageDialog(
            imageUrl = url,
            onClose = { imageDialogUrl = null }
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
                            if (msg != null && !msg.deleted) {
                                Md(msg.message)
                            } else {
                                Translated("Deleted")
                            }
                        }

                        // Attachments
                        if (msg != null && !msg.deleted && msg.attachments.isNotEmpty()) {
                            MessageAttachments(
                                attachments = msg.attachments,
                                onImageClick = { url -> imageDialogUrl = url },
                                onImageLoaded = { if (lastMutation == ChatStore.ChatMutationType.AppendNew && isNearBottom()) smoothScrollToBottom() },
                            )
                        }

                        if (isOwner) {
                            Div(attrs = {
                                classes(Styles.chatPinButton)

                                style { marginTop(8.px) }

                                onClick {
                                    // Optimistic update
                                    ChatStore.removePinByMessageId(pin.messageId)
                                    scope.launch {
                                        val ok = ChatApi.unpinMessage(worldId, pin.messageId)
                                        if (!ok) {
                                            try {
                                                ChatStore.setPins(worldId, ChatApi.listPins(worldId).toList())
                                            } catch (_: Throwable) {
                                            }
                                        }
                                    }
                                }
                            }) { Translated("Unpin") }
                        }
                    }
                }
            }
        }

        // Helper to load older messages when scrolled to the top, preserving scroll position
        fun tryLoadOlder() {
            val el = messagesContainerRef.value ?: return
            try {
                if (!hasMore || isLoadingMore) return
                // Trigger when near the top
                if (el.scrollTop <= 50.0) {
                    val prevScrollHeight = el.scrollHeight
                    val prevScrollTop = el.scrollTop
                    scope.launch {
                        val added = ChatStore.loadOlder(worldId)
                        if (added > 0) {
                            awaitAnimationFrame()
                            try {
                                val newScrollHeight = el.scrollHeight
                                val delta = newScrollHeight - prevScrollHeight
                                el.scrollTop = prevScrollTop + delta
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                console.error("Error attempting to load older messages", t)
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
                onScroll { tryLoadOlder() }
            }
        ) {
            if (isLoading && messages.isEmpty()) {
                // Use the reusable Spinner component
                Spinner(message = "Loading messages...")
            } else if (error != null) {
                // Error message
                P(
                    attrs = {
                        classes(Styles.chatErrorText)
                    }
                ) {
                    Text(error)
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
                if (isLoadingMore && hasMore) {
                    Spinner(message = "Loading older messages...")
                }
                messages.forEach { chatMessage ->
                    ChatMessageItem(
                        message = chatMessage,
                        onMessageClick = { message -> selectedMessage = message },
                        isOwner = isOwner,
                        isCurrentUser = (chatMessage.senderType == ChatSenderType.USER && currentUserId.value == chatMessage.senderId),
                        isPinned = pinnedIds.contains(chatMessage.key),
                        onPinToggle = {
                            scope.launch {
                                if (pinnedIds.contains(chatMessage.key)) {
                                    ChatStore.removePinByMessageId(chatMessage.key)
                                    val ok = ChatApi.unpinMessage(worldId, chatMessage.key)
                                    if (!ok) {
                                        try {
                                            ChatStore.setPins(worldId, ChatApi.listPins(worldId).toList())
                                        } catch (_: Throwable) {
                                        }
                                    }
                                } else {
                                    try {
                                        val pin = ChatApi.pinMessage(worldId, chatMessage.key)
                                        ChatStore.addPin(worldId, pin)
                                    } catch (e: Throwable) {
                                        console.error("Pin failed", e)
                                    }
                                }
                            }
                        },
                        onImageClick = { url -> imageDialogUrl = url },
                        onImageLoaded = { if (lastMutation == ChatStore.ChatMutationType.AppendNew && isNearBottom()) smoothScrollToBottom() },
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
    isCurrentUser: Boolean = false,
    isPinned: Boolean = false,
    onPinToggle: (() -> Unit)? = null,
    onImageClick: (String) -> Unit = {},
    onImageLoaded: () -> Unit = {},
) {
    Div(
        attrs = {
            classes(Styles.chatMessageItem)
            classes(if (isCurrentUser) Styles.chatMessageItemCurrentUser else Styles.chatMessageItemOtherUser)
            if (message.senderType == ChatSenderType.OBJECT) classes(Styles.chatMessageItemObject)
            if (message.senderType == ChatSenderType.SYSTEM) classes(Styles.chatMessageItemSystem)

            onClick { onMessageClick(message) }
        }
    ) {
        // Username and timestamp
        Div(
            attrs = {
                classes(Styles.chatMessageHeader)
            }
        ) {
            // Username
            if (message.senderType != ChatSenderType.SYSTEM) {
                Span(
                    attrs = {
                        classes(Styles.chatMessageUsername)
                        classes(if (isCurrentUser) Styles.chatMessageUsernameCurrentUser else Styles.chatMessageUsernameOtherUser)
                    }
                ) {
                    Text(message.senderName)
                }
            } else {
                // System message content is treated as the header
                MessageContent(message = message)
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
                            title(str("Edited"))
                        }
                    ) {
                        Text("•")
                    }
                }
                // Show shout indicator if the message was shouted
                if (message.shouted) {
                    Span(
                        attrs = {
                            classes(Styles.chatMessageEditedIndicator)
                            title(str("Shouted"))
                        }
                    ) {
                        Text("📣")
                    }
                }
                Text(formatTimestamp(message.timestamp.toEpochMilliseconds()))

                // Owner-only pin/unpin control
                if (isOwner && onPinToggle != null && message.senderType != ChatSenderType.SYSTEM) {
                    Span(attrs = {
                        classes(Styles.chatPinButton)

                        style {
                            marginLeft(8.px)
                        }

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
        
        if (message.senderType != ChatSenderType.SYSTEM) {
            // Message content
            MessageContent(message = message)
        }

        // Attachments
        if (!message.deleted && message.attachments.isNotEmpty()) {
            MessageAttachments(
                attachments = message.attachments,
                onImageClick = onImageClick,
                onImageLoaded = onImageLoaded,
            )
        }
    }
}

@Composable
private fun MessageContent(message: ChatMessage) {
    P(
        attrs = {
            classes(Styles.chatMessageContent)
            if (message.deleted) {
                classes(Styles.chatMessageContentDeleted)
            }
            if (message.senderType == ChatSenderType.SYSTEM) {
                classes(Styles.chatMessageContentSystem)
            }
        }
    ) {
        if (message.deleted) {
            Translated("Deleted")
        } else {
            Md(message.message)
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
