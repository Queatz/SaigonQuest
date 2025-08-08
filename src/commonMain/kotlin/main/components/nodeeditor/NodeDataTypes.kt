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
 * Data class for "Present text" dialogue nodes
 */
@Serializable
data class TextNodeData(
    val text: String = ""
) : NodeDataType

/**
 * Data class for "Teleport player" action nodes
 */
@Serializable
data class TeleportNodeData(
    val worldId: String = "",
    val position: String = "0,0,0"
) : NodeDataType

/**
 * Empty node data for nodes that don't have any specific data
 */
@Serializable
data class EmptyNodeData(
    val dummy: String = ""
) : NodeDataType

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
        } catch (e: Exception) {
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
            is TextNodeData -> json.encodeToString(TextNodeData.serializer(), data)
            is TeleportNodeData -> json.encodeToString(TeleportNodeData.serializer(), data)
            is EmptyNodeData -> "{}"
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
            is TextNodeData -> {
                if (key == PropertyKey.TEXT_CONTENT) nodeData.text else ""
            }
            is TeleportNodeData -> {
                when (key) {
                    PropertyKey.TARGET_WORLD_ID -> nodeData.worldId
                    PropertyKey.TARGET_POSITION -> nodeData.position
                    else -> ""
                }
            }
            else -> ""
        }
    }
    
    /**
     * Set property value in node data
     */
    fun setPropertyValue(nodeData: NodeDataType, key: PropertyKey, value: String): NodeDataType {
        return when (nodeData) {
            is PropertyBasedNodeData -> {
                val updatedProperties = nodeData.properties.toMutableList()
                val existingProperty = updatedProperties.find { it.key == key }
                
                if (existingProperty != null) {
                    // Update existing property
                    val index = updatedProperties.indexOf(existingProperty)
                    updatedProperties[index] = PropertyValue(key, value)
                } else {
                    // Add new property
                    updatedProperties.add(PropertyValue(key, value))
                }
                
                nodeData.copy(properties = updatedProperties)
            }
            is TextNodeData -> {
                if (key == PropertyKey.TEXT_CONTENT) {
                    nodeData.copy(text = value)
                } else {
                    nodeData
                }
            }
            is TeleportNodeData -> {
                when (key) {
                    PropertyKey.TARGET_WORLD_ID -> nodeData.copy(worldId = value)
                    PropertyKey.TARGET_POSITION -> nodeData.copy(position = value)
                    else -> nodeData
                }
            }
            else -> nodeData
        }
    }
}
