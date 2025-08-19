package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import models.*
import models.nodeeditor.NodeDataModel
import models.NotificationData
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import org.slf4j.LoggerFactory
import repository.InventoryRepository
import repository.ItemRepository
import repository.NotificationRepository
import repository.WorldRepository
import routes.WebSocketMessage
import routes.handlers.WebSocketUtils
import utils.json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Runner for GiveItems nodes
 *
 * Prompts the current user to receive specified items from the world owner.
 * If accepted, moves quantities from the world owner's inventory to the player's inventory
 * and broadcasts inventory change notifications to both users if connected.
 * Sets eventData["outputProperty"] to PropertyKey.YES/NO/NOT_ENOUGH for branching.
 */
class GiveItemsNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(GiveItemsNodeRunner::class.java)
        private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        fun processConfirmation(confirmation: TakeItemsConfirmation): Boolean {
            val id = confirmation.requestId
            val deferred = pending[id]
            if (deferred == null) {
                logger.debug("GiveItems processConfirmation: no pending for id={}", id)
                return false
            }
            val ok = deferred.complete(confirmation.accepted)
            logger.debug("GiveItems processConfirmation: completed id={} ok={} accepted={}", id, ok, confirmation.accepted)
            return ok
        }
    }

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            val itemsCsv = nodeData.properties.find { it.key == PropertyKey.ITEMS }?.value?.trim().orEmpty()
            val quantitiesCsv = nodeData.properties.find { it.key == PropertyKey.QUANTITIES }?.value?.trim().orEmpty()

            if (itemsCsv.isBlank()) {
                WebSocketDebug.logError("GiveItems: ITEMS property is missing", null, context.session)
                return false
            }

            val itemIds = itemsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val quantities = quantitiesCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(1) }

            // Normalize quantities
            val normalizedQuantities = itemIds.mapIndexed { idx, _ -> quantities.getOrNull(idx) ?: 1 }

            // Resolve world owner
            val worldRepo = WorldRepository()
            val world = worldRepo.getById(context.worldId)
            if (world == null || world.userId.isBlank()) {
                WebSocketDebug.logError("GiveItems: World or owner not found for worldId=${context.worldId}", null, context.session)
                return false
            }
            val ownerUserId = world.userId

            // Prepare item details for prompt
            val itemRepo = ItemRepository()
            val entries = itemIds.mapIndexedNotNull { idx, itemId ->
                val qty = (normalizedQuantities.getOrNull(idx) ?: 1).coerceAtLeast(1)
                val item = itemRepo.getItemById(itemId)
                if (item == null) {
                    WebSocketDebug.logError("GiveItems: Item not found: $itemId", null, context.session)
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
                WebSocketDebug.logError("GiveItems: No valid items to show", null, context.session)
                return false
            }

            // Build and send prompt (reuse SHOW_TAKE_ITEMS and prompt model)
            val requestId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Boolean>()
            logger.debug("GiveItems register: id={}", requestId)
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
            WebSocketDebug.logMessage("GIVE_ITEMS_SHOW_SEND", "requestId=$requestId nodeId=${node.id}", context.session)
            context.connection.session.send(payload)

            // Await confirmation up to 5 minutes (treat timeout same as decline)
            val accepted = try {
                WebSocketDebug.logMessage("GIVE_ITEMS_AWAIT_START", "requestId=$requestId", context.session)
                withTimeoutOrNull(300.seconds) { deferred.await() } ?: false
            } finally {
                pending.remove(requestId)
                if (!deferred.isCompleted) deferred.cancel()
            }

            // If declined, branch NO
            if (!accepted) {
                WebSocketDebug.logMessage("GIVE_ITEMS_DECLINED_OR_TIMEOUT", "requestId=$requestId", context.session)
                eventData["outputProperty"] = PropertyKey.NO
                return true
            }

            val invRepo = InventoryRepository()

            // Pre-check: ensure the owner has enough of every requested item
            val insufficient = entries.any { entry ->
                try {
                    val available = invRepo.getTotalItemQuantity(ownerUserId, entry.itemId)
                    available < entry.quantity
                } catch (_: Throwable) { true }
            }
            if (insufficient) {
                // Notify world owner that a player attempted but owner lacks items (DB + WebSocket)
                try {
                    val repo = NotificationRepository()
                    val world = WorldRepository().getById(context.worldId)
                    val objectId = context.objectId
                    val objectTitle = try { repository.ObjectRepository().getById(objectId)?.title ?: "Object" } catch (_: Throwable) { "Object" }
                    val details = entries.joinToString(", ") { "${it.title} x${it.quantity}" }
                    val description = "${context.connection.user.username.ifBlank { context.connection.userId }} tried to receive: $details via \"$objectTitle\" in \"${world?.title ?: context.worldId}\" but there weren't enough items."
                    val created = repo.create(
                        Notification(
                            userId = ownerUserId,
                            title = "Item request failed",
                            description = description,
                            isRead = false,
                            type = NotificationType.SYSTEM,
                            worldId = context.worldId,
                            objectId = objectId,
                            otherUserId = context.connection.userId,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Failed to create/broadcast owner notification for GiveItems NOT_ENOUGH", e, context.session)
                }
                eventData["outputProperty"] = PropertyKey.NOT_ENOUGH
                return true
            }

            // Perform transfers owner -> player
            for (entry in entries) {
                try {
                    invRepo.transfer(
                        fromType = InventoryOwnerType.USER,
                        fromId = ownerUserId,
                        toType = InventoryOwnerType.USER,
                        toId = context.connection.userId,
                        itemKey = entry.itemId,
                        quantity = entry.quantity
                    )
                } catch (e: Throwable) {
                    WebSocketDebug.logError("GiveItems: Transfer failed for item=${entry.itemId}", e, context.session)
                    if (e is IllegalArgumentException && e.message?.contains("Not enough quantity") == true) {
                        // Notify owner on failure (DB + WebSocket)
                        try {
                            val repo = NotificationRepository()
                            val world = WorldRepository().getById(context.worldId)
                            val objectId = context.objectId
                            val objectTitle = try { repository.ObjectRepository().getById(objectId)?.title ?: "Object" } catch (_: Throwable) { "Object" }
                            val description = "${context.connection.user.username.ifBlank { context.connection.userId }} tried to receive ${entry.title} x${entry.quantity} via \"$objectTitle\" in \"${world?.title ?: context.worldId}\" but there weren't enough items."
                            val created = repo.create(
                                Notification(
                                    userId = ownerUserId,
                                    title = "Item request failed",
                                    description = description,
                                    isRead = false,
                                    type = NotificationType.SYSTEM,
                                    worldId = context.worldId,
                                    objectId = objectId,
                                    otherUserId = context.connection.userId,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        } catch (ex: Throwable) {
                            WebSocketDebug.logError("Failed to create/broadcast owner notification for GiveItems NOT_ENOUGH (transfer)", ex, context.session)
                        }
                        eventData["outputProperty"] = PropertyKey.NOT_ENOUGH
                        return true
                    }
                }
            }

            // Notify both users to refresh inventory if connected
            suspend fun broadcastInventoryChanged(targetUserId: String) {
                val payloadMsg = json.encodeToString(WebSocketMessage(
                    action = WebSocketAction.INVENTORY_CHANGED,
                    data = json.encodeToString(InventoryChanged(targetUserId))
                ))
                try {
                    val targets = context.activeConnections.entries.flatMap { (_, conns) ->
                        ArrayList(conns).filter { it.userId == targetUserId }
                    }
                    targets.forEach { conn ->
                        try { conn.session.send(payloadMsg) } catch (_: Throwable) {}
                    }
                } catch (e: Throwable) {
                    WebSocketDebug.logError("Error broadcasting INVENTORY_CHANGED", e, context.session)
                }
            }
            broadcastInventoryChanged(context.connection.userId)
            broadcastInventoryChanged(ownerUserId)
            WebSocketDebug.logMessage("GIVE_ITEMS_ACCEPTED", "requestId=$requestId entries=${entries.size}", context.session)

            // Send a system notification to the world owner
            try {
                val repo = NotificationRepository()
                val world = WorldRepository().getById(context.worldId)
                val objectId = context.objectId
                val objectTitle = try { repository.ObjectRepository().getById(objectId)?.title ?: "Object" } catch (_: Throwable) { "Object" }
                val title = "Items given to ${context.connection.user.username.ifBlank { context.connection.userId }}"
                val details = entries.joinToString(", ") { "${it.title} x${it.quantity}" }
                val description = "${context.connection.user.username.ifBlank { context.connection.userId }} received: $details via \"$objectTitle\" in \"${world?.title ?: context.worldId}\""
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
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Throwable) {
                WebSocketDebug.logError("Failed to create owner notification for GiveItems", e, context.session)
            }

            // Branch YES
            eventData["outputProperty"] = PropertyKey.YES
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing GiveItems node", e, context.session)
            return false
        }
    }
}
