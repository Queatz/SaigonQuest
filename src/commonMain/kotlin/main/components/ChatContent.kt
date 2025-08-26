package main.components

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
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.api.sendChatMessage
import main.api.sendTypingStatus
import main.components.chat.ChatMessages
import main.theme.Styles
import main.theme.Theme
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.flexWrap
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.maxHeight
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgb
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.w3c.dom.HTMLInputElement

/**
 * Content for the chat panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 * 
 * @param worldId The ID of the world to get messages for
 */
@Composable
fun ChatContent(worldId: String) {
    var messageText by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isWebSocketConnected by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var showEmotePopup by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    
    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()
    
    // Reference to the chat input element for auto-focus
    val chatInputRef = remember { mutableStateOf<HTMLInputElement?>(null) }
    
    // Function to handle typing status changes
    val updateTypingStatus = { newIsTyping: Boolean ->
        console.log("[TYPING] updateTypingStatus called with newIsTyping=$newIsTyping, current isTyping=$isTyping")
        
        if (isTyping != newIsTyping) {
            console.log("[TYPING] Typing status changing from $isTyping to $newIsTyping")
            isTyping = newIsTyping
            
            // Send typing status update
            if (isAuthenticated && isWebSocketConnected) {
                console.log("[TYPING] User is authenticated and WebSocket is connected, sending typing status")
                scope.launch {
                    val success = WebSocketApi.sendTypingStatus(worldId, newIsTyping)
                    console.log("[TYPING] Typing status update sent: $success")
                }
            } else {
                console.log("[TYPING] Cannot send typing status: authenticated=$isAuthenticated, connected=$isWebSocketConnected")
            }
        } else {
            console.log("[TYPING] Typing status unchanged: $isTyping")
        }
    }
    
    // Default emotes
    val defaultEmotes = remember {
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "👍", "👎", "👏", "🙌", "🤝", "👋", "🤘", "✌️", "🤞", "🤟"
        )
    }
    
    // Check if user is authenticated
    LaunchedEffect(Unit) {
        try {
            val user = UserApi.getCurrentUser()
            isAuthenticated = user.key.isNotEmpty()
        } catch (e: Throwable) {
            console.error("Error checking authentication status", e)
            isAuthenticated = false
        }
    }
    
    // Check WebSocket connection status periodically using ConnectionManager
    LaunchedEffect(Unit) {
        while (true) {
            isWebSocketConnected = WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED
            lastError = WebSocketApi.getLastError()
            
            // If not connected and we have a worldId, try to connect using ConnectionManager
            if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.DISCONNECTED && 
                worldId.isNotEmpty()) {
                console.log("WebSocket not connected, attempting to connect to world: $worldId")
                WebSocketConnectionManager.connectToWorld(worldId)
            }
            
            delay(10000) // Check every 10 seconds
        }
    }
    
    // Typing timeout coroutine
    LaunchedEffect(isTyping) {
        if (isTyping) {
            console.log("[TYPING] Starting typing timeout coroutine (5 seconds)")
            delay(5000) // 5 seconds timeout
            console.log("[TYPING] Typing timeout reached, setting typing status to false")
            updateTypingStatus(false)
        } else {
            console.log("[TYPING] Typing timeout coroutine not started (isTyping=false)")
        }
    }
    
    // Clean up typing status on dispose
    DisposableEffect(Unit) {
        console.log("[TYPING] Setting up DisposableEffect for cleanup")
        
        onDispose {
            console.log("[TYPING] DisposableEffect onDispose triggered")
            
            // Send typing stop if currently typing
            if (isTyping) {
                console.log("[TYPING] User was typing when component disposed, sending typing=false")
                scope.launch {
                    val success = WebSocketApi.sendTypingStatus(worldId, false)
                    console.log("[TYPING] Final typing status update sent: $success")
                }
            } else {
                console.log("[TYPING] User was not typing when component disposed, no cleanup needed")
            }
        }
    }
    
    LaunchedEffect(chatInputRef) {
        try {
            delay(100)
            chatInputRef.value?.focus()
        } catch (e: Throwable) {
            console.error("Error focusing chat input", e)
        }
    }
    
    // Chat messages area with ChatMessages component
    Div(
        attrs = {
            style {
                property("flex-grow", "1")
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                height(100.percent)
            }
        }
    ) {
        // Use the ChatMessages component to display messages
        ChatMessages(worldId = worldId)
    }
    
    // Chat input area
    Div(
        attrs = {
            style {
                padding(16.px)
                display(DisplayStyle.Flex)
                property("gap", "8px")
                property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
            }
        }
    ) {
        // If not connected, show only the connection status indicator
        if (!isWebSocketConnected) {
            Div(
                attrs = {
                    style {
                        padding(8.px, 16.px)
                        backgroundColor(rgba(255, 0, 0, 0.2))
                        color(rgb(255, 255, 255))
                        fontSize(14.px)
                        property("border-radius", "4px")
                        width(100.percent)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                    }
                }
            ) {
                Text(lastError ?: str("Connecting to chat server..."))
            }
        } else {
            // Emote button with popup
            Div(
                attrs = {
                    style {
                        position(Position.Relative)
                        property("flex-shrink", "0")
                    }
                }
            ) {
                // Emote button
                Div(
                    attrs = {
                        style {
                            width(40.px)
                            height(40.px)
                            backgroundColor(rgba(255, 255, 255, 0.1))
                            property("border-radius", "4px")
                            display(DisplayStyle.Flex)
                            justifyContent(JustifyContent.Center)
                            alignItems(AlignItems.Center)
                            cursor(if (isAuthenticated) "pointer" else "not-allowed")
                            opacity(if (isAuthenticated) 1.0 else 0.5)
                        }
                        onClick {
                            if (isAuthenticated) {
                                // Toggle emote popup
                                showEmotePopup = !showEmotePopup
                            }
                        }
                    }
                ) {
                    Text("😀")
                }
                
                // Emote popup
                if (showEmotePopup && isAuthenticated) {
                    Div(
                        attrs = {
                            style {
                                position(Position.Absolute)
                                bottom(45.px)
                                left(0.px)
                                width(250.px)
                                padding(8.px)
                                backgroundColor(rgba(40, 40, 40, 0.95))
                                property("border-radius", "8px")
                                property("box-shadow", "0 4px 8px rgba(0, 0, 0, 0.3)")
                                display(DisplayStyle.Flex)
                                flexWrap(FlexWrap.Wrap)
                                property("gap", "8px")
                                property("z-index", "10")
                                maxHeight(200.px)
                                property("overflow-y", "auto")
                            }
                            // Close popup when clicking outside
                            onClick { e ->
                                e.stopPropagation()
                            }
                        }
                    ) {
                        // Emote grid
                        defaultEmotes.forEach { emote ->
                            Div(
                                attrs = {
                                    classes(Styles.emoteButton)
                                    onClick { e ->
                                        e.stopPropagation()
                                        WebSocketApi.sendChatMessage(worldId, emote) { success ->
                                            if (success) {
                                                console.log("Emote sent successfully")
                                            } else {
                                                console.error("Failed to send emote: ${WebSocketApi.getLastError()}")
                                            }
                                        }
                                        showEmotePopup = false
                                    }
                                }
                            ) {
                                Text(emote)
                            }
                        }
                    }
                }
            }
            
            // Text input
            TextInput(
                value = messageText,
                attrs = {
                    classes(Styles.inputField)
                    style {
                        width(0.px)
                        property("flex-grow", "1")
                        property("border", "0")
                        opacity(if (isAuthenticated) 1.0 else 0.7)
                    }
                    placeholder(if (isAuthenticated) str("Type a message...") else str("Sign in to chat"))
                    onInput { 
                        val newValue = it.value
                        console.log("[TYPING] Input changed: '${messageText}' -> '${newValue}'")
                        messageText = newValue
                        
                        // Update typing status when input changes
                        if (isAuthenticated && newValue.isNotEmpty()) {
                            console.log("[TYPING] Input not empty and user authenticated, setting typing status to true")
                            // If not already typing, set typing to true
                            updateTypingStatus(true)
                        } else if (newValue.isEmpty() && isTyping) {
                            console.log("[TYPING] Input is now empty, setting typing status to false")
                            updateTypingStatus(false)
                        } else {
                            console.log("[TYPING] No typing status change needed: authenticated=$isAuthenticated, empty=${newValue.isEmpty()}, isTyping=$isTyping")
                        }
                    }
                    autoFocus()
                    if (!isAuthenticated) {
                        disabled()
                    }
                    // Add keypress event handler for Enter key using Kotlin-native onKeyDown
                    onKeyDown { event ->
                        if (event.key == "Enter") {
                            event.preventDefault()
                            if (isAuthenticated && messageText.isNotEmpty()) {
                                // Send message using WebSocketApi
                                console.log("Sending message via Enter key: $messageText")
                                val currentMessage = messageText // Capture current message text
                                WebSocketApi.sendChatMessage(worldId, currentMessage) { success ->
                                    if (success) {
                                        console.log("Message sent successfully via Enter key")
                                        messageText = ""
                                        
                                        // Set typing status to false when message is sent
                                        console.log("[TYPING] Message sent via Enter key, setting typing status to false")
                                        updateTypingStatus(false)
                                        
                                        // Mark messages as read when sending a message
                                        scope.launch {
                                            try {
                                                ChatApi.markMessagesAsRead(worldId)
                                            } catch (e: Throwable) {
                                                console.error("Error marking messages as read", e)
                                            }
                                        }
                                    } else {
                                        console.error("Failed to send message via Enter key: ${WebSocketApi.getLastError()}")
                                    }
                                }
                            }
                        }
                    }
                    // Use ref for auto-focus
                    ref { element -> 
                        chatInputRef.value = element
                        onDispose {
                            chatInputRef.value = null
                        }
                    }
                }
            )
            
            // Send button
            Button(
                attrs = {
                    style {
                        padding(8.px, 16.px)
                        backgroundColor(Theme.colors.primary)
                        color(Theme.colors.onPrimary)
                        property("border", "0")
                        property("border-radius", "4px")
                        cursor(if (isAuthenticated && messageText.isNotEmpty()) "pointer" else "not-allowed")
                        opacity(if (isAuthenticated && messageText.isNotEmpty()) 1.0 else 0.5)
                    }
                    if (!isAuthenticated) {
                        disabled()
                    }
                    onClick {
                        if (isAuthenticated && messageText.isNotEmpty()) {
                            // Send message using WebSocketApi
                            console.log("Attempting to send chat message: $messageText to world: $worldId")
                            val currentMessage = messageText // Capture current message text
                            WebSocketApi.sendChatMessage(worldId, currentMessage) { success ->
                                if (success) {
                                    console.log("Message sent successfully")
                                    messageText = ""
                                    
                                    // Set typing status to false when message is sent
                                    console.log("[TYPING] Message sent via Send button, setting typing status to false")
                                    updateTypingStatus(false)
                                    
                                    // Mark messages as read when sending a message
                                    scope.launch {
                                        try {
                                            ChatApi.markMessagesAsRead(worldId)
                                        } catch (e: Throwable) {
                                            console.error("Error marking messages as read", e)
                                        }
                                    }
                                } else {
                                    console.error("Failed to send message: ${WebSocketApi.getLastError()}")
                                }
                            }
                        }
                    }
                }
            ) {
                Translated("Send")
            }
        }
    }
}
