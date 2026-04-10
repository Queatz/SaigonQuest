package logic

import debug.WebSocketDebug
import models.nodeeditor.*
import utils.json
import kotlinx.serialization.json.*

/**
 * Runner for extracting fields from JSON strings
 */
class JsonExtractNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse JsonExtract node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun outKey(key: PropertyKey) = "${node.id}_out_${key.name.lowercase()}"
        
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val jsonString = getInput(PropertyKey.JSON)
        val extractKey = getInput(PropertyKey.KEY)

        if (jsonString.isNullOrBlank() || extractKey.isNullOrBlank()) {
            return true
        }

        try {
            val element = json.parseToJsonElement(jsonString)
            val value = if (element is JsonObject) {
                element[extractKey]?.let {
                    if (it is JsonPrimitive) it.content else it.toString()
                } ?: ""
            } else if (element is JsonArray && extractKey.toIntOrNull() != null) {
                val index = extractKey.toInt()
                if (index >= 0 && index < element.size) {
                    val item = element[index]
                    if (item is JsonPrimitive) item.content else item.toString()
                } else ""
            } else {
                ""
            }
            
            eventData[outKey(PropertyKey.VALUE)] = value
            eventData["${node.id}_out_0"] = value
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to extract key $extractKey from JSON", e, context.session)
            // We don't fail the whole execution, just set empty value
            eventData[outKey(PropertyKey.VALUE)] = ""
            eventData["${node.id}_out_0"] = ""
        }

        return true
    }
}
