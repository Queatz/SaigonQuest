package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import models.*
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.InventoryRepository
import repository.NotificationRepository
import repository.QuestRepository
import routes.WebSocketMessage
import utils.json
import kotlin.time.Clock

/**
 * Runner for UpdatePlayerQuestStep nodes
 */
class UpdatePlayerQuestStepNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val repository = QuestRepository()
        
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = try {
                json.decodeFromString<PropertyBasedNodeData>(jsonData)
            } catch (e: Throwable) {
                PropertyBasedNodeData()
            }

            fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
            
            val questId = (eventData[inKey(PropertyKey.QUEST_ID)] as? String) 
                ?: nodeData.properties.find { it.key == PropertyKey.QUEST_ID }?.value
                
            val stepIndexStr = (eventData[inKey(PropertyKey.STEP_INDEX)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.STEP_INDEX }?.value
                
            val stepIndex = stepIndexStr?.toIntOrNull() ?: 0

            val completedStr = (eventData[inKey(PropertyKey.COMPLETED)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.COMPLETED }?.value
                
            val completed = completedStr?.toBoolean() ?: false

            if (questId.isNullOrBlank()) {
                WebSocketDebug.logError("UpdatePlayerQuestStep: Missing questId ($questId)", null, context.session)
                return false
            }

            val userId = context.connection.userId
            if (userId.isBlank()) {
                WebSocketDebug.logError("UpdatePlayerQuestStep: Missing userId in context", null, context.session)
                return false
            }

            val currentProgress = repository.getPlayerQuestProgress(questId, userId)
            val wasCompleted = currentProgress?.completed ?: false

            repository.updatePlayerQuestProgress(questId, userId, stepIndex, completed)
            
            WebSocketDebug.logMessage("UPDATE_PLAYER_QUEST_STEP", "Updated Quest $questId progress to step $stepIndex (completed=$completed) for $userId", context.session)

            if (completed && !wasCompleted) {
                val quest = repository.getQuestById(questId)
                if (quest != null) {
                    val invRepo = InventoryRepository()
                    for (reward in quest.rewards) {
                        try {
                            invRepo.addOrCombineItemToUserInventory(userId, reward.itemId, reward.quantity)
                        } catch (e: Throwable) {
                            WebSocketDebug.logError("Failed to award quest reward: ${reward.itemId}", e, context.session)
                        }
                    }

                    // Notify client of inventory change
                    if (quest.rewards.isNotEmpty()) {
                        try {
                            val inventoryChangedMessage = WebSocketMessage(
                                action = WebSocketAction.INVENTORY_CHANGED,
                                data = json.encodeToString(InventoryChanged(userId))
                            )
                            context.session.send(json.encodeToString(inventoryChangedMessage))
                        } catch (e: Throwable) {
                            WebSocketDebug.logError("Failed to send inventory changed message", e, context.session)
                        }
                    }

                    // Send notification
                    try {
                        val notificationRepo = NotificationRepository()
                        notificationRepo.create(
                            Notification(
                                userId = userId,
                                title = "Quest Completed",
                                description = "You have completed the quest: ${quest.name}",
                                type = NotificationType.QUEST_COMPLETED,
                                timestamp = Clock.System.now()
                            )
                        )
                    } catch (e: Throwable) {
                        WebSocketDebug.logError("Failed to send Quest Completed notification", e, context.session)
                    }
                }
            }

            // Notify client to refresh quest UI
            try {
                val refreshMessage = WebSocketMessage(
                    action = WebSocketAction.QUEST_PROGRESS_UPDATED,
                    data = json.encodeToString(mapOf("questId" to questId))
                )
                context.session.send(json.encodeToString(refreshMessage))
            } catch (e: Throwable) {
                WebSocketDebug.logError("Failed to send quest refresh message", e, context.session)
            }
            
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing UpdatePlayerQuestStep node", e, context.session)
            return false
        }
    }
}
