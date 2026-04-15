package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import models.Notification
import models.NotificationType
import models.WebSocketAction
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.NotificationRepository
import repository.QuestRepository
import routes.WebSocketMessage
import utils.json
import kotlin.time.Clock

/**
 * Runner for StartQuest nodes
 */
class StartQuestNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
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

            if (questId.isNullOrBlank()) {
                WebSocketDebug.logError("StartQuest: Missing questId ($questId)", null, context.session)
                return false
            }

            val userId = context.connection.userId
            if (userId.isBlank()) {
                WebSocketDebug.logError("StartQuest: Missing userId in context", null, context.session)
                return false
            }

            // Start the quest at step 0 if not already started, or just set it to 0
            repository.updatePlayerQuestProgress(questId, userId, 0, false)
            
            WebSocketDebug.logMessage("START_QUEST", "Started Quest $questId for $userId", context.session)

            // Send notification
            try {
                val quest = repository.getQuestById(questId)
                val questName = quest?.name ?: "a quest"
                val notificationRepo = NotificationRepository()
                notificationRepo.create(
                    Notification(
                        userId = userId,
                        title = "Quest Started",
                        description = "You have started the quest: $questName",
                        type = NotificationType.QUEST_STARTED,
                        timestamp = Clock.System.now()
                    )
                )
            } catch (e: Throwable) {
                WebSocketDebug.logError("Failed to send Quest Started notification", e, context.session)
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
            WebSocketDebug.logError("Error executing StartQuest node", e, context.session)
            return false
        }
    }
}
