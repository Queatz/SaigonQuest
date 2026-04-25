package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.UserRepository
import utils.json

/**
 * Runner for CheckPlayerSkill nodes
 */
class CheckPlayerSkillNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val userRepository = UserRepository()
        
        WebSocketDebug.logMessage("CHECK_SKILL_NODE_EXECUTE", "Executing CheckPlayerSkill node", context.session)
        
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
                WebSocketDebug.logError("Missing skillId in CheckPlayerSkill node", null, context.session)
                return false
            }

            val operator = fromEventOrNode("operator", PropertyKey.OPERATOR) ?: "Equals"
            val value = fromEventOrNode("value", PropertyKey.VALUE)?.toIntOrNull() ?: 0
            
            val user = userRepository.getById(context.connection.userId) ?: return false
            val currentLevel = user.skillLevels[skillId] ?: 0
            
            val result = when (operator) {
                "At least" -> currentLevel >= value
                "At most" -> currentLevel <= value
                "Equals" -> currentLevel == value
                else -> false
            }
            
            eventData["outputProperty"] = if (result) PropertyKey.YES else PropertyKey.NO
            
            WebSocketDebug.logMessage("CHECK_SKILL_RESULT", "Checked skill $skillId: $currentLevel $operator $value = $result", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing CheckPlayerSkill node", e, context.session)
            return false
        }
    }
}
