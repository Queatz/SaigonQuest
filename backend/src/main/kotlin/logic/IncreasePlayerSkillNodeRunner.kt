package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.UserRepository
import utils.json

/**
 * Runner for IncreasePlayerSkill nodes
 */
class IncreasePlayerSkillNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val userRepository = UserRepository()
        
        WebSocketDebug.logMessage("INCREASE_SKILL_NODE_EXECUTE", "Executing IncreasePlayerSkill node", context.session)
        
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            fun inKey(name: String) = "${node.id}_in_${name}"
            fun fromEventOrNode(nameLower: String, key: PropertyKey): String? {
                val eventVal = eventData[inKey(nameLower)] as? String
                if (!eventVal.isNullOrBlank()) return eventVal
                return nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }
            }

            val skillId = fromEventOrNode("skill_id", PropertyKey.SKILL_ID) ?: run {
                WebSocketDebug.logError("Missing skillId in IncreasePlayerSkill node", null, context.session)
                return false
            }

            val amount = fromEventOrNode("amount", PropertyKey.QUANTITY)?.toIntOrNull() ?: 1
            
            val user = userRepository.getById(context.connection.userId) ?: return false
            val currentLevel = user.skillLevels[skillId] ?: 0
            val newLevel = currentLevel + amount
            
            userRepository.updateUserSkillLevel(context.connection.userId, skillId, newLevel)
            
            WebSocketDebug.logMessage("INCREASE_SKILL_SUCCESS", "Increased skill $skillId by $amount (new level: $newLevel) for user ${context.connection.userId}", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing IncreasePlayerSkill node", e, context.session)
            return false
        }
    }
}
