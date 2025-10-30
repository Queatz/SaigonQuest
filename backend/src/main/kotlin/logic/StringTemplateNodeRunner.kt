package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import utils.json

/**
 * Runner for StringTemplate nodes
 *
 * Formats a template string by replacing placeholders like {name} with
 * values provided through input ports or static properties.
 * Emits the resulting string to the node's string output.
 */
class StringTemplateNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse StringTemplate node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val template = getInput(PropertyKey.TEMPLATE) ?: return false

        // Collect candidate values from known property keys that could be referenced in template
        val candidates: Map<String, String> = buildMap {
            // Standard known keys
            listOf(
                PropertyKey.NAME,
                PropertyKey.TEXT,
                PropertyKey.TEXT_CONTENT,
            ).forEach { key ->
                getInput(key)?.let { put(key.name.lowercase(), it) }
            }

            // Also include any eventData inputs that are targeted to this node ("<nodeId>_in_<something>")
            eventData.forEach { (k, v) ->
                if (k is String && k.startsWith("${node.id}_in_") && v is String) {
                    val label = k.removePrefix("${node.id}_in_")
                    put(label, v)
                }
            }
        }

        // Replace placeholders {key} with matching values; leave unchanged if not found
        val regex = "\\{([a-zA-Z0-9_]+)\\}".toRegex()
        val result = regex.replace(template) { match ->
            val key = match.groupValues[1].lowercase()
            candidates[key] ?: match.value
        }

        // Emit to the first string output (TEXT)
        val outPortId = "${node.id}_out_1" // because _out_0 is trigger "Next", _out_1 is string "Text"
        eventData[outPortId] = result
        eventData["${node.id}_text"] = result

        return true
    }
}
