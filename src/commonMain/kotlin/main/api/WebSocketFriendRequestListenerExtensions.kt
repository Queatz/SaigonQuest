package main.api

import models.FriendRequestData
import models.WebSocketAction

/**
 * Listener extensions for friend request lifecycle events on the global WebSocket.
 * These helpers parse FriendRequestData and return a ListenerToken for removal.
 */
/**
 * Currently unused by design (no dedicated Requests UI). The flow relies on
 * notifications and OtherPlayerDialog for request handling. Kept for potential
 * future Requests UI.
 */
fun WebSocketApiGlobal.addFriendRequestCreatedListener(
    listener: (FriendRequestData) -> Unit,
): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<FriendRequestData>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApiGlobal: Error parsing FRIEND_REQUEST_CREATED", e)
        }
    }
    return addListenerWithToken(WebSocketAction.FRIEND_REQUEST_CREATED, handler)
}

fun WebSocketApiGlobal.addFriendRequestAcceptedListener(
    listener: (FriendRequestData) -> Unit,
): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<FriendRequestData>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApiGlobal: Error parsing FRIEND_REQUEST_ACCEPTED", e)
        }
    }
    return addListenerWithToken(WebSocketAction.FRIEND_REQUEST_ACCEPTED, handler)
}

/**
 * Currently unused by design (no dedicated Requests UI). The flow relies on
 * notifications and OtherPlayerDialog for request handling. Kept for potential
 * future Requests UI.
 */
fun WebSocketApiGlobal.addFriendRequestDeclinedListener(
    listener: (FriendRequestData) -> Unit,
): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<FriendRequestData>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApiGlobal: Error parsing FRIEND_REQUEST_DECLINED", e)
        }
    }
    return addListenerWithToken(WebSocketAction.FRIEND_REQUEST_DECLINED, handler)
}

/**
 * Currently unused by design (no dedicated Requests UI). The flow relies on
 * notifications and OtherPlayerDialog for request handling. Kept for potential
 * future Requests UI.
 */
fun WebSocketApiGlobal.addFriendRequestCancelledListener(
    listener: (FriendRequestData) -> Unit,
): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<FriendRequestData>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApiGlobal: Error parsing FRIEND_REQUEST_CANCELLED", e)
        }
    }
    return addListenerWithToken(WebSocketAction.FRIEND_REQUEST_CANCELLED, handler)
}
