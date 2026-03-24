package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import utils.json

/**
 * Runner for JoinList nodes
 *
 * Formats and joins a comma-separated list into a single string.
 * Inputs:
 *   - List: Comma-separated string of items
 *   - Template: Format for each item, using {item} as placeholder
 *   - Separator: String used to join formatted items
 * Output:
 *   - Text: Resulting joined string
 */
class JoinListNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse JoinList node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val listStr = getInput(PropertyKey.LIST_CONTENT) ?: ""
        val template = getInput(PropertyKey.ITEM_TEMPLATE) ?: "{item}"
        val separator = getInput(PropertyKey.ITEM_SEPARATOR) ?: ", "

        if (listStr.isBlank()) {
            val result = ""
            eventData["${node.id}_out_0"] = result
            eventData["${node.id}_text"] = result
            return true
        }

        val items = listStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val joinedResult = items.joinToString(separator) { item ->
            template.replace("{item}", item)
        }

        // Emit to string output (TEXT at out_0)
        eventData["${node.id}_out_0"] = joinedResult
        // Convenience alias
        eventData["${node.id}_text"] = joinedResult

        return true
    }
}
