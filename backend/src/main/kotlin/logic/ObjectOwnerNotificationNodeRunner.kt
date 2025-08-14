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

/**
 * Runner for ObjectOwnerNotification nodes
 *
 * This node runner handles sending notifications to the object's owner
 * (the owner of the world the object belongs to).
 */
class ObjectOwnerNotificationNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage(
            "OBJECT_OWNER_NOTIFICATION_NODE_EXECUTE",
            "Executing ObjectOwnerNotification node with data: ${node.data}",
            context.session
        )

        // Get the JSON data string from the node data
        val jsonData = node.data["data"] ?: "{}"
        WebSocketDebug.logMessage(
            "OBJECT_OWNER_NOTIFICATION_NODE_JSON_DATA",
            "Node JSON data: $jsonData",
            context.session
        )

        // Parse the JSON data to extract the message
        val message = try {
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)
            nodeData.properties.find { it.key == PropertyKey.TEXT_CONTENT }?.value ?: ""
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse ObjectOwnerNotification node data", e, context.session)
            ""
        }

        if (message.isBlank()) {
            WebSocketDebug.logError(
                "Missing or empty message in ObjectOwnerNotification node data",
                null,
                context.session
            )
            return false
        }

        // Get the object data using context.objectId
        val objectRepository = ObjectRepository()
        val objectData = objectRepository.getById(context.objectId)
        if (objectData == null) {
            WebSocketDebug.logError("Object not found with ID: ${context.objectId}", null, context.session)
            return false
        }

        // Get the world and owner
        val worldRepository = WorldRepository()
        val world = worldRepository.getById(objectData.worldId)
        if (world == null) {
            WebSocketDebug.logError("World not found with ID: ${objectData.worldId}", null, context.session)
            return false
        }
        val ownerUserId = world.userId

        if (ownerUserId.isBlank()) {
            WebSocketDebug.logError("World owner userId is blank for world: ${world.key}", null, context.session)
            return false
        }

        WebSocketDebug.logMessage(
            "OBJECT_OWNER_NOTIFICATION_USER",
            "Creating notification for object owner: $ownerUserId",
            context.session
        )

        val notification = Notification(
            userId = ownerUserId,
            title = "New message from \"${objectData.title}\" in \"${world.title}\"",
            description = message,
            isRead = false,
            type = NotificationType.OBJECT_NOTIFICATION,
            worldId = objectData.worldId,
            objectId = objectData.key,
            timestamp = System.currentTimeMillis()
        )

        // Save the notification
        return try {
            val notificationRepository = NotificationRepository()
            val createdNotification = notificationRepository.create(notification)
            WebSocketDebug.logMessage(
                "OBJECT_OWNER_NOTIFICATION_CREATED",
                "Created notification with key: ${createdNotification.key}",
                context.session
            )
            WebSocketDebug.logMessage(
                "OBJECT_OWNER_NOTIFICATION_SUCCESS",
                "ObjectOwnerNotification node execution completed successfully",
                context.session
            )
            true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to create owner notification", e, context.session)
            false
        }
    }
}

