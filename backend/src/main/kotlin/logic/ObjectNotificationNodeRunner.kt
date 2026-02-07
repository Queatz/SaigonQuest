

package logic

import debug.WebSocketDebug
import models.Notification
import models.NotificationType
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.NotificationRepository
import repository.ObjectRepository
import repository.WorldRepository
import utils.json
import kotlin.time.Clock


/**
 * Runner for ObjectNotification nodes
 *
 * This node runner handles sending notifications to the current user.
 * It creates a notification with the object and world information.
 */
class ObjectNotificationNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("OBJECT_NOTIFICATION_NODE_EXECUTE", "Executing ObjectNotification node with data: ${node.data}", context.session)
        
        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        WebSocketDebug.logMessage("OBJECT_NOTIFICATION_NODE_JSON_DATA", "Node JSON data: $jsonData", context.session)
        
        // Parse the JSON data to extract the message
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse ObjectNotification node data", e, context.session)
            PropertyBasedNodeData()
        }

        fun inKey(name: String) = "${node.id}_in_${name}"
        fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
            val eventVal = eventData[inKey(nameLower)] as? String
            if (!eventVal.isNullOrBlank()) return eventVal
            return nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }
        }

        val message = fromEventOrNode("text_content", PropertyKey.TEXT_CONTENT) ?: ""
        
        if (message.isBlank()) {
            WebSocketDebug.logError("Missing or empty message in ObjectNotification node data", null, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("OBJECT_NOTIFICATION_MESSAGE", "Message to send: $message", context.session)
        
        // Get the object data using context.objectId
        val objectRepository = ObjectRepository()
        val objectData = objectRepository.getById(context.objectId)
        if (objectData == null) {
            WebSocketDebug.logError("Object not found with ID: ${context.objectId}", null, context.session)
            return false
        }
        
        // Get the world title
        val worldRepository = WorldRepository()
        val worldTitle = worldRepository.getById(objectData.worldId)?.title ?: "Unknown World"
        
        // Create a notification
        val userId = context.connection.userId
        WebSocketDebug.logMessage("OBJECT_NOTIFICATION_USER", "Creating notification for user: $userId", context.session)
        
        val notification = Notification(
            userId = userId,
            title = "New message from \"${objectData.title}\" in \"$worldTitle\"",
            description = message,
            isRead = false,
            type = NotificationType.OBJECT_NOTIFICATION,
            worldId = objectData.worldId,
            objectId = objectData.key,
            timestamp = Clock.System.now()
        )
        
        // Save the notification
        try {
            val notificationRepository = NotificationRepository()
            val createdNotification = notificationRepository.create(notification)
            WebSocketDebug.logMessage("OBJECT_NOTIFICATION_CREATED", "Created notification with key: ${createdNotification.key}", context.session)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to create notification", e, context.session)
            return false
        }
        
        WebSocketDebug.logMessage("OBJECT_NOTIFICATION_SUCCESS", "ObjectNotification node execution completed successfully", context.session)
        return true
    }
}
