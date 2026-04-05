package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.QuestRepository
import utils.json

/**
 * Runner for UpdateQuestState nodes
 *
 * Updates a global quest state value.
 */
class UpdateQuestStateNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
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
                WebSocketDebug.logError("UpdateQuestState: Missing questId ($questId) or stateName ($stateName)", null, context.session)
                return false
            }

            repository.updateQuestState(questId, stateName, newValue)
            
            WebSocketDebug.logMessage("UPDATE_QUEST_STATE", "Updated Quest $questId state $stateName to $newValue", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing UpdateQuestState node", e, context.session)
            return false
        }
    }
}
