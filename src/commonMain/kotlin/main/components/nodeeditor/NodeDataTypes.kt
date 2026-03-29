package main.components.nodeeditor

import models.LogicNode
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import models.nodeeditor.PropertyType
import models.nodeeditor.PropertyValue
import models.nodeeditor.getInputPropertyDefinitions

/**
 * Utility object for parsing and serializing node data
 */
object NodeDataParser {
    private val json = utils.json
    
    /**
     * Parse node data from a JSON string based on LogicNode
     */
    fun parseNodeData(node: LogicNode, dataString: String): PropertyBasedNodeData {
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
    fun serializeNodeData(data: PropertyBasedNodeData): String {
        return json.encodeToString(PropertyBasedNodeData.serializer(), data)
    }
    
    /**
     * Get property value from node data
     */
    fun getPropertyValue(nodeData: PropertyBasedNodeData, key: PropertyKey): String {
        return nodeData.properties.find { it.key == key }?.value ?: ""
    }
}
