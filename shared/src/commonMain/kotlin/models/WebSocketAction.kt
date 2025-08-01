package models

import kotlinx.serialization.Serializable

/**
 * Enum representing all possible WebSocket actions
 *
 * This enum defines all valid actions that can be used in WebSocket messages
 * between the client and server. Using an enum instead of strings provides
 * type safety and better code completion.
 */
@Serializable
enum class WebSocketAction {
    ADD_OBJECT,
    REMOVE_OBJECT,
    UPDATE_PLAYER_POSITION,
    PLAYER_INTERACT,
    TRIGGER_OBJECT_EVENT,
    SEND_CHAT_MESSAGE,
    RECEIVE_CHAT_MESSAGE,
    USER_JOIN,
    USER_LEAVE
}
