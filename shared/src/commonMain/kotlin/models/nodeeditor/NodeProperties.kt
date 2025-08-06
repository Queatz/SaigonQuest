package models.nodeeditor

import kotlinx.serialization.Serializable

/**
 * Enum for property keys to ensure type safety
 */
@Serializable
enum class PropertyKey {
    // Common properties
    TRIGGER,
    NEXT,
    COMPLETE,
    
    // Text properties
    TEXT_CONTENT,
    
    // Dialogue properties
    BUTTONS,
    
    // Teleport properties
    TARGET_WORLD_ID,
    TARGET_POSITION,
    
    // Player/Visitor/World/Object info properties
    NAME,
    ID,
    POSITION
}

/**
 * Data class representing a property value
 */
@Serializable
data class PropertyValue(
    val key: PropertyKey,
    val value: String
)

/**
 * Generic property-based node data class
 */
@Serializable
data class PropertyBasedNodeData(
    val properties: List<PropertyValue> = emptyList()
)
