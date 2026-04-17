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
import main.api.ChatApi
import main.api.ContentApi
import main.api.UserApi
import main.api.UserEmojiHistoryApi
import main.api.WebSocketApi
import main.api.sendChatMessage
import main.api.sendTypingStatus
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogInput
import main.components.Translated
import main.components.media.AudioRecorderUI
import main.components.media.UploadMediaDialog
import main.components.media.findMediaInClipboardEvent
import main.components.media.rememberAudioRecorderState
import main.theme.Styles
import models.MediaType
import models.UserEmojiHistory
import models.WebSocketAction
import models.chat.ImageAttachment
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.flexShrink
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgb
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement

@Composable
fun ChatComposer(
    worldId: String,
    isAuthenticated: Boolean,
    isPlayer: Boolean,
    isWebSocketConnected: Boolean,
    lastError: String?,
    isDialog: Boolean = false,
    onClose: () -> Unit,
) {
    var initialRichText by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var showEmotePopup by remember(messageText.isBlank()) { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var shoutNext by remember { mutableStateOf(false) }
    var showRichEditorDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val chatInputRef = remember { mutableStateOf<HTMLInputElement?>(null) }
    
    var userEmojiHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    
    DisposableEffect(isAuthenticated) {
        var token: main.api.ListenerToken? = null
        if (isAuthenticated) {
            scope.launch {
                try {
                    val history = UserEmojiHistoryApi.getHistory()
                    userEmojiHistory = history.emojis
                } catch (e: Throwable) {
                    console.error("Failed to load emoji history", e)
                }
            }
            
            token = WebSocketApi.addListenerWithToken(WebSocketAction.UPDATE_EMOJI_HISTORY) { message ->
                try {
                    val history = utils.json.decodeFromString(UserEmojiHistory.serializer(), message.data)
                    userEmojiHistory = history.emojis
                } catch (e: Throwable) {
                    console.error("Failed to decode emoji history update", e)
                }
            }
        }
        onDispose {
            token?.let { WebSocketApi.removeListener(it) }
        }
    }

    val recorderState = rememberAudioRecorderState(
        onUpload = { file ->
            scope.launch {
                try {
                    val token = UserApi.getToken() ?: ""
                    val record = ContentApi.upload(file, token)
                    val audioUrl = ContentApi.imageUrlFor(record.uploadedFilePath)
                    
                    val finalMessage = "![audio]($audioUrl)"

                    WebSocketApi.sendChatMessage(worldId, finalMessage, shouted = shoutNext) { success ->
                        if (success) {
                            shoutNext = false
                            // updateTypingStatus(false) is handled by LaunchedEffect on recorderState.isRecording below if needed
                            // but we can also do it here
                        }
                    }
                } catch (t: Throwable) {
                    console.error("Failed to upload/send audio message", t)
                }
            }
        },
        onError = { console.error(it) }
    )

    val isRecording = recorderState.isRecording

    val updateTypingStatus = { newIsTyping: Boolean ->
        if (isTyping != newIsTyping) {
            isTyping = newIsTyping
            if (isAuthenticated && isWebSocketConnected) {
                scope.launch {
                    WebSocketApi.sendTypingStatus(worldId, newIsTyping)
                }
            }
        }
    }

    // Typing timeout coroutine
    LaunchedEffect(isTyping) {
        if (isTyping) {
            delay(5000)
            updateTypingStatus(false)
        }
    }

    // Clean up typing status on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (isTyping) {
                scope.launch {
                    WebSocketApi.sendTypingStatus(worldId, false)
                }
            }
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

    val displayEmotes = remember(userEmojiHistory, defaultEmotes) {
        val historyPart = userEmojiHistory.take(20)
        val defaultPart = defaultEmotes.filter { it !in historyPart.toSet() }
        (historyPart + defaultPart).take(20)
    }

    LaunchedEffect(chatInputRef) {
        try {
            delay(100)
            chatInputRef.value?.focus()
        } catch (e: Throwable) {
            console.error("Error focusing chat input", e)
        }
    }

    // Chat input area
    Div(
        attrs = {
            style {
                padding(16.px)
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Stretch)
                property("gap", "8px")
                property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
                width(100.percent)
                boxSizing("border-box")
            }
        }
    ) {
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
            if (isRecording) {
                AudioRecorderUI(
                    state = recorderState,
                    isFullWidth = true
                )
            } else {
                if (messageText.isBlank()) {
                    if (!isDialog) {
                        Div(
                            attrs = {
                                classes(Styles.emoteTriggerWrapper)
                            }
                        ) {
                            DialogButton(
                                onClick = { if (isAuthenticated) showEmotePopup = !showEmotePopup },
                                style = if (showEmotePopup) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                                isDisabled = !isAuthenticated,
                                small = true,
                            ) {
                                Text("😀")
                            }

                            if (showEmotePopup && isAuthenticated) {
                                Div(
                                    attrs = {
                                        classes(Styles.emotePopupContainer)
                                        onClick { e -> e.stopPropagation() }
                                    }
                                ) {
                                    displayEmotes.forEach { emote ->
                                        Div(
                                            attrs = {
                                                classes(Styles.emoteButton)
                                                onClick { e ->
                                                    e.stopPropagation()
                                                    WebSocketApi.sendChatMessage(worldId, emote, shouted = shoutNext) { success ->
                                                        if (success) {
                                                            shoutNext = false
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
                    }

                    if (isPlayer) {
                        DialogButton(
                            onClick = { if (isAuthenticated) showUploadDialog = true },
                            style = DialogButtonStyle.Secondary,
                            isDisabled = !isAuthenticated,
                            small = true,
                            attrs = {
                                style {
                                    flexShrink(0)
                                }
                            },
                        ) {
                            Text("🖼️")
                        }
                    }

                    DialogButton(
                        onClick = { if (isAuthenticated) recorderState.start() },
                        style = DialogButtonStyle.Secondary,
                        isDisabled = !isAuthenticated,
                        small = true,
                        attrs = {
                            title(str("Record"))
                            style {
                                flexShrink(0)
                            }
                        }
                    ) {
                        Text("🎤")
                    }
                } else {
                    DialogButton(
                        onClick = { if (isAuthenticated) shoutNext = !shoutNext },
                        style = if (shoutNext) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                        isDisabled = !isAuthenticated,
                        small = true,
                        attrs = {
                            title(str("Shout"))
                            style {
                                flexShrink(0)
                            }
                        }
                    ) {
                        Text("📣")
                    }
                }

                DialogInput(
                    value = messageText,
                    onInput = { newValue ->
                        messageText = newValue
                        if (isAuthenticated && newValue.isNotEmpty()) {
                            updateTypingStatus(true)
                        } else if (newValue.isEmpty() && isTyping) {
                            updateTypingStatus(false)
                        }
                    },
                    placeholder = if (isAuthenticated) str("Message") else str("Sign in to chat"),
                    enabled = isAuthenticated,
                    containerAttrs = {
                        style {
                            width(0.px)
                            flexGrow(1)
                            flexShrink(0)
                            marginBottom(0.px)
                        }
                    },
                    wrapperAttrs = {
                        style {
                            opacity(if (isAuthenticated) 1.0 else 0.7)
                        }
                    },
                    attrs = {
                        attr("autofocus", "true")
                        onKeyDown { event ->
                            if (event.key == "Enter") {
                                event.preventDefault()
                                if (isAuthenticated && messageText.isNotBlank()) {
                                    val currentMessage = messageText
                                    WebSocketApi.sendChatMessage(worldId, currentMessage, shouted = shoutNext) { success ->
                                        if (success) {
                                            messageText = ""
                                            shoutNext = false
                                            updateTypingStatus(false)
                                            scope.launch {
                                                try { ChatApi.markMessagesAsRead(worldId) } catch (_: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            } else if (event.key == "Escape") {
                                event.preventDefault()
                                onClose()
                            }
                        }
                        onPaste { event ->
                            val file = findMediaInClipboardEvent(event)
                            if (file != null) {
                                event.preventDefault()
                                if (isAuthenticated && isPlayer) {
                                    scope.launch {
                                        try {
                                            val token = UserApi.getToken() ?: ""
                                            val record = ContentApi.upload(file, token)
                                            val finalUrl = ContentApi.imageUrlFor(record.uploadedFilePath)
                                            ChatApi.createMessage(worldId = worldId, message = "", attachments = listOf(ImageAttachment(url = finalUrl)))
                                        } catch (_: Throwable) {}
                                    }
                                }
                            }
                        }
                        ref { element ->
                            chatInputRef.value = element as? org.w3c.dom.HTMLInputElement
                            onDispose { chatInputRef.value = null }
                        }
                    }
                )

                if (messageText.isBlank()) {
                    if (!isDialog) {
                        DialogButton(
                            onClick = { if (isAuthenticated) showRichEditorDialog = true },
                            style = DialogButtonStyle.Primary,
                            isDisabled = !isAuthenticated,
                            small = true,
                            attrs = {
                                style {
                                    flexShrink(0)
                                }
                            },
                        ) {
                            Text("📝")
                        }
                    }
                } else {
                    DialogButton(
                        onClick = {
                            if (isAuthenticated && messageText.isNotEmpty()) {
                                val currentMessage = messageText
                                WebSocketApi.sendChatMessage(worldId, currentMessage, shouted = shoutNext) { success ->
                                    if (success) {
                                        messageText = ""
                                        shoutNext = false
                                        updateTypingStatus(false)
                                        scope.launch {
                                            try { ChatApi.markMessagesAsRead(worldId) } catch (_: Throwable) {}
                                        }
                                    }
                                }
                            }
                        },
                        style = DialogButtonStyle.Primary,
                        isDisabled = !isAuthenticated || messageText.isEmpty(),
                        attrs = {
                            style {
                                flexShrink(0)
                            }
                        },
                    ) {
                        Translated("Send")
                    }
                }
            }

            if (showUploadDialog) {
                UploadMediaDialog(
                    onClose = { showUploadDialog = false },
                    onSelect = { imageUrl, _, _ ->
                        scope.launch {
                            try {
                                ChatApi.createMessage(worldId = worldId, message = "", attachments = listOf(ImageAttachment(url = imageUrl)))
                            } catch (_: Throwable) {} finally { showUploadDialog = false }
                        }
                    },
                    filter = MediaType.Image,
                )
            }

            LaunchedEffect(worldId) {
                ChatComposerController.requests.collect { req ->
                    if (req != null && req.worldId == worldId) {
                        initialRichText = req.initialText
                        showRichEditorDialog = true
                        ChatComposerController.clear()
                    }
                }
            }

            if (showRichEditorDialog) {
                RichTextEditorDialog(
                    onClose = { 
                        showRichEditorDialog = false 
                        initialRichText = ""
                    },
                    onSend = { markdown ->
                        if (isAuthenticated && markdown.isNotBlank()) {
                            WebSocketApi.sendChatMessage(worldId = worldId, messageText = markdown, shouted = shoutNext) { success ->
                                if (success) {
                                    showRichEditorDialog = false
                                    initialRichText = ""
                                    messageText = ""
                                    shoutNext = false
                                    updateTypingStatus(false)
                                    scope.launch { try { ChatApi.markMessagesAsRead(worldId) } catch (_: Throwable) {} }
                                }
                            }
                        }
                    },
                    actions = {
                        DialogButton(
                            onClick = { if (isAuthenticated) shoutNext = !shoutNext },
                            style = if (shoutNext) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                            isDisabled = !isAuthenticated,
                            small = true,
                            attrs = {
                                title(str("Shout"))
                            }
                        ) {
                            Text("📣")
                        }
                    },
                    initialText = initialRichText,
                )
            }
        }
    }
}

private inline fun <T : Any> jso(block: T.() -> Unit): T {
    val res = js("{}").unsafeCast<T>()
    block(res)
    return res
}
