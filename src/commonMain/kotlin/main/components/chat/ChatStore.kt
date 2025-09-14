package main.components.chat

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WorldApi
import models.ChatMessage
import models.ChatMessagePin
import models.WebSocketAction

/**
 * Global chat state store keyed by worldId. Loads chat history and pins once per world,
 * listens for WebSocket updates, and exposes Compose-observable state for rendering.
 */
object ChatStore {
    class ChatState {
        val messages = mutableStateListOf<ChatMessage>()
        val pins = mutableStateListOf<ChatMessagePin>()
        val isOwner = mutableStateOf(false)
        val isLoading = mutableStateOf(true)
        val error = mutableStateOf<String?>(null)
    }

    private val scope: CoroutineScope = MainScope()
    private val states = mutableStateMapOf<String, ChatState>()
    private var listenersInitialized = false

    fun getState(worldId: String): ChatState {
        return states.getOrPut(worldId) { ChatState() }
    }

    fun replaceMessage(worldId: String, updated: ChatMessage) {
        val state = states[worldId] ?: return
        val idx = state.messages.indexOfFirst { it.key == updated.key }
        if (idx >= 0) {
            state.messages[idx] = updated
        } else {
            state.messages.add(updated)
            state.messages.sortBy { it.timestamp }
        }
    }

    fun setPins(worldId: String, newPins: List<ChatMessagePin>) {
        val state = states[worldId] ?: return
        state.pins.clear()
        state.pins.addAll(newPins.sortedByDescending { it.pinnedAt })
    }

    fun addPin(worldId: String, pin: ChatMessagePin) {
        val state = states[worldId] ?: return
        if (state.pins.none { it.messageId == pin.messageId }) {
            state.pins.add(pin)
            state.pins.sortByDescending { it.pinnedAt }
        }
    }

    fun removePinByMessageId(messageId: String) {
        // We don't get worldId on PIN_REMOVED; remove from all worlds
        states.values.forEach { st ->
            val idx = st.pins.indexOfFirst { it.messageId == messageId }
            if (idx >= 0) st.pins.removeAt(idx)
        }
    }

    fun preload(worldId: String) {
        val state = getState(worldId)
        // If already loaded and not loading, do nothing
        if (!state.isLoading.value && state.messages.isNotEmpty()) return

        initializeListenersIfNeeded()

        state.isLoading.value = true
        state.error.value = null

        scope.launch {
            try {
                // Determine ownership
                val me = try { UserApi.getCurrentUser() } catch (_: Throwable) { null }
                val world = try { WorldApi.getWorld(worldId) } catch (_: Throwable) { null }
                state.isOwner.value = (me != null && world != null && me.key == world.userId)

                // Load messages
                val chatMessages = ChatApi.getChatMessages(worldId).toList().sortedBy { it.timestamp }
                state.messages.clear()
                state.messages.addAll(chatMessages)

                // Load pins
                try {
                    val pins = ChatApi.listPins(worldId).toList()
                    setPins(worldId, pins)
                } catch (t: Throwable) {
                    console.error("Failed to load pins for world $worldId", t)
                }

                state.isLoading.value = false
            } catch (t: Throwable) {
                console.error("Error preloading chat for world $worldId", t)
                state.error.value = "Failed to load messages"
                state.isLoading.value = false
            }
        }
    }

    private fun initializeListenersIfNeeded() {
        if (listenersInitialized) return
        listenersInitialized = true

        // New message
        WebSocketApi.addListener(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                if (message.isValid()) {
                    val chatMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    // Ensure a state exists for this world, even if preload hasn't run yet
                    val st = states.getOrPut(chatMessage.worldId) { ChatState() }
                    // Mark as not loading if we start receiving live messages
                    if (st.isLoading.value) st.isLoading.value = false
                    st.messages.add(chatMessage)
                    st.messages.sortBy { it.timestamp }
                } else {
                    console.error("Invalid chat message received: ${message.action}")
                }
            } catch (t: Throwable) {
                console.error("Error parsing chat message", t)
            }
        }

        // Update message
        WebSocketApi.addListener(WebSocketAction.UPDATE_CHAT_MESSAGE) { message ->
            try {
                if (message.isValid()) {
                    val updated = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    replaceMessage(updated.worldId, updated)
                } else {
                    console.error("Invalid chat update received: ${message.action}")
                }
            } catch (t: Throwable) {
                console.error("Error parsing chat update", t)
            }
        }

        // Delete message (soft delete)
        WebSocketApi.addListener(WebSocketAction.DELETE_CHAT_MESSAGE) { message ->
            try {
                if (message.isValid()) {
                    val deleted = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    replaceMessage(deleted.worldId, deleted)
                } else {
                    console.error("Invalid chat delete received: ${message.action}")
                }
            } catch (t: Throwable) {
                console.error("Error parsing chat delete", t)
            }
        }

        // Pins
        WebSocketApi.addListener(WebSocketAction.PIN_ADDED) { message ->
            try {
                if (message.isValid()) {
                    val pin = ApiClient.json.decodeFromString<ChatMessagePin>(message.data)
                    addPin(pin.worldId, pin)
                }
            } catch (t: Throwable) {
                console.error("Error handling PIN_ADDED", t)
            }
        }
        WebSocketApi.addListener(WebSocketAction.PIN_REMOVED) { message ->
            try {
                if (message.isValid()) {
                    val messageId = ApiClient.json.decodeFromString<String>(message.data)
                    removePinByMessageId(messageId)
                }
            } catch (t: Throwable) {
                console.error("Error handling PIN_REMOVED", t)
            }
        }
    }
}
