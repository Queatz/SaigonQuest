package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.QuestRepository
import utils.json

/**
 * Runner for PlayerQuestState nodes
 *
 * Reads a player-local quest state value.
 */
class PlayerQuestStateNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
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

            if (questId.isNullOrBlank() || stateName.isNullOrBlank()) {
                WebSocketDebug.logError("PlayerQuestState: Missing questId ($questId) or stateName ($stateName)", null, context.session)
                return false
            }

            val userId = context.connection.userId
            if (userId.isBlank()) {
                WebSocketDebug.logError("PlayerQuestState: Missing userId in context", null, context.session)
                return false
            }

            val state = repository.getPlayerQuestState(questId, userId, stateName)
            val value = state?.value ?: ""
            
            // Output to port (index 0 is VALUE)
            eventData["${node.id}_out_0"] = value
            
            WebSocketDebug.logMessage("PLAYER_QUEST_STATE_VALUE", "Quest $questId state $stateName for $userId = $value", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing PlayerQuestState node", e, context.session)
            return false
        }
    }
}
