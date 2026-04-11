package main.components.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import lib.AudioBufferSourceNode
import lib.AudioContext
import main.api.AiApi
import main.api.ApiClient
import main.api.ApiConstants
import main.api.UserApi
import main.components.audio.MusicManager
import main.components.audio.TtsManager
import models.AiChatClientEvent
import models.AiChatMessageDto
import models.AiChatServerEvent
import models.AiRole
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

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

    var stopVoiceRequest by mutableStateOf(false)
        private set

    var speakingIndex by mutableStateOf(-1)
        private set

    val isSpeechPaused get() = TtsManager.isPaused
    val isSpeechLoading get() = TtsManager.isLoading

    private var sessionId: String? = null
    private var socket: WebSocket? = null
    var initialMessageCount by mutableStateOf(0)
        private set

    fun open(
        system: String? = null,
        initialUserMessage: String? = null,
        initialAssistantMessage: String? = null,
    ) {
        if (isOpen) close() // reset any previous ephemeral session

        isOpen = true
        val initialHistory = buildList {
            if (!system.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.System, content = system))
            if (!initialUserMessage.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.User, content = initialUserMessage))
        }

        val displayMessages = if (!initialAssistantMessage.isNullOrBlank()) {
            initialHistory + AiChatMessageDto(role = AiRole.Assistant, content = initialAssistantMessage)
        } else {
            initialHistory
        }

        messages = displayMessages
        initialMessageCount = displayMessages.size
        isLoading = false
        input = ""
        isFullscreen = false

        scope.launch { connectAndStart(system, initialHistory) }
    }

    fun close() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        sessionId = null
        isOpen = false
        isLoading = false
        TtsManager.stop()
        messages = emptyList()
        input = ""
    }

    fun setInput(value: String) { input = value }

    fun toggleFullscreen() { isFullscreen = !isFullscreen }

    fun requestStopVoice() {
        stopVoiceRequest = true
        scope.launch {
            delay(100)
            stopVoiceRequest = false
        }
    }

    fun speak(index: Int) {
        val m = messages.getOrNull(index) ?: return
        if (speakingIndex == index) {
            TtsManager.togglePauseResume()
            return
        }

        speakingIndex = index
        TtsManager.speak(m.content) {
            if (speakingIndex == index) {
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

    private fun connectAndStart(system: String?, initialHistory: List<AiChatMessageDto>) {
        val token = UserApi.getToken() ?: ""
        val url = "${ApiConstants.API_BASE_URL.replace("http", "ws")}/ws/ai/chat?token=$token"
        val ws = WebSocket(url)
        socket = ws

        ws.onopen = {
            // Send Start with initialMessages taken from provided history
            val start = AiChatClientEvent.Start(
                system = system,
                initialMessages = initialHistory,
            )
            val payload = ApiClient.json.encodeToString(AiChatClientEvent.serializer(), start)
            ws.send(payload)

            // If last preloaded message in history was user, we expect an assistant response
            if (initialHistory.lastOrNull()?.role == AiRole.User) isLoading = true
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
                            speak(messages.size - 1)
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
