package main.api

import models.FriendStatusChanged
import models.WebSocketAction

/**
 * Listener extension for the global WebSocket API.
 */
fun WebSocketApiGlobal.addFriendStatusChangedListener(
    listener: (FriendStatusChanged) -> Unit,
): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<FriendStatusChanged>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApiGlobal: Error parsing FRIEND_STATUS_CHANGED", e)
        }
    }
    return addListenerWithToken(WebSocketAction.FRIEND_STATUS_CHANGED, handler)
}
