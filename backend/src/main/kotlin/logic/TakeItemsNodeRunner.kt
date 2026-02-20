package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import models.*
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.InventoryRepository
import repository.ItemRepository
import repository.NotificationRepository
import repository.WorldRepository
import routes.WebSocketMessage
import utils.json
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Runner for TakeItems nodes
 *
 * Prompts the current user to transfer specified items to the world owner.
 * If accepted, moves quantities from the player's inventory to the world owner's inventory
 * and broadcasts inventory change notifications to both users if connected.
 * Sets eventData["outputProperty"] to PropertyKey.YES/NO/NOT_ENOUGH for branching.
 */
class TakeItemsNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(TakeItemsNodeRunner::class.java)
        private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        fun processConfirmation(confirmation: TakeItemsConfirmation): Boolean {
            val id = confirmation.requestId
            val deferred = pending[id]
            if (deferred == null) {
                logger.debug("TakeItems processConfirmation: no pending for id={}", id)
                return false
            }
            val ok = deferred.complete(confirmation.accepted)
            logger.debug("TakeItems processConfirmation: completed id={} ok={} accepted={}", id, ok, confirmation.accepted)
            return ok
        }
    }

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            fun inKey(name: String) = "${node.id}_in_${name}"
            fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
                val eventVal = eventData[inKey(nameLower)] as? String
                if (!eventVal.isNullOrBlank()) return eventVal
                return nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }
            }

            val itemsCsv = fromEventOrNode("items", PropertyKey.ITEMS)?.trim().orEmpty()
            val quantitiesCsv = fromEventOrNode("quantities", PropertyKey.QUANTITIES)?.trim().orEmpty()

            if (itemsCsv.isBlank()) {
                WebSocketDebug.logError("TakeItems: ITEMS property is missing", null, context.session)
                return false
            }

            val itemIds = itemsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val quantities = quantitiesCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(1) }

            // Normalize quantities: if fewer provided, pad with 1s; if more, truncate
            val normalizedQuantities = itemIds.mapIndexed { idx, _ -> quantities.getOrNull(idx) ?: 1 }

            // Resolve world owner
            val worldRepo = WorldRepository()
            val world = worldRepo.getById(context.worldId)
            if (world == null || world.userId.isBlank()) {
                WebSocketDebug.logError("TakeItems: World or owner not found for worldId=${context.worldId}", null, context.session)
                return false
            }
            val ownerUserId = world.userId

            // Prepare item details for prompt
            val itemRepo = ItemRepository()
            val entries = itemIds.mapIndexedNotNull { idx, itemId ->
                val qty = (normalizedQuantities.getOrNull(idx) ?: 1).coerceAtLeast(1)
                val item = itemRepo.getItemById(itemId)
                if (item == null) {
                    WebSocketDebug.logError("TakeItems: Item not found: $itemId", null, context.session)
                    null
                } else TakeItemEntry(
                    itemId = item.key,
                    quantity = qty,
                    title = item.title,
                    file = item.file,
                    assetIndex = item.assetIndex
                )
            }
            if (entries.isEmpty()) {
                WebSocketDebug.logError("TakeItems: No valid items to show", null, context.session)
                return false
            }

            // Build and send prompt
            val requestId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Boolean>()
            logger.debug("TakeItems register: id={}", requestId)
            pending[requestId] = deferred
            val prompt = TakeItemsPrompt(
                requestId = requestId,
                userId = context.connection.userId,
                nodeId = node.id,
                items = entries
            )
            val message = WebSocketMessage(
                action = WebSocketAction.SHOW_TAKE_ITEMS,
                data = json.encodeToString(prompt)
            )
            val payload = json.encodeToString(message)
            WebSocketDebug.logMessage("TAKE_ITEMS_SHOW_SEND", "requestId=$requestId nodeId=${node.id}", context.session)
            context.connection.session.send(payload)

            // Await confirmation up to 5 minutes (treat timeout same as decline)
            val accepted = try {
                WebSocketDebug.logMessage("TAKE_ITEMS_AWAIT_START", "requestId=$requestId", context.session)
                withTimeoutOrNull(300.seconds) {
                    deferred.await()
                } ?: false
            } finally {
                // Clean up
                pending.remove(requestId)
                if (!deferred.isCompleted) deferred.cancel()
            }

            // If declined, branch NO
            if (!accepted) {
                WebSocketDebug.logMessage("TAKE_ITEMS_DECLINED_OR_TIMEOUT", "requestId=$requestId", context.session)
                eventData["outputProperty"] = PropertyKey.NO
                return true
            }

            // Perform transfers
            val invRepo = InventoryRepository()

            // Pre-check: ensure the player has enough of every requested item
            val insufficient = entries.any { entry ->
                try {
                    val available = invRepo.getTotalItemQuantity(context.connection.userId, entry.itemId)
                    available < entry.quantity
                } catch (_: Throwable) {
                    true
                }
            }
            if (insufficient) {
                // Notify world owner that a player attempted but lacks items
                try {
                    val repo = NotificationRepository()
                    val objectId = context.objectId
                    val objectTitle = try { repository.ObjectRepository().getById(objectId)?.title ?: "Object" } catch (_: Throwable) { "Object" }
                    val details = entries.joinToString(", ") { "${it.title} x${it.quantity}" }
                    val description = "${context.connection.user.username.ifBlank { context.connection.userId }} tried to transfer: $details via \"$objectTitle\" in \"${world.title.ifBlank { context.worldId }}\" but there weren't enough items."
                    repo.create(
                        Notification(
                            userId = ownerUserId,
                            title = "Item transfer failed",
                            description = description,
                            isRead = false,
                            type = NotificationType.SYSTEM,
                            worldId = context.worldId,
                            objectId = objectId,
                            otherUserId = context.connection.userId,
                            timestamp = Clock.System.now()
                        )
                    )
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Failed to create owner notification for TakeItems NOT_ENOUGH", e, context.session)
                }
                // Branch NOT_ENOUGH when user lacks required quantity
                eventData["outputProperty"] = PropertyKey.NOT_ENOUGH
                return true
            }

            for (entry in entries) {
                try {
                    invRepo.transfer(
                        fromType = InventoryOwnerType.USER,
                        fromId = context.connection.userId,
                        toType = InventoryOwnerType.USER,
                        toId = ownerUserId,
                        itemKey = entry.itemId,
                        quantity = entry.quantity
                    )
                } catch (e: Throwable) {
                    WebSocketDebug.logError("TakeItems: Transfer failed for item=${entry.itemId}", e, context.session)
                    // If transfer fails due to insufficient quantity, branch NOT_ENOUGH
                    if (e is IllegalArgumentException && e.message?.contains("Not enough quantity") == true) {
                        // Notify owner on failure
                        try {
                            val repo = NotificationRepository()
                            val objectId = context.objectId
                            val objectTitle = try { repository.ObjectRepository().getById(objectId)?.title ?: "Object" } catch (_: Throwable) { "Object" }
                            val description = "${context.connection.user.username.ifBlank { context.connection.userId }} tried to transfer ${entry.title} x${entry.quantity} via \"$objectTitle\" in \"${world.title.ifBlank { context.worldId }}\" but there weren't enough items."
                            repo.create(
                                Notification(
                                    userId = ownerUserId,
                                    title = "Item transfer failed",
                                    description = description,
                                    isRead = false,
                                    type = NotificationType.SYSTEM,
                                    worldId = context.worldId,
                                    objectId = objectId,
                                    otherUserId = context.connection.userId,
                                    timestamp = Clock.System.now()
                                )
                            )
                        } catch (ex: Throwable) {
                            WebSocketDebug.logError("Failed to create owner notification for TakeItems NOT_ENOUGH (transfer)", ex, context.session)
                        }
                        eventData["outputProperty"] = PropertyKey.NOT_ENOUGH
                        return true
                    }
                    // Continue attempting remaining transfers for other errors
                }
            }

            // Notify both users to refresh inventory if connected
            suspend fun broadcastInventoryChanged(targetUserId: String) {
                val payload = json.encodeToString(WebSocketMessage(
                    action = WebSocketAction.INVENTORY_CHANGED,
                    data = json.encodeToString(InventoryChanged(targetUserId))
                ))
                try {
                    // Send to all worlds where this user is connected
                    val targets = context.activeConnections.entries.flatMap { (wId, conns) ->
                        ArrayList(conns).filter { it.userId == targetUserId }
                    }
                    targets.forEach { conn ->
                        try { conn.session.send(payload) } catch (_: Throwable) {}
                    }
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Error broadcasting INVENTORY_CHANGED", e, context.session)
                }
            }
            broadcastInventoryChanged(context.connection.userId)
            broadcastInventoryChanged(ownerUserId)
            WebSocketDebug.logMessage("TAKE_ITEMS_ACCEPTED", "requestId=$requestId entries=${entries.size}", context.session)

            // Send a system notification to the world owner
            try {
                val repo = NotificationRepository()
                val objectId = context.objectId
                val objectTitle = try { repository.ObjectRepository().getById(objectId)?.title ?: "Object" } catch (_: Throwable) { "Object" }
                val title = "Items received from ${context.connection.user.username.ifBlank { context.connection.userId }}"
                val details = entries.joinToString(", ") { "${it.title} x${it.quantity}" }
                val description = "${context.connection.user.username.ifBlank { context.connection.userId }} transferred: $details via \"$objectTitle\" in \"${world.title.ifBlank { context.worldId }}\""
                repo.create(
                    Notification(
                        userId = ownerUserId,
                        title = title,
                        description = description,
                        isRead = false,
                        type = NotificationType.SYSTEM,
                        worldId = context.worldId,
                        objectId = objectId,
                        otherUserId = context.connection.userId,
                        timestamp = Clock.System.now()
                    )
                )
            } catch (e: Throwable) {
                WebSocketDebug.logError("Failed to create owner notification for TakeItems", e, context.session)
            }

            // Branch YES
            eventData["outputProperty"] = PropertyKey.YES
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing TakeItems node", e, context.session)
            return false
        }
    }
}
