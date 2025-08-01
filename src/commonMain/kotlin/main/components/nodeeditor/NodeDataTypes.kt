package main.components.nodeeditor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
 * Data class for "Show buttons" dialogue nodes
 */
@Serializable
data class ButtonsNodeData(
    val buttons: String = ""
) : NodeDataType

/**
 * Data class for "Teleport player" action nodes
 */
@Serializable
data class TeleportNodeData(
    val position: String = "0,0,0"
) : NodeDataType

/**
 * Data class for "Play sound" action nodes
 */
@Serializable
data class SoundNodeData(
    val sound: String = ""
) : NodeDataType

/**
 * Data class for "Show effect" action nodes
 */
@Serializable
data class EffectNodeData(
    val effect: String = ""
) : NodeDataType

/**
 * Empty node data for nodes that don't have any specific data
 */
@Serializable
data class EmptyNodeData(
    val dummy: String = ""
) : NodeDataType

/**
 * Utility object for parsing and serializing node data
 */
object NodeDataParser {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parse node data from a JSON string based on node type and label
     */
    fun parseNodeData(nodeType: String, nodeLabel: String, dataString: String): NodeDataType {
        val defaultData = if (dataString.isBlank() || dataString == "{}") "{}" else dataString
        
        return try {
            when (nodeType) {
                "dialogue" -> {
                    when (nodeLabel) {
                        "Present text" -> json.decodeFromString<TextNodeData>(defaultData)
                        "Show buttons" -> json.decodeFromString<ButtonsNodeData>(defaultData)
                        else -> EmptyNodeData()
                    }
                }
                "action" -> {
                    when (nodeLabel) {
                        "Teleport player" -> json.decodeFromString<TeleportNodeData>(defaultData)
                        "Play sound" -> json.decodeFromString<SoundNodeData>(defaultData)
                        "Show effect" -> json.decodeFromString<EffectNodeData>(defaultData)
                        else -> EmptyNodeData()
                    }
                }
                else -> EmptyNodeData()
            }
        } catch (e: Exception) {
            // If parsing fails, return an empty data object of the appropriate type
            when (nodeType) {
                "dialogue" -> {
                    when (nodeLabel) {
                        "Present text" -> TextNodeData()
                        "Show buttons" -> ButtonsNodeData()
                        else -> EmptyNodeData()
                    }
                }
                "action" -> {
                    when (nodeLabel) {
                        "Teleport player" -> TeleportNodeData()
                        "Play sound" -> SoundNodeData()
                        "Show effect" -> EffectNodeData()
                        else -> EmptyNodeData()
                    }
                }
                else -> EmptyNodeData()
            }
        }
    }
    
    /**
     * Serialize node data to a JSON string
     */
    fun serializeNodeData(data: NodeDataType): String {
        return when (data) {
            is TextNodeData -> json.encodeToString(TextNodeData.serializer(), data)
            is ButtonsNodeData -> json.encodeToString(ButtonsNodeData.serializer(), data)
            is TeleportNodeData -> json.encodeToString(TeleportNodeData.serializer(), data)
            is SoundNodeData -> json.encodeToString(SoundNodeData.serializer(), data)
            is EffectNodeData -> json.encodeToString(EffectNodeData.serializer(), data)
            is EmptyNodeData -> "{}"
            else -> "{}"
        }
    }
}
