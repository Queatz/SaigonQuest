package models.nodeeditor

import kotlinx.serialization.Serializable
import models.LogicNode

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
    POSITION,
    
    // HasItem properties
    ITEM_ID,
    QUANTITY,
    YES,
    NO
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
        
        // HasItem node properties
        is LogicNode.HasItem -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ITEM_ID,
                name = "Item",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUANTITY,
                name = "Quantity",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT,
                defaultValue = "1"
            ),
            NodePropertyDefinition(
                key = PropertyKey.YES,
                name = "Yes",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO,
                name = "No",
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
