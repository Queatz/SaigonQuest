package main.components.player

import app.softwork.routingcompose.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ListenerToken
import main.api.UserApi
import main.api.WebSocketApi
import main.api.addTeleportPlayerListener
import main.api.addPauseListener
import main.api.addUpdatePlayerListener
import models.ChatMessage
import models.UpdatePlayerData
import models.WebSocketAction

/**
 * Sets up WebSocket listeners for the local player and forwards events via callbacks.
 */
class PlayerNetworkSync(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private var chatListenerToken: ListenerToken? = null
    private var updatePlayerListenerToken: ListenerToken? = null

    fun initChatListener(onSelfChat: (String) -> Unit) {
        chatListenerToken = WebSocketApi.addListenerWithToken(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                if (message.isValid()) {
                    val chatMessage = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    scope.launch {
                        try {
                            val user = UserApi.getCurrentUser()
                            if (chatMessage.userId == user.key) {
                                onSelfChat(chatMessage.message)
                            }
                        } catch (t: Throwable) {
                            console.error("PlayerNetworkSync: getCurrentUser failed", t)
                        }
                    }
                }
            } catch (e: Throwable) {
                console.error("PlayerNetworkSync: chat parse error", e)
            }
        }
    }

    fun initStatusListener(
        onShowStatus: (String) -> Unit,
        onClearStatus: () -> Unit,
    ) {
        updatePlayerListenerToken = WebSocketApi.addUpdatePlayerListener { updateData: UpdatePlayerData ->
            scope.launch {
                try {
                    val currentUser = UserApi.getCurrentUser()
                    if (updateData.userId == currentUser.key) {
                        val incomingStatus: String? = updateData.status
                        if (!incomingStatus.isNullOrBlank()) onShowStatus(incomingStatus) else if (incomingStatus != null) onClearStatus()
                    }
                } catch (e: Throwable) {
                    console.error("PlayerNetworkSync: status update error", e)
                }
            }
        }
        // Initial status
        scope.launch {
            try {
                val user = UserApi.getCurrentUser()
                if (user.status.isNotBlank()) onShowStatus(user.status)
            } catch (e: Throwable) {
                console.error("PlayerNetworkSync: initial status fetch error", e)
            }
        }
    }

    fun initTeleportListener(router: Router) {
        WebSocketApi.addTeleportPlayerListener(router) { teleportData ->
            console.log("PlayerNetworkSync: teleport event: $teleportData")
        }
    }

    fun initPauseListener(router: Router) {
        WebSocketApi.addPauseListener(router) { pauseData ->
            console.log("PlayerNetworkSync: pause event for user ${'$'}{pauseData.userId}")
        }
    }

    fun dispose() {
        chatListenerToken?.let { WebSocketApi.removeListener(it) }
        chatListenerToken = null
        updatePlayerListenerToken?.let { WebSocketApi.removeListener(it) }
        updatePlayerListenerToken = null
    }
}
