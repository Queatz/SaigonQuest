package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.QuestRepository
import utils.json

/**
 * Runner for QuestPlayerStatus nodes
 */
class QuestPlayerStatusNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
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
                WebSocketDebug.logError("QuestPlayerStatus: Missing questId ($questId)", null, context.session)
                return false
            }

            val userId = context.connection.userId
            if (userId.isBlank()) {
                WebSocketDebug.logError("QuestPlayerStatus: Missing userId in context", null, context.session)
                return false
            }

            val progress = repository.getPlayerQuestProgress(questId, userId)
            
            val outputProperty = when {
                progress == null -> PropertyKey.NOT_STARTED
                progress.completed -> PropertyKey.COMPLETE
                else -> PropertyKey.IN_PROGRESS
            }
            
            WebSocketDebug.logMessage("QUEST_PLAYER_STATUS", "Quest $questId status for $userId: $outputProperty", context.session)
            
            eventData["outputProperty"] = outputProperty
            
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing QuestPlayerStatus node", e, context.session)
            return false
        }
    }
}
