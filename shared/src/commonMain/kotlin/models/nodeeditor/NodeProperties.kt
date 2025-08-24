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
    IMAGE_URL,

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
    NO,

    // TakeItems specific output when player lacks items
    NOT_ENOUGH,

    // TakeItems properties
    ITEMS,
    QUANTITIES,
    
    // OpenUrl properties
    URL,

    // Delay properties
    DELAY_SECONDS
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
        // Delay node properties
        is LogicNode.Delay -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.DELAY_SECONDS,
                name = "Seconds",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT,
                defaultValue = "5"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        // ObjectOwnerNotification node properties
        is LogicNode.ObjectOwnerNotification -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Message",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            )
        )
        // ObjectNotification node properties
        is LogicNode.ObjectNotification -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Message",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            )
        )
        
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
                key = PropertyKey.IMAGE_URL,
                name = "Image URL",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "",
                description = "Optional image shown above the text"
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
        
        // OpenUrl node properties
        is LogicNode.OpenUrl -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.URL,
                name = "URL",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "https://"
            )
        )

        // TakeItems node properties
        is LogicNode.TakeItems -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ITEMS,
                name = "Items",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "",
                description = "Comma-separated item IDs"
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUANTITIES,
                name = "Quantities",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "1",
                description = "Comma-separated quantities matching items"
            ),
            NodePropertyDefinition(
                key = PropertyKey.YES,
                name = "Accepted",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO,
                name = "Declined",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NOT_ENOUGH,
                name = "Not enough",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT,
                description = "Player does not have enough items"
            )
        )
        
        // GiveItems node properties
        is LogicNode.GiveItems -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ITEMS,
                name = "Items",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "",
                description = "Comma-separated item IDs"
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUANTITIES,
                name = "Quantities",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "1",
                description = "Comma-separated quantities matching items"
            ),
            NodePropertyDefinition(
                key = PropertyKey.YES,
                name = "Accepted",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO,
                name = "Declined",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NOT_ENOUGH,
                name = "Not enough",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT,
                description = "World owner does not have enough of item"
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
