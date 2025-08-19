package main.api

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import models.WebSocketAction
import org.w3c.dom.WebSocket

/**
 * Global (user-level) WebSocket API used for cross-world notifications, such as friends status changes.
 * This socket is not tied to any world and lives alongside the world-scoped WebSocketApi.
 */
object WebSocketApiGlobal {
    private val scope = MainScope()
    private var socket: WebSocket? = null
    private var isConnected = false
    private var isConnecting = false

    // Simple listener registry compatible with ListenerToken
    private val listeners = mutableMapOf<WebSocketAction, MutableList<(WebSocketMessage) -> Unit>>()

    fun connectGlobal() {
        if (isConnected || isConnecting) return
        isConnecting = true

        val token = UserApi.getToken() ?: ""
        val url = "${ApiConstants.API_BASE_URL}/ws/global?token=$token"
        socket = WebSocket(url)

        socket?.onopen = {
            isConnected = true
            isConnecting = false
        }
        socket?.onclose = {
            isConnected = false
            isConnecting = false
        }
        socket?.onerror = {
            isConnected = false
            isConnecting = false
        }
        socket?.onmessage = { event ->
            val messageData = event.data.toString()
            try {
                val message = ApiClient.json.decodeFromString<WebSocketMessage>(messageData)
                message.action?.let { action ->
                    val actionListeners = listeners[action] ?: return@let
                    actionListeners.forEach { listener ->
                        scope.launch { listener(message) }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        isConnected = false
        isConnecting = false
    }

    fun addListener(action: WebSocketAction, listener: (WebSocketMessage) -> Unit) {
        if (!listeners.containsKey(action)) {
            listeners[action] = mutableListOf()
        }
        val list = listeners[action]!!
        if (!list.contains(listener)) list.add(listener)
    }

    fun addListenerWithToken(action: WebSocketAction, listener: (WebSocketMessage) -> Unit): ListenerToken {
        addListener(action, listener)
        return ListenerToken(action, listener)
    }

    fun removeListener(action: WebSocketAction, listener: (WebSocketMessage) -> Unit) {
        listeners[action]?.remove(listener)
    }

    fun removeListener(token: ListenerToken) {
        removeListener(token.action, token.listener)
    }
}
