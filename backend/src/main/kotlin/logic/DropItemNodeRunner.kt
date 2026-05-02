package logic

import debug.WebSocketDebug
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import models.*
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.InventoryRepository
import repository.ItemRepository
import repository.NotificationRepository
import repository.ObjectRepository
import repository.WorldRepository
import routes.WebSocketMessage
import utils.json
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Runner for the DropItem node
 *
 * Takes items from the world owner's inventory and drops them in the world
 * at a random position within a given radius.
 */
class DropItemNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    private val worldRepository = WorldRepository()
    private val objectRepository = ObjectRepository()
    private val inventoryRepository = InventoryRepository()
    private val itemRepository = ItemRepository()
    private val notificationRepository = NotificationRepository()

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("DROP_ITEM_START", "Executing DropItem node: ${node.id}", context.session)

        // Parse property data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            PropertyBasedNodeData()
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String) ?: nodeData.properties.find { it.key == key }?.value

        val itemsCsv = getInput(PropertyKey.ITEMS)?.trim().orEmpty()
        val quantitiesCsv = getInput(PropertyKey.QUANTITIES)?.trim().orEmpty()
        val radiusStr = getInput(PropertyKey.RADIUS) ?: "1"
        val radius = radiusStr.toDoubleOrNull() ?: 1.0

        if (itemsCsv.isEmpty()) {
            WebSocketDebug.logMessage("DROP_ITEM_ERROR", "No items specified", context.session)
            return false
        }

        val items = itemsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val quantities = quantitiesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 1 }

        WebSocketDebug.logMessage("DROP_ITEM_DEBUG", "Items: $items, Quantities: $quantities", context.session)

        // Find the world owner
        val world = worldRepository.getById(context.worldId)
        if (world == null) {
            WebSocketDebug.logMessage("DROP_ITEM_ERROR", "World not found: ${context.worldId}", context.session)
            return false
        }
        val ownerId = world.userId

        // Get base position (current object's position or (0,0,0))
        var baseX = 0.0
        var baseY = 0.0
        var baseZ = 0.0

        if (context.objectId.isNotEmpty()) {
            val obj = objectRepository.getById(context.objectId)
            if (obj != null) {
                baseX = obj.position.x.toDouble()
                baseY = obj.position.y.toDouble()
                baseZ = obj.position.z.toDouble()
            }
        }

        // Broadcast target
        val connections = context.activeConnections[context.worldId] ?: mutableListOf()

        val droppedSummary = mutableListOf<String>()

        // Transfer items from owner to new objects
        items.forEachIndexed { index, itemKey ->
            val qty = if (index < quantities.size) quantities[index] else 1
            
            try {
                // Check if owner has enough items
                val ownerQty = inventoryRepository.getTotalItemQuantity(ownerId, itemKey)
                val toTransfer = if (ownerQty < qty) ownerQty else qty
                
                if (toTransfer > 0) {
                    // Calculate random position for EACH item
                    val angle = Random.nextDouble() * 2.0 * kotlin.math.PI
                    val r = sqrt(Random.nextDouble()) * radius
                    val dropX = baseX + r * cos(angle)
                    val dropZ = baseZ + r * sin(angle)
                    val dropY = baseY // Keep same height for now

                    // Get item info to make the object look like the item
                    val item = itemRepository.getItemById(itemKey)
                    val itemTitle = item?.title ?: "Dropped Item"
                    val itemFile = if (item?.file?.isNotEmpty() == true) item.file else "models/chest"

                    WebSocketDebug.logMessage("DROP_ITEM_ITEM_START", "Dropping item $index: $itemKey x$toTransfer at ($dropX, $dropY, $dropZ)", context.session)

                    // Create the dropped item object
                    val dropObject = Object(
                        worldId = context.worldId,
                        title = itemTitle,
                        kind = ObjectKind.Inventory,
                        position = Position(
                            x = dropX.toFloat(),
                            y = dropY.toFloat(),
                            z = dropZ.toFloat()
                        ),
                        file = itemFile,
                        collection = "default"
                    )
                    
                    val createdObject = objectRepository.create(dropObject)
                    val objectId = createdObject.key ?: return@forEachIndexed

                    WebSocketDebug.logMessage("DROP_ITEM_TRANSFER_EXEC", "Transferring $toTransfer of $itemKey to $objectId", context.session)
                    inventoryRepository.transfer(
                        fromType = InventoryOwnerType.USER,
                        fromId = ownerId,
                        toType = InventoryOwnerType.OBJECT,
                        toId = objectId,
                        itemKey = itemKey,
                        quantity = toTransfer
                    )
                    
                    droppedSummary.add("$itemTitle x$toTransfer")
                    
                    // Broadcast the new object to all connections in the world
                    val createObjectMessage = json.encodeToString(
                        WebSocketMessage(
                            action = WebSocketAction.ADD_OBJECT,
                            data = json.encodeToString(createdObject)
                        )
                    )
                    
                    connections.forEach { conn ->
                        try {
                            conn.session.send(Frame.Text(createObjectMessage))
                        } catch (e: Throwable) {
                            // Ignore send errors
                        }
                    }
                } else {
                    WebSocketDebug.logMessage("DROP_ITEM_NO_STOCK", "Owner does not have $itemKey", context.session)
                }
            } catch (e: Throwable) {
                WebSocketDebug.logMessage("DROP_ITEM_TRANSFER_ERROR", "Error transferring item $itemKey: ${e.message}", context.session)
            }
        }

        WebSocketDebug.logMessage("DROP_ITEM_COMPLETE", "DropItem node execution finished", context.session)

        if (droppedSummary.isNotEmpty()) {
            broadcastInventoryChanged(ownerId)

            try {
                val worldTitle = world.title.ifBlank { context.worldId }
                val objectTitle = if (context.objectId.isNotEmpty()) {
                    objectRepository.getById(context.objectId)?.title ?: "Object"
                } else "Object"

                val details = droppedSummary.joinToString(", ")
                val description = "Items dropped: $details via \"$objectTitle\" in \"$worldTitle\""

                notificationRepository.create(
                    Notification(
                        userId = ownerId,
                        title = "Items dropped from inventory",
                        description = description,
                        isRead = false,
                        type = NotificationType.SYSTEM,
                        worldId = context.worldId,
                        objectId = context.objectId,
                        timestamp = Clock.System.now()
                    )
                )
            } catch (e: Throwable) {
                WebSocketDebug.logError("Failed to create owner notification for DropItem", e, context.session)
            }
        }

        return true
    }

    private suspend fun broadcastInventoryChanged(targetUserId: String) {
        val payloadMsg = json.encodeToString(
            WebSocketMessage(
                action = WebSocketAction.INVENTORY_CHANGED,
                data = json.encodeToString(InventoryChanged(targetUserId))
            )
        )
        try {
            val targets = context.activeConnections.entries.flatMap { (_, conns) ->
                ArrayList(conns).filter { it.userId == targetUserId }
            }
            targets.forEach { conn ->
                try {
                    conn.session.send(Frame.Text(payloadMsg))
                } catch (_: Throwable) {
                }
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error broadcasting INVENTORY_CHANGED", e, context.session)
        }
    }
}
