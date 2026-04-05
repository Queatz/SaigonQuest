package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import utils.json

/**
 * Runner for CompareValue nodes
 *
 * Compares two values (Equals, Greater than, Less than).
 */
class CompareValueNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("COMPARE_VALUE_NODE_EXECUTE", "Executing CompareValue node", context.session)
        
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
            
            val a = (eventData[inKey(PropertyKey.A)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.A }?.value ?: ""
                
            val b = (eventData[inKey(PropertyKey.B)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.B }?.value ?: ""
                
            val operator = (eventData[inKey(PropertyKey.OPERATOR)] as? String)
                ?: nodeData.properties.find { it.key == PropertyKey.OPERATOR }?.value ?: "Equals"

            val result = when (operator) {
                "Equals" -> a == b
                "Greater than" -> {
                    val aNum = a.toDoubleOrNull()
                    val bNum = b.toDoubleOrNull()
                    if (aNum != null && bNum != null) aNum > bNum else a > b
                }
                "Less than" -> {
                    val aNum = a.toDoubleOrNull()
                    val bNum = b.toDoubleOrNull()
                    if (aNum != null && bNum != null) aNum < bNum else a < b
                }
                else -> {
                    WebSocketDebug.logError("CompareValue: Unknown operator $operator", null, context.session)
                    false
                }
            }
            
            val outputProperty = if (result) PropertyKey.YES else PropertyKey.NO
            eventData["outputProperty"] = outputProperty
            
            WebSocketDebug.logMessage("COMPARE_VALUE_RESULT", "Compare: $a $operator $b = $result (Output: $outputProperty)", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing CompareValue node", e, context.session)
            return false
        }
    }
}
