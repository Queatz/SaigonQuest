package main.components.chat

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.*
import main.theme.Styles
import main.theme.Theme
import models.MediaType
import models.chat.ImageAttachment
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.Blob
import lib.MediaRecorder
import lib.BlobPropertyBag
import lib.FilePropertyBag
import web.navigator.navigator
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStream
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import main.components.audio.MusicManager
import org.w3c.dom.HTMLCanvasElement
import main.components.Translated
import main.components.media.UploadMediaDialog
import main.components.media.findMediaInClipboardEvent
import main.components.chat.RecordingPreview

@Composable
fun ChatComposer(
    worldId: String,
    isAuthenticated: Boolean,
    isPlayer: Boolean,
    isWebSocketConnected: Boolean,
    lastError: String?
) {
    var initialRichText by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var showEmotePopup by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var shoutNext by remember { mutableStateOf(false) }
    var showRichEditorDialog by remember { mutableStateOf(false) }
    
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingChunks = remember { mutableListOf<Blob>() }
    var isCancelled by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val chatInputRef = remember { mutableStateOf<HTMLInputElement?>(null) }

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
            kotlinx.coroutines.delay(5000)
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

    fun startRecording() {
        MusicManager.startRecording()
        scope.launch {
            try {
                val constraints = MediaStreamConstraints(
                    audio = MediaTrackConstraints()
                )
                val stream = navigator.mediaDevices.getUserMedia(constraints)
                val recorder = MediaRecorder(stream)

                recorder.ondataavailable = { event ->
                    recordingChunks.add(event.data)
                }

                recorder.onstop = {
                    if (!isCancelled) {
                        val blobProps = jso<BlobPropertyBag> {
                            type = "audio/webm"
                        }
                        val blob = Blob(recordingChunks.toTypedArray(), blobProps.unsafeCast<org.w3c.files.BlobPropertyBag>())

                        val fileProps = jso<FilePropertyBag> {
                            type = "audio/webm"
                        }
                        val file = File(arrayOf(blob), "recorded-audio.webm", fileProps.unsafeCast<org.w3c.files.FilePropertyBag>())

                        scope.launch {
                            try {
                                val token = UserApi.getToken() ?: ""
                                val record = ContentApi.upload(file, token)
                                val audioUrl = ContentApi.imageUrlFor(record.uploadedFilePath)
                                
                                val finalMessage = "![audio]($audioUrl)"

                                WebSocketApi.sendChatMessage(worldId, finalMessage, shouted = shoutNext) { success ->
                                    if (success) {
                                        shoutNext = false
                                        updateTypingStatus(false)
                                    }
                                }
                            } catch (t: Throwable) {
                                console.error("Failed to upload/send audio message", t)
                            }
                        }
                    }

                    val tracks = stream.asDynamic().getTracks() as Array<dynamic>
                    tracks.forEach { it.stop() }
                    mediaRecorder = null
                    isRecording = false
                    MusicManager.stopRecording()
                }

                recorder.onerror = {
                    console.error("Recording error")
                    isRecording = false
                    MusicManager.stopRecording()
                }

                recordingChunks.clear()
                isCancelled = false
                recorder.start()
                mediaRecorder = recorder
                isRecording = true
            } catch (t: Throwable) {
                console.error("Failed to start recording", t)
                MusicManager.stopRecording()
                isRecording = false
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isCancelled = false
        mediaRecorder?.stop()
    }

    fun cancelRecording() {
        if (!isRecording) return
        isCancelled = true
        mediaRecorder?.stop()
    }

    // Default emotes
    val defaultEmotes = remember {
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "👍", "👎", "👏", "🙌", "🤝", "👋", "🤘", "✌️", "🤞", "🤟"
        )
    }

    LaunchedEffect(chatInputRef) {
        try {
            kotlinx.coroutines.delay(100)
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
                alignItems(AlignItems.Center)
                property("gap", "8px")
                property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
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
                Div(attrs = {
                    classes(Styles.chatButton)
                    onClick { cancelRecording() }
                    title(str("Discard"))
                }) {
                    Text("🗑️")
                }

                RecordingPreview(isRecording = isRecording)

                Button(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(Theme.colors.primary)
                            color(Theme.colors.onPrimary)
                            property("border", "0")
                            borderRadius(8.px)
                            cursor("pointer")
                        }
                        onClick { stopRecording() }
                    }
                ) {
                    Translated("Send")
                }
            } else {
                if (messageText.isBlank()) {
                    Div(
                        attrs = {
                            classes(Styles.emoteTriggerWrapper)
                        }
                    ) {
                        Div(
                            attrs = {
                                classes(Styles.chatButton)
                                if (!isAuthenticated) classes(Styles.disabledAction)
                                if (showEmotePopup) classes(Styles.chatButtonSelected)
                                onClick { if (isAuthenticated) showEmotePopup = !showEmotePopup }
                            }
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
                                defaultEmotes.forEach { emote ->
                                    Div(
                                        attrs = {
                                            classes(Styles.emoteButton)
                                            onClick { e ->
                                                e.stopPropagation()
                                                WebSocketApi.sendChatMessage(worldId, emote, shouted = shoutNext) { success ->
                                                    if (success) shoutNext = false
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

                    if (isPlayer) {
                        Div(
                            attrs = {
                                classes(Styles.chatButton)
                                onClick { if (isAuthenticated) showUploadDialog = true }
                            }
                        ) {
                            Text("🖼️")
                        }
                    }

                    Div(
                        attrs = {
                            title(str("Record"))
                            classes(Styles.chatButton)
                            onClick { if (isAuthenticated) startRecording() }
                        }
                    ) {
                        Text("🎤")
                    }
                } else {
                    Div(
                        attrs = {
                            title(str("Shout"))
                            classes(Styles.chatButton)
                            if (shoutNext) classes(Styles.chatButtonSelected)
                            onClick { if (isAuthenticated) shoutNext = !shoutNext }
                        }
                    ) {
                        Text("📣")
                    }
                }

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
                            messageText = newValue
                            if (isAuthenticated && newValue.isNotEmpty()) {
                                updateTypingStatus(true)
                            } else if (newValue.isEmpty() && isTyping) {
                                updateTypingStatus(false)
                            }
                        }
                        autoFocus()
                        if (!isAuthenticated) disabled()
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
                            chatInputRef.value = element
                            onDispose { chatInputRef.value = null }
                        }
                    }
                )

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
                            if (!isAuthenticated) disabled()
                            onClick { if (isAuthenticated) showRichEditorDialog = true }
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
                            if (!isAuthenticated) disabled()
                            onClick {
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
                            }
                        }
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
                        initialRichText = ""
                        if (isAuthenticated && markdown.isNotBlank()) {
                            WebSocketApi.sendChatMessage(worldId = worldId, messageText = markdown, shouted = shoutNext) { success ->
                                if (success) {
                                    shoutNext = false
                                    updateTypingStatus(false)
                                    scope.launch { try { ChatApi.markMessagesAsRead(worldId) } catch (_: Throwable) {} }
                                }
                                showRichEditorDialog = false
                            }
                        }
                    },
                    actions = {
                        Div(
                            attrs = {
                                title(str("Shout"))
                                classes(Styles.chatButton)
                                if (shoutNext) classes(Styles.chatButtonSelected)
                                onClick { if (isAuthenticated) shoutNext = !shoutNext }
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
