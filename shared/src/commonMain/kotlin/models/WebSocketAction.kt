package models

/**
 * Enum representing all possible WebSocket actions
 *
 * This enum defines all valid actions that can be used in WebSocket messages
 * between the client and server. Using an enum instead of strings provides
 * type safety and better code completion.
 */
enum class WebSocketAction {
    ADD_OBJECT,
    REMOVE_OBJECT,
    OBJECT_UPDATE,
    UPDATE_PLAYER_POSITION,
    USER_INTERACT,
    TRIGGER_OBJECT_EVENT,
    SEND_CHAT_MESSAGE,
    RECEIVE_CHAT_MESSAGE,
    UPDATE_CHAT_MESSAGE,
    DELETE_CHAT_MESSAGE,
    USER_JOIN,
    USER_LEAVE,
    UPDATE_PLAYER,
    TELEPORT_PLAYER,
    SHOW_DIALOGUE,
    DIALOGUE_CONFIRM,
    PLAYER_TYPING,
    OPEN_URL,
    NOTIFICATION,
    PAUSE
}
