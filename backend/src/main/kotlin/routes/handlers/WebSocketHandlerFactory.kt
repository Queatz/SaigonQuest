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
            WebSocketAction.USER_JOIN -> UserJoinHandler()
            WebSocketAction.USER_LEAVE -> UserLeaveHandler()
            WebSocketAction.ADD_OBJECT -> AddObjectHandler()
            WebSocketAction.REMOVE_OBJECT -> RemoveObjectHandler()
            WebSocketAction.OBJECT_UPDATE -> ObjectUpdateHandler()
            WebSocketAction.USER_INTERACT -> PlayerInteractHandler(objectRepository)
            WebSocketAction.TRIGGER_OBJECT_EVENT -> TriggerObjectEventHandler()
            WebSocketAction.UPDATE_PLAYER -> UpdatePlayerHandler()
            WebSocketAction.TELEPORT_PLAYER -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.SHOW_DIALOGUE -> NullHandler() // This is only sent by the server, not received
            WebSocketAction.RECEIVE_CHAT_MESSAGE -> NullHandler() // This is only sent by the server, not received
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
        json: kotlinx.serialization.json.Json,
        session: io.ktor.server.websocket.DefaultWebSocketServerSession
    ) {
        // Do nothing
    }
}
