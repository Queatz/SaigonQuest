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
import main.api.ContentApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.api.sendChatMessage
import main.api.sendTypingStatus
import main.components.chat.ChatMessages
import main.components.chat.RichTextEditorDialog
import main.components.media.UploadMediaDialog
import main.theme.Styles
import main.theme.Theme
import models.chat.ImageAttachment
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
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
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
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
    var isPlayer by remember { mutableStateOf(false) }
    var isWebSocketConnected by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var showEmotePopup by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var shoutNext by remember { mutableStateOf(false) }
    // Subscription state
    var isSubscribed by remember { mutableStateOf(true) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var willSubscribe by remember { mutableStateOf<Boolean?>(null) }
    // Rich text editor dialog state
    var showRichEditorDialog by remember { mutableStateOf(false) }

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
            isPlayer = user.isPlayer
        } catch (e: Throwable) {
            console.error("Error checking authentication status", e)
            isAuthenticated = false
        }
    }

    // Fetch subscription status when authenticated
    LaunchedEffect(isAuthenticated, worldId) {
        if (isAuthenticated && worldId.isNotEmpty()) {
            try {
                isSubscribed = ChatApi.getSubscription(worldId)
            } catch (e: Throwable) {
                console.error("Failed to fetch subscription", e)
                isSubscribed = true
            }
        }
    }

    // Check WebSocket connection status periodically using ConnectionManager
    LaunchedEffect(Unit) {
        while (true) {
            isWebSocketConnected =
                WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED
            lastError = WebSocketApi.getLastError()

            // If not connected and we have a worldId, try to connect using ConnectionManager
            if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.DISCONNECTED &&
                worldId.isNotEmpty()
            ) {
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

    // Subscription bell header
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.FlexEnd)
                alignItems(AlignItems.Center)
                padding(8.px)
                property("border-bottom", "1px solid rgba(255, 255, 255, 0.1)")
            }
        }
    ) {
        // Bell button
        Div(
            attrs = {
                title(str(if (isSubscribed) "Subscribed" else "Not subscribed"))
                style {
                    width(36.px)
                    height(36.px)
                    backgroundColor(rgba(255, 255, 255, 0.1))
                    borderRadius(8.px)
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.Center)
                    alignItems(AlignItems.Center)
                    cursor(if (isAuthenticated) "pointer" else "not-allowed")
                    opacity(if (isAuthenticated) 1.0 else 0.5)
                }
                onClick {
                    if (isAuthenticated) {
                        willSubscribe = !isSubscribed
                        showSubscriptionDialog = true
                    }
                }
            }
        ) {
            Text(if (isSubscribed) "🔔" else "🔕")
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
        // Mark messages as read when chat opens
        LaunchedEffect(Unit) {
            try {
                ChatApi.markMessagesAsRead(worldId)
            } catch (t: Throwable) {
                console.error("Error marking messages as read on open", t)
            }
        }

        // Use the ChatMessages component to display messages
        ChatMessages(
            worldId = worldId,
        )
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
                        borderRadius(8.px)
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
            if (messageText.isBlank()) {
                // Emote button with popup
                Div(
                    attrs = {
                        classes(Styles.emoteTriggerWrapper)
                    }
                ) {
                    // Emote button
                    Div(
                        attrs = {
                            classes(Styles.emoteTriggerButton)
                            if (!isAuthenticated) {
                                classes(Styles.disabledAction)
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
                                classes(Styles.emotePopupContainer)
                                // Keep clicks inside the popup from bubbling out
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
                                            WebSocketApi.sendChatMessage(
                                                worldId,
                                                emote,
                                                shouted = shoutNext
                                            ) { success ->
                                                if (success) {
                                                    console.log("Emote sent successfully")
                                                    shoutNext = false
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

                // Upload image button
                if (isPlayer) {
                    Div(
                        attrs = {
                            style {
                                width(40.px)
                                height(40.px)
                                backgroundColor(rgba(255, 255, 255, 0.1))
                                borderRadius(8.px)
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.Center)
                                alignItems(AlignItems.Center)
                                cursor(if (isAuthenticated) "pointer" else "not-allowed")
                                opacity(if (isAuthenticated) 1.0 else 0.5)
                            }
                            onClick {
                                if (isAuthenticated) {
                                    showUploadDialog = true
                                }
                            }
                        }
                    ) {
                        Text("🖼️")
                    }
                }
            }

            // Shout toggle button
            Div(
                attrs = {
                    title(str("Shout"))
                    style {
                        width(40.px)
                        height(40.px)
                        backgroundColor(if (shoutNext) rgba(255, 255, 255, 0.25) else rgba(255, 255, 255, 0.1))
                        borderRadius(8.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        cursor(if (isAuthenticated) "pointer" else "not-allowed")
                        opacity(if (isAuthenticated) 1.0 else 0.5)
                    }
                    onClick {
                        if (isAuthenticated) {
                            shoutNext = !shoutNext
                        }
                    }
                }
            ) {
                Text("📣")
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
                    placeholder(if (isAuthenticated) str("Message") else str("Sign in to chat"))
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
                                WebSocketApi.sendChatMessage(worldId, currentMessage, shouted = shoutNext) { success ->
                                    if (success) {
                                        console.log("Message sent successfully via Enter key")
                                        messageText = ""
                                        shoutNext = false

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
                    // Handle paste of image from clipboard
                    onPaste { event ->
                        try {
                            val dt = event.clipboardData
                            val files = dt?.files
                            val length = files?.length ?: 0
                            for (i in 0 until length) {
                                val file = files?.item(i)
                                if (file != null && file.type.startsWith("image/")) {
                                    event.preventDefault()
                                    if (isAuthenticated && isPlayer) {
                                        scope.launch {
                                            try {
                                                val token = UserApi.getToken() ?: ""
                                                val record = ContentApi.upload(file, token)
                                                val finalUrl = ContentApi.imageUrlFor(record.uploadedFilePath)
                                                val attachments = listOf(
                                                    ImageAttachment(
                                                        url = finalUrl,
                                                    ),
                                                )
                                                ChatApi.createMessage(
                                                    worldId = worldId,
                                                    message = "",
                                                    attachments = attachments,
                                                )
                                            } catch (t: Throwable) {
                                                console.error("Failed to send pasted image", t)
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        } catch (t: Throwable) {
                            console.error("Error handling paste event", t)
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

            // Send button or rich editor launcher
            if (messageText.isBlank()) {
                Button(
                    attrs = {
                        style {
                            padding(8.px, 12.px)
                            backgroundColor(Theme.colors.primary)
                            color(Theme.colors.onPrimary)
                            property("border", "0")
                            borderRadius(8.px)
                            cursor(if (isAuthenticated) "pointer" else "not-allowed")
                            opacity(if (isAuthenticated) 1.0 else 0.5)
                        }
                        if (!isAuthenticated) {
                            disabled()
                        }
                        onClick {
                            if (isAuthenticated) {
                                showRichEditorDialog = true
                            }
                        }
                    }
                ) {
                    Text("📝")
                }
            } else {
                Button(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(Theme.colors.primary)
                            color(Theme.colors.onPrimary)
                            property("border", "0")
                            borderRadius(8.px)
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
                                WebSocketApi.sendChatMessage(worldId, currentMessage, shouted = shoutNext) { success ->
                                    if (success) {
                                        console.log("Message sent successfully")
                                        messageText = ""
                                        shoutNext = false

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

            // Upload media dialog
            if (showUploadDialog) {
                UploadMediaDialog(
                    onClose = { showUploadDialog = false },
                    onSelect = { imageUrl ->
                        scope.launch {
                            try {
                                val attachments = listOf(
                                    ImageAttachment(
                                        url = imageUrl,
                                    ),
                                )
                                ChatApi.createMessage(
                                    worldId = worldId,
                                    message = "",
                                    attachments = attachments,
                                )
                            } catch (t: Throwable) {
                                console.error("Failed to send image message", t)
                            } finally {
                                showUploadDialog = false
                            }
                        }
                    },
                )
            }

            // Rich text editor dialog
            if (showRichEditorDialog) {
                RichTextEditorDialog(
                    onClose = { showRichEditorDialog = false },
                    onSend = { markdown ->
                        if (isAuthenticated && markdown.isNotBlank()) {
                            WebSocketApi.sendChatMessage(worldId, markdown, shouted = shoutNext) { success ->
                                if (success) {
                                    // Reset states on success
                                    shoutNext = false
                                    updateTypingStatus(false)
                                    scope.launch {
                                        try {
                                            ChatApi.markMessagesAsRead(worldId)
                                        } catch (e: Throwable) {
                                            console.error("Error marking messages as read", e)
                                        }
                                    }
                                } else {
                                    console.error("Failed to send rich text message: ${WebSocketApi.getLastError()}")
                                }
                                showRichEditorDialog = false
                            }
                        }
                    },
                )
            }
        }
    }

    // Subscription confirm dialog
    if (showSubscriptionDialog && willSubscribe != null) {
        BasicConfirmDialog(
            message = str(if (willSubscribe == true) "Subscribe to this world?" else "Unsubscribe from this world?"),
            onConfirm = {
                scope.launch {
                    try {
                        val ok = if (willSubscribe == true) ChatApi.subscribe(worldId) else ChatApi.unsubscribe(worldId)
                        if (ok) {
                            isSubscribed = (willSubscribe == true)
                        }
                    } catch (t: Throwable) {
                        console.error("Failed to update subscription", t)
                    } finally {
                        showSubscriptionDialog = false
                    }
                }
            },
            onCancel = {
                showSubscriptionDialog = false
            },
        )
    }
}
