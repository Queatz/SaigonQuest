package main.components.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ApiConstants
import main.api.UserApi
import models.AiChatClientEvent
import models.AiChatMessageDto
import models.AiChatServerEvent
import models.AiRole
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

    private var sessionId: String? = null
    private var socket: WebSocket? = null

    fun open(
        system: String? = null,
        initialUserMessage: String? = null,
    ) {
        if (isOpen) close() // reset any previous ephemeral session

        isOpen = true
        messages = buildList {
            if (!system.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.System, content = system))
            if (!initialUserMessage.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.User, content = initialUserMessage))
        }
        isLoading = false
        input = ""

        scope.launch { connectAndStart(system) }
    }

    fun close() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        sessionId = null
        isOpen = false
        isLoading = false
        messages = emptyList()
        input = ""
    }

    fun setInput(value: String) { input = value }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val ws = socket ?: return

        // Optimistic append user message
        messages = messages + AiChatMessageDto(role = AiRole.User, content = trimmed)
        input = ""
        isLoading = true

        val payload = ApiClient.json.encodeToString(
            AiChatClientEvent.UserMessage.serializer(),
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
            val payload = ApiClient.json.encodeToString(AiChatClientEvent.Start.serializer(), start)
            ws.send(payload)

            // If last preloaded message was user, we expect an assistant response
            if (messages.lastOrNull()?.role == AiRole.User) isLoading = true
        }

        ws.onmessage = { ev: MessageEvent ->
            val dataStr = ev.data?.toString()
            if (dataStr != null) {
                // Try to decode SessionOpened
                val opened = runCatching {
                    ApiClient.json.decodeFromString(AiChatServerEvent.SessionOpened.serializer(), dataStr)
                }.getOrNull()
                if (opened != null) {
                    sessionId = opened.sessionId
                } else {
                    // Try to decode AssistantMessage
                    val assistant = runCatching {
                        ApiClient.json.decodeFromString(AiChatServerEvent.AssistantMessage.serializer(), dataStr)
                    }.getOrNull()
                    if (assistant != null) {
                        messages = messages + AiChatMessageDto(role = AiRole.Assistant, content = assistant.text)
                        isLoading = false
                    } else {
                        // Try to decode Error
                        val err = runCatching {
                            ApiClient.json.decodeFromString(AiChatServerEvent.Error.serializer(), dataStr)
                        }.getOrNull()
                        if (err != null) {
                            console.error("AI chat error: ", err.message)
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
