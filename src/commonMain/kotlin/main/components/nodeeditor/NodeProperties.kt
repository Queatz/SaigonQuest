package main.components.nodeeditor

import kotlinx.serialization.Serializable
import models.LogicNode
import models.NodeCategory

/**
 * Enum representing the type of a node property
 */
@Serializable
enum class PropertyType {
    STRING,
    NUMBER,
    BOOLEAN,
    VECTOR3,
    COLOR,
    ENUM,
    TRIGGER // Special type for execution flow
}

/**
 * Enum representing the direction of a property (input or output)
 */
@Serializable
enum class PropertyDirection {
    INPUT,
    OUTPUT
}

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
 * Data class representing a node property definition
 */
@Serializable
data class NodePropertyDefinition(
    val key: PropertyKey,
    val name: String,
    val type: PropertyType,
    val direction: PropertyDirection,
    val defaultValue: String = "",
    val description: String = ""
)

/**
 * Data class representing a property value
 */
@Serializable
data class PropertyValue(
    val key: PropertyKey,
    val value: String
)

/**
 * Extension function to get property definitions for a LogicNode
 */
fun LogicNode.getPropertyDefinitions(): List<NodePropertyDefinition> {
    return when (this) {
        // Teleport node properties
        is LogicNode.Teleport -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TARGET_WORLD_ID,
                name = "Target World URL",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TARGET_POSITION,
                name = "Target Position",
                type = PropertyType.VECTOR3,
                direction = PropertyDirection.INPUT,
                defaultValue = "0,0,0"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        
        // VisitorInteract node properties
        is LogicNode.VisitorInteract -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        
        // PlayerInteract node properties
        is LogicNode.PlayerInteract -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        
        // Dialogue node properties
        is LogicNode.Dialogue -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Text Content",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.BUTTONS,
                name = "Buttons",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "OK" // Default button text
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        
        // UserLanguage node properties
        is LogicNode.UserLanguage -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "English",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Tiếng Việt",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
    }
}

/**
 * Extension function to get input property definitions for a LogicNode
 */
fun LogicNode.getInputPropertyDefinitions(): List<NodePropertyDefinition> {
    return getPropertyDefinitions().filter { it.direction == PropertyDirection.INPUT }
}

/**
 * Extension function to get output property definitions for a LogicNode
 */
fun LogicNode.getOutputPropertyDefinitions(): List<NodePropertyDefinition> {
    return getPropertyDefinitions().filter { it.direction == PropertyDirection.OUTPUT }
}

/**
 * Extension function to convert PropertyType to PortData dataType
 */
fun PropertyType.toPortDataType(): String {
    return when (this) {
        PropertyType.STRING -> "string"
        PropertyType.NUMBER -> "number"
        PropertyType.BOOLEAN -> "boolean"
        PropertyType.VECTOR3 -> "vector3"
        PropertyType.COLOR -> "color"
        PropertyType.ENUM -> "enum"
        PropertyType.TRIGGER -> "trigger"
    }
}
