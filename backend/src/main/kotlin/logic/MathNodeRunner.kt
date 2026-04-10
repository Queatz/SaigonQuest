package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import utils.json

/**
 * Runner for Math nodes
 *
 * Performs arithmetic operations (Add, Subtract, Multiply, Divide).
 */
class MathNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("MATH_NODE_EXECUTE", "Executing Math node", context.session)
        
        try {
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
            
            val aStr = eventData[inKey(PropertyKey.A)]?.toString()
                ?: nodeData.properties.find { it.key == PropertyKey.A }?.value ?: "0"
                
            val bStr = eventData[inKey(PropertyKey.B)]?.toString()
                ?: nodeData.properties.find { it.key == PropertyKey.B }?.value ?: "0"
                
            val operator = eventData[inKey(PropertyKey.MATH_OPERATOR)]?.toString()
                ?: nodeData.properties.find { it.key == PropertyKey.MATH_OPERATOR }?.value ?: "Add"

            val a = aStr.toDoubleOrNull() ?: 0.0
            val b = bStr.toDoubleOrNull() ?: 0.0

            val resultValue = when (operator) {
                "Add" -> a + b
                "Subtract" -> a - b
                "Multiply" -> a * b
                "Divide" -> if (b != 0.0) a / b else 0.0
                else -> {
                    WebSocketDebug.logError("Math: Unknown operator $operator", null, context.session)
                    0.0
                }
            }
            
            val resultString = if (resultValue % 1 == 0.0) resultValue.toLong().toString() else resultValue.toString()
            
            // Output to port (index 0 is RESULT)
            eventData["${node.id}_out_0"] = resultString
            
            WebSocketDebug.logMessage("MATH_RESULT", "Math: $a $operator $b = $resultString", context.session)
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing Math node", e, context.session)
            return false
        }
    }
}
