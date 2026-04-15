package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.QuestRepository
import utils.json

/**
 * Runner for QuestStep nodes
 *
 * Checks if a specific quest step is completed for the player.
 */
class QuestStepNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
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
            val targetStepIndex = stepIndexStr?.toIntOrNull() ?: 0

            if (questId.isNullOrBlank()) {
                WebSocketDebug.logError("QuestStep: Missing questId ($questId)", null, context.session)
                return false
            }

            val userId = context.connection.userId
            if (userId.isBlank()) {
                WebSocketDebug.logError("QuestStep: Missing userId in context", null, context.session)
                return false
            }

            val progress = repository.getPlayerQuestProgress(questId, userId)
            
            val isCompleted = progress != null && (progress.completed || progress.currentStepIndex > targetStepIndex)
            
            val outputProperty = if (isCompleted) PropertyKey.COMPLETE else PropertyKey.INCOMPLETE
            
            WebSocketDebug.logMessage("QUEST_STEP_STATUS", "Quest $questId step $targetStepIndex status for $userId: $outputProperty", context.session)
            
            eventData["outputProperty"] = outputProperty
            
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing QuestStep node", e, context.session)
            return false
        }
    }
}
