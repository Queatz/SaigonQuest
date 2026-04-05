package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.QuestRepository
import utils.json

/**
 * Runner for UpdatePlayerQuestState nodes
 *
 * Updates a player-local quest state value.
 */
class UpdatePlayerQuestStateNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val repository = QuestRepository()
        
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
            
            val questId = (eventData[inKey(PropertyKey.QUEST_ID)] as? String) 
                ?: nodeData.properties.find { it.key == PropertyKey.QUEST_ID }?.value
                
            val stateName = (eventData[inKey(PropertyKey.QUEST_STATE_ID)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.QUEST_STATE_ID }?.value
                
            val newValue = (eventData[inKey(PropertyKey.VALUE)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.VALUE }?.value ?: ""

            if (questId.isNullOrBlank() || stateName.isNullOrBlank()) {
                WebSocketDebug.logError("UpdatePlayerQuestState: Missing questId ($questId) or stateName ($stateName)", null, context.session)
                return false
            }

            val userId = context.connection.userId
            if (userId.isBlank()) {
                WebSocketDebug.logError("UpdatePlayerQuestState: Missing userId in context", null, context.session)
                return false
            }

            repository.updatePlayerQuestState(questId, userId, stateName, newValue)
            
            WebSocketDebug.logMessage("UPDATE_PLAYER_QUEST_STATE", "Updated Quest $questId state $stateName for $userId to $newValue", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing UpdatePlayerQuestState node", e, context.session)
            return false
        }
    }
}
