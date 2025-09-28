package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import models.ChatMessage
import models.ChatSenderType
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.ChatRepository
import repository.ObjectRepository
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import utils.json

/**
 * Runner for SendChat nodes
 *
 * Sends a chat message to the world's chat as the current object and opens the chat panel for the user who triggered it.
 */
class SendChatNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("SEND_CHAT_NODE_EXECUTE", "Executing SendChat node with data: ${node.data}", context.session)

        // Parse node data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse SendChat node data", e, context.session)
            return false
        }

        val messageText = nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value?.trim().orEmpty()
        if (messageText.isBlank()) {
            WebSocketDebug.logError("SendChat: TEXT_CONTENT is missing or blank", null, context.session)
            return false
        }

        // Resolve object info
        val obj = try { ObjectRepository().getById(context.objectId) } catch (_: Throwable) { null }
        val senderName = obj?.title?.takeIf { it.isNotBlank() } ?: "Object"

        // Compose chat message
        val chatMessage = ChatMessage(
            worldId = context.worldId,
            senderType = ChatSenderType.OBJECT,
            senderId = context.objectId,
            senderName = senderName,
            message = messageText,
        )

        try {
            // Save
            val repo = ChatRepository()
            val saved = repo.saveMessage(chatMessage)

            // Broadcast to world
            val broadcast = WebSocketMessage(
                action = WebSocketAction.RECEIVE_CHAT_MESSAGE,
                data = json.encodeToString(ChatMessage.serializer(), saved)
            )
            val broadcastStr = json.encodeToString(WebSocketMessage.serializer(), broadcast)
            WebSocketUtils.broadcastToWorld(context.worldId, broadcastStr, context.activeConnections, context.session)

            // Open chat for current user only
            val openChatMsg = WebSocketMessage(
                action = WebSocketAction.OPEN_CHAT,
                data = "{}"
            )
            context.connection.session.send(json.encodeToString(WebSocketMessage.serializer(), openChatMsg))

            WebSocketDebug.logMessage("SEND_CHAT_NODE_SUCCESS", "Sent and broadcast chat message as object ${context.objectId}", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("SendChat: failed to save/broadcast message", e, context.session)
            return false
        }
    }
}
