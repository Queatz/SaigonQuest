package routes.handlers

import models.WebSocketAction
import repository.ChatRepository
import repository.ObjectRepository
import repository.UserRepository

/**
 * Factory for creating WebSocket handlers
 *
 * This factory creates the appropriate handler for each WebSocketAction.
 * It ensures that handlers are created with the correct dependencies.
 */
class WebSocketHandlerFactory(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val objectRepository: ObjectRepository
) {
    /**
     * Get the appropriate handler for a WebSocketAction
     *
     * @param action The WebSocketAction to get a handler for
     * @return The appropriate WebSocketHandler for the action
     */
    fun getHandler(action: WebSocketAction?): WebSocketHandler {
        return when (action) {
            WebSocketAction.SEND_CHAT_MESSAGE -> SendChatMessageHandler(chatRepository)
            WebSocketAction.UPDATE_CHAT_MESSAGE -> UpdateChatMessageHandler(chatRepository)
            WebSocketAction.DELETE_CHAT_MESSAGE -> DeleteChatMessageHandler(chatRepository)
            WebSocketAction.UPDATE_PLAYER_POSITION -> UpdatePlayerPositionHandler()
            WebSocketAction.USER_JOIN -> UserJoinHandler(objectRepository)
            WebSocketAction.USER_LEAVE -> UserLeaveHandler(chatRepository, userRepository)
            WebSocketAction.ADD_OBJECT -> NullHandler()
            WebSocketAction.REMOVE_OBJECT -> NullHandler()
            WebSocketAction.OBJECT_UPDATE -> NullHandler()
            WebSocketAction.USER_INTERACT -> PlayerInteractHandler(objectRepository)
            WebSocketAction.TRIGGER_OBJECT_EVENT -> TriggerObjectEventHandler()
            WebSocketAction.UPDATE_PLAYER -> UpdatePlayerHandler()
            WebSocketAction.DIALOGUE_CONFIRM -> DialogueConfirmHandler()
            WebSocketAction.PLAYER_TYPING -> PlayerTypingHandler()
            WebSocketAction.OBJECT_TYPING -> NullHandler() // Server -> clients only
            WebSocketAction.TELEPORT_PLAYER -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.SHOW_DIALOGUE -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.RECEIVE_CHAT_MESSAGE -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.OPEN_URL -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.NOTIFICATION -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.PAUSE -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.PIN_ADDED -> NullHandler() // Server -> clients only
            WebSocketAction.PIN_REMOVED -> NullHandler() // Server -> clients only
            WebSocketAction.SHOW_TAKE_ITEMS -> NullHandler() // Server -> clients only
            WebSocketAction.INVENTORY_CHANGED -> NullHandler() // Server -> clients only
            WebSocketAction.OPEN_CHAT -> NullHandler() // Server -> clients only
            WebSocketAction.TAKE_ITEMS_CONFIRM -> TakeItemsConfirmHandler()
            WebSocketAction.FRIEND_STATUS_CHANGED -> NullHandler() // Server -> clients only
            WebSocketAction.FRIEND_REQUEST_CREATED -> NullHandler() // Server -> clients only
            WebSocketAction.FRIEND_REQUEST_ACCEPTED -> NullHandler() // Server -> clients only
            WebSocketAction.FRIEND_REQUEST_DECLINED -> NullHandler() // Server -> clients only
            WebSocketAction.FRIEND_REQUEST_CANCELLED -> NullHandler() // Server -> clients only
            WebSocketAction.HEARTBEAT -> NullHandler() // Ping/pong no-op
            null -> NullHandler()
        }
    }
}

/**
 * Null handler for actions that don't need to be handled
 *
 * This handler does nothing and is used for actions that don't need to be handled,
 * such as actions that are only sent by the server and not received from clients.
 */
class NullHandler : WebSocketHandler {
    override suspend fun handle(
        message: routes.WebSocketMessage,
        connection: routes.WebSocketConnection,
        worldId: String,
        activeConnections: java.util.concurrent.ConcurrentHashMap<String, MutableList<routes.WebSocketConnection>>,
        session: io.ktor.server.websocket.DefaultWebSocketServerSession
    ) {
        // Do nothing
    }
}
