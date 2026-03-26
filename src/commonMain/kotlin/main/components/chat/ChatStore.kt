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
import kotlin.time.Clock

/**
 * Global chat state store keyed by worldId. Loads chat history and pins once per world,
 * listens for WebSocket updates, and exposes Compose-observable state for rendering.
 */
object ChatStore {
    private const val MIN_OLDER_LOAD_INTERVAL_MS = 1_000L
    enum class ChatMutationType { ReplaceAll, AppendNew, PrependOld, Update }
    class ChatState {
        val messages = mutableStateListOf<ChatMessage>()
        val pins = mutableStateListOf<ChatMessagePin>()
        val isOwner = mutableStateOf(false)
        val isLoading = mutableStateOf(true)
        val isLoadingMore = mutableStateOf(false)
        val hasMore = mutableStateOf(true)
        val lastOlderLoadAtMs = mutableStateOf(0L)
        val lastMutation = mutableStateOf(ChatMutationType.ReplaceAll)
        val lastMutatedMessageKey = mutableStateOf<String?>(null)
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
        state.lastMutatedMessageKey.value = updated.key
        state.lastMutation.value = ChatMutationType.Update
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
        state.hasMore.value = true
        state.lastOlderLoadAtMs.value = 0L

        scope.launch {
            try {
                // Determine ownership
                val me = try { UserApi.getCurrentUser() } catch (_: Throwable) { null }
                val world = try { WorldApi.getWorld(worldId) } catch (_: Throwable) { null }
                state.isOwner.value = (me != null && world != null && me.key == world.userId)

                // Load latest page of messages (server defaults: limit=100, olderThan=now)
                val chatMessages = ChatApi.getChatMessages(worldId).toList()
                val existingKeys = state.messages.map { it.key }.toSet()
                val newMessages = chatMessages.filter { it.key !in existingKeys }
                if (newMessages.isNotEmpty()) {
                    state.messages.addAll(newMessages)
                    state.messages.sortBy { it.timestamp }
                }
                state.lastMutation.value = ChatMutationType.ReplaceAll
                // If fewer than typical page size, assume no more
                if (chatMessages.isEmpty()) state.hasMore.value = false

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

    /**
     * Load a page of older messages and merge into the beginning of the list.
     * Returns the number of new messages added.
     */
    suspend fun loadOlder(worldId: String, limit: Int = 100): Int {
        val state = getState(worldId)
        if (state.isLoadingMore.value || !state.hasMore.value) return 0
        // Throttle: ensure at least 1s between successive older-loads per world
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (nowMs - state.lastOlderLoadAtMs.value < MIN_OLDER_LOAD_INTERVAL_MS) return 0
        state.lastOlderLoadAtMs.value = nowMs
        state.isLoadingMore.value = true
        state.error.value = null
        return try {
            val oldestMs = state.messages.firstOrNull()?.timestamp?.toEpochMilliseconds()
                ?: Clock.System.now().toEpochMilliseconds()
            val older = ChatApi.getChatMessagesBefore(worldId, oldestMs, limit).toList()
            if (older.isEmpty()) {
                state.hasMore.value = false
                0
            } else {
                val existingKeys = state.messages.map { it.key }.toHashSet()
                val newOnes = older.filter { it.key !in existingKeys }
                if (newOnes.isEmpty()) {
                    // If server returned only duplicates, assume no more
                    state.hasMore.value = older.size >= limit
                    0
                } else {
                    state.lastMutation.value = ChatMutationType.PrependOld
                    state.messages.addAll(newOnes)
                    state.messages.sortBy { it.timestamp }
                    if (older.size < limit) state.hasMore.value = false
                    newOnes.size
                }
            }
        } catch (t: Throwable) {
            console.error("Failed to load older messages for $worldId", t)
            state.error.value = "Failed to load messages"
            0
        } finally {
            state.isLoadingMore.value = false
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
                    st.lastMutatedMessageKey.value = chatMessage.key
                    st.lastMutation.value = ChatMutationType.AppendNew
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
