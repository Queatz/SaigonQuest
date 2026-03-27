package main.components.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.browser.document
import main.api.ApiClient
import main.api.ApiConstants
import main.api.UserApi
import models.AiChatClientEvent
import models.AiChatMessageDto
import models.AiChatServerEvent
import models.AiRole
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import main.api.AiApi
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

object GlobalAiChatStore {
    private val scope = MainScope()

    var isOpen by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var messages by mutableStateOf(listOf<AiChatMessageDto>())
        private set

    var input by mutableStateOf("")
        private set

    var isFullscreen by mutableStateOf(false)
        private set

    var speakingIndex by mutableStateOf(-1)
        private set

    private var sessionId: String? = null
    private var socket: WebSocket? = null
    var initialMessageCount by mutableStateOf(0)
        private set

    fun open(
        system: String? = null,
        initialUserMessage: String? = null,
    ) {
        if (isOpen) close() // reset any previous ephemeral session

        isOpen = true
        val initial = buildList {
            if (!system.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.System, content = system))
            if (!initialUserMessage.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.User, content = initialUserMessage))
        }
        messages = initial
        initialMessageCount = initial.size
        isLoading = false
        input = ""
        isFullscreen = false

        scope.launch { connectAndStart(system) }
    }

    fun close() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        sessionId = null
        isOpen = false
        isLoading = false
        speakingIndex = -1
        messages = emptyList()
        input = ""
    }

    fun setInput(value: String) { input = value }

    fun toggleFullscreen() { isFullscreen = !isFullscreen }

    fun speak(index: Int) {
        val m = messages.getOrNull(index) ?: return
        if (speakingIndex != -1) return // Already speaking someone else

        scope.launch {
            speakingIndex = index
            try {
                val bytes = AiApi.generateTts(m.content)
                val uint8Array = Uint8Array(bytes.size)
                for (i in bytes.indices) {
                    uint8Array[i] = bytes[i]
                }
                val blob = Blob(arrayOf(uint8Array), BlobPropertyBag(type = "audio/mpeg"))
                val url = URL.createObjectURL(blob)
                val audio = document.createElement("audio") as HTMLAudioElement
                audio.src = url
                audio.onended = { speakingIndex = -1 }
                audio.play()
            } catch (t: Throwable) {
                console.error("TTS failed:", t)
                speakingIndex = -1
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val ws = socket ?: return

        // Optimistic append user message
        messages = messages + AiChatMessageDto(role = AiRole.User, content = trimmed)
        input = ""
        isLoading = true

        val payload = ApiClient.json.encodeToString(
            AiChatClientEvent.serializer(),
            AiChatClientEvent.UserMessage(text = trimmed),
        )
        ws.send(payload)
    }

    private fun connectAndStart(system: String?) {
        val token = UserApi.getToken() ?: ""
        val url = "${ApiConstants.API_BASE_URL.replace("http", "ws")}/ws/ai/chat?token=$token"
        val ws = WebSocket(url)
        socket = ws

        ws.onopen = {
            // Send Start with initialMessages taken from current messages
            val start = AiChatClientEvent.Start(
                system = system,
                initialMessages = messages,
            )
            val payload = ApiClient.json.encodeToString(AiChatClientEvent.serializer(), start)
            ws.send(payload)

            // If last preloaded message was user, we expect an assistant response
            if (messages.lastOrNull()?.role == AiRole.User) isLoading = true
        }

        ws.onmessage = { ev: MessageEvent ->
            val dataStr = ev.data?.toString()
            if (dataStr != null) {
                val event = runCatching {
                    ApiClient.json.decodeFromString(AiChatServerEvent.serializer(), dataStr)
                }.getOrNull()

                if (event != null) {
                    when (event) {
                        is AiChatServerEvent.SessionOpened -> {
                            sessionId = event.sessionId
                        }
                        is AiChatServerEvent.AssistantMessage -> {
                            messages = messages + AiChatMessageDto(role = AiRole.Assistant, content = event.text)
                            isLoading = false
                        }
                        is AiChatServerEvent.Error -> {
                            console.error("AI chat error: ", event.message)
                            isLoading = false
                        }
                    }
                }
            }
        }

        ws.onclose = {
            // Don’t auto-reconnect: ephemeral session ends on close.
            socket = null
        }

        ws.onerror = {
            console.error("AI chat websocket error")
            isLoading = false
        }
    }
}
