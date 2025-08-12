package main.components.nodeeditor

import kotlinx.serialization.Serializable
import models.LogicNode
import models.nodeeditor.PropertyKey
import models.nodeeditor.PropertyType
import models.nodeeditor.PropertyValue
import models.nodeeditor.getInputPropertyDefinitions

/**
 * Base interface for all node data types
 */
interface NodeDataType

/**
 * Generic property-based node data class
 */
@Serializable
data class PropertyBasedNodeData(
    val properties: List<PropertyValue> = emptyList()
) : NodeDataType

/**
 * Utility object for parsing and serializing node data
 */
object NodeDataParser {
    private val json = utils.json
    
    /**
     * Parse node data from a JSON string based on LogicNode
     */
    fun parseNodeData(node: LogicNode, dataString: String): NodeDataType {
        val defaultData = if (dataString.isBlank() || dataString == "{}") "{}" else dataString
        
        return try {
            // Try to parse as PropertyBasedNodeData first
            json.decodeFromString<PropertyBasedNodeData>(defaultData)
        } catch (e: Throwable) {
            // If that fails, create a new PropertyBasedNodeData with default values
            PropertyBasedNodeData(
                properties = node.getInputPropertyDefinitions()
                    .filter { it.type != PropertyType.TRIGGER }
                    .map { PropertyValue(it.key, it.defaultValue) }
            )
        }
    }
    
    /**
     * Serialize node data to a JSON string
     */
    fun serializeNodeData(data: NodeDataType): String {
        return when (data) {
            is PropertyBasedNodeData -> json.encodeToString(PropertyBasedNodeData.serializer(), data)
            else -> "{}"
        }
    }
    
    /**
     * Get property value from node data
     */
    fun getPropertyValue(nodeData: NodeDataType, key: PropertyKey): String {
        return when (nodeData) {
            is PropertyBasedNodeData -> {
                nodeData.properties.find { it.key == key }?.value ?: ""
            }
            else -> ""
        }
    }
}
