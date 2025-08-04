package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.w3c.dom.HTMLInputElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.chat.ChatMessages
import main.theme.Styles
import main.theme.Theme
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

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
    
    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()
    
    // Reference to the chat input element for auto-focus
    val chatInputRef = remember { mutableStateOf<HTMLInputElement?>(null) }
    
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
        } catch (e: Exception) {
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
    
    LaunchedEffect(chatInputRef) {
        try {
            delay(100)
            chatInputRef.value?.focus()
        } catch (e: Exception) {
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
                Text(lastError ?: "Connecting to chat server...")
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
                                    style {
                                        width(48.px)
                                        height(48.px)
                                        display(DisplayStyle.Flex)
                                        justifyContent(JustifyContent.Center)
                                        alignItems(AlignItems.Center)
                                        fontSize(32.px)
                                        cursor("pointer")
                                        property("border-radius", "4px")
                                        property("transition", "background-color 0.2s")
                                        property(":hover", "{ background-color: rgba(255, 255, 255, 0.1); }")
                                    }
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
            Input(
                type = InputType.Text,
                attrs = {
                    classes(Styles.inputField)
                    style {
                        width(0.px)
                        property("flex-grow", "1")
                        property("border", "0")
                        opacity(if (isAuthenticated) 1.0 else 0.7)
                    }
                    placeholder(if (isAuthenticated) "Type a message..." else "Sign in to chat")
                    value(messageText)
                    onInput { messageText = it.value }
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
                                        
                                        // Mark messages as read when sending a message
                                        scope.launch {
                                            try {
                                                ChatApi.markMessagesAsRead(worldId)
                                            } catch (e: Exception) {
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
                                    
                                    // Mark messages as read when sending a message
                                    scope.launch {
                                        try {
                                            ChatApi.markMessagesAsRead(worldId)
                                        } catch (e: Exception) {
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
                Text("Send")
            }
        }
    }
}
