package models.nodeeditor

import kotlinx.serialization.Serializable
import models.LogicNode
import models.MinigameType

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
    TEXT,
    SHOUT,

    // Dialogue properties
    BUTTONS,
    IMAGE_URL,
    TITLE,
    PLACEHOLDER,
    CONFIRM_LABEL,

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
    NOT_STARTED,
    IN_PROGRESS,

    // TakeItems specific output when player lacks items
    NOT_ENOUGH,

    // TakeItems properties
    ITEMS,
    QUANTITIES,
    
    // OpenUrl properties
    URL,

    // Delay properties
    DELAY_SECONDS,

    // UserAge properties
    MIN_AGE,
    MAX_AGE,

    // StringTemplate properties
    TEMPLATE,

    // AI properties
    USER_PROMPT,
    SYSTEM_PROMPT,
    IMAGE_SIZE,

    // Dialogue Word Click properties
    WORD_CLICKED,
    CLICKED_WORD,

    // PlaySound properties
    SOUND_URL,
    PLAY_FOR_EVERYONE,

    // PlayerImageInput properties
    ALLOW_MULTIPLE,
    IMAGE_MARKDOWN,
    NO_IMAGES,

    // PlayerAudioInput properties
    AUDIO_URL,
    AUDIO_MARKDOWN,
    NO_AUDIO,

    // JoinList properties
    LIST_CONTENT,
    ITEM_TEMPLATE,
    ITEM_SEPARATOR,

    // Quest properties
    QUEST_ID,
    QUEST_STATE_ID,
    SKILL_ID,
    STEP_INDEX,
    COMPLETED,
    INCOMPLETE,
    VALUE,

    // Logic/Math properties
    OPERATOR,
    MATH_OPERATOR,
    A,
    B,
    RESULT,

    // JsonExtract properties
    JSON,
    KEY,

    // Minigame properties
    MINIGAME_TYPE,
    CONFIG_JSON,
    TIMED,
    REFLEX,
    ACCURACY,
    INITIAL_MESSAGE,

    // SetAsset properties
    FILE,
    COLLECTION,

    // MoveObject properties
    ANIMATE,
    SAVED_POSITION_ID,

    // ChangeWorldMusic properties
    MUSIC_URL,
    MUSIC_NAME,
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
    TRIGGER, // Special type for execution flow
    QUEST,
    QUEST_STATE,
    QUEST_STEP,
    SKILL,
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
    val description: String = "",
    val options: List<String> = emptyList()
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

        // PlayerNearby node properties
        is LogicNode.PlayerNearby -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // VisitorNearby node properties
        is LogicNode.VisitorNearby -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // PlayerJoined node properties
        is LogicNode.PlayerJoined -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // VisitorJoined node properties
        is LogicNode.VisitorJoined -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // PlayerFar node properties
        is LogicNode.PlayerFar -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // VisitorFar node properties
        is LogicNode.VisitorFar -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // PlayerLeave node properties
        is LogicNode.PlayerLeave -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // VisitorLeave node properties
        is LogicNode.VisitorLeave -> listOf(
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
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
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
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Text Content",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.WORD_CLICKED,
                name = "On Word Click",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.CLICKED_WORD,
                name = "Clicked Word",
                type = PropertyType.STRING,
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
        
        // UserSex node properties
        is LogicNode.UserSex -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Boy",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Girl",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        
        // UserAge node properties
        is LogicNode.UserAge -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.MIN_AGE,
                name = "Min age",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.MAX_AGE,
                name = "Max age",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
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

        // IsObjectOwner node properties
        is LogicNode.IsObjectOwner -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.YES,
                name = "Is owner",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO,
                name = "Is not owner",
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

        // SendChat node properties
        is LogicNode.SendChat -> listOf(
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
            ),
            NodePropertyDefinition(
                key = PropertyKey.SHOUT,
                name = "Shout",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "false"
            )
        )

        // SetStatus node properties
        is LogicNode.SetStatus -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Status",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // CreateDoc node properties
        is LogicNode.CreateDoc -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ID,
                name = "Parent doc",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "",
                description = "Optional parent doc id"
            ),
            NodePropertyDefinition(
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ID,
                name = "Doc ID",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // PlayerInput node properties
        is LogicNode.PlayerInput -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.PLACEHOLDER,
                name = "Placeholder",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.CONFIRM_LABEL,
                name = "Confirm label",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "OK"
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // StringTemplate node properties
        is LogicNode.StringTemplate -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TEMPLATE,
                name = "Template",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // PlayerName node properties
        is LogicNode.PlayerName -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.NAME,
                name = "Name",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // DocContent node properties (pure value, no triggers)
        is LogicNode.DocContent -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.ID,
                name = "Doc",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // OpenDoc node properties
        is LogicNode.OpenDoc -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ID,
                name = "Doc",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // AI node properties (pure value, no triggers)
        is LogicNode.Ai -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.SYSTEM_PROMPT,
                name = "System prompt",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.USER_PROMPT,
                name = "User prompt",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
        )

        // AiChat node properties
        is LogicNode.AiChat -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.SYSTEM_PROMPT,
                name = "System prompt",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.USER_PROMPT,
                name = "Initial user message",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.INITIAL_MESSAGE,
                name = "Initial message",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            )
        )

        // PlaySound node properties
        is LogicNode.PlaySound -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.SOUND_URL,
                name = "Sound",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.PLAY_FOR_EVERYONE,
                name = "Play for everyone",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "true"
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // PlayerImageInput node properties
        is LogicNode.PlayerImageInput -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Description",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.ALLOW_MULTIPLE,
                name = "Allow multiple",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "false"
            ),
            NodePropertyDefinition(
                key = PropertyKey.CONFIRM_LABEL,
                name = "Confirm label",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "Confirm"
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO_IMAGES,
                name = "No images",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.IMAGE_URL,
                name = "Image",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.IMAGE_MARKDOWN,
                name = "Images (Markdown)",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )

        // PlayerAudioInput node properties
        is LogicNode.PlayerAudioInput -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Description",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.ALLOW_MULTIPLE,
                name = "Allow multiple",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "false"
            ),
            NodePropertyDefinition(
                key = PropertyKey.CONFIRM_LABEL,
                name = "Confirm label",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "Confirm"
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO_AUDIO,
                name = "No audio",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.AUDIO_URL,
                name = "Audio",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.AUDIO_MARKDOWN,
                name = "Audio (Markdown)",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )

        // JoinList node properties
        is LogicNode.JoinList -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.LIST_CONTENT,
                name = "List",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.ITEM_TEMPLATE,
                name = "Template",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "{item}"
            ),
            NodePropertyDefinition(
                key = PropertyKey.ITEM_SEPARATOR,
                name = "Separator",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ", "
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )

        // QuestState node properties
        is LogicNode.QuestState -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_STATE_ID,
                name = "State",
                type = PropertyType.QUEST_STATE,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.VALUE,
                name = "Value",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )
        // PlayerQuestState node properties
        is LogicNode.PlayerQuestState -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_STATE_ID,
                name = "State",
                type = PropertyType.QUEST_STATE,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.VALUE,
                name = "Value",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )
        // UpdateQuestState node properties
        is LogicNode.UpdateQuestState -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_STATE_ID,
                name = "State",
                type = PropertyType.QUEST_STATE,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.VALUE,
                name = "Value",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        // UpdatePlayerQuestState node properties
        is LogicNode.UpdatePlayerQuestState -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_STATE_ID,
                name = "State",
                type = PropertyType.QUEST_STATE,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.VALUE,
                name = "Value",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )
        // CompareValue node properties
        is LogicNode.CompareValue -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.A,
                name = "A",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.B,
                name = "B",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.OPERATOR,
                name = "Operator",
                type = PropertyType.ENUM,
                direction = PropertyDirection.INPUT,
                defaultValue = "Equals",
                options = listOf("Equals", "Greater than", "Less than")
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
        // Math node properties
        is LogicNode.Math -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.A,
                name = "A",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.B,
                name = "B",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.MATH_OPERATOR,
                name = "Operator",
                type = PropertyType.ENUM,
                direction = PropertyDirection.INPUT,
                defaultValue = "Add",
                options = listOf("Add", "Subtract", "Multiply", "Divide")
            ),
            NodePropertyDefinition(
                key = PropertyKey.RESULT,
                name = "Result",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // AiImage node properties
        is LogicNode.AiImage -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.USER_PROMPT,
                name = "Prompt",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.IMAGE_SIZE,
                name = "Size",
                type = PropertyType.ENUM,
                direction = PropertyDirection.INPUT,
                defaultValue = "1024*1024",
                options = listOf("1024*1024", "720*1280", "1280*720", "768*1024", "1024*768")
            ),
            NodePropertyDefinition(
                key = PropertyKey.IMAGE_URL,
                name = "URL",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.IMAGE_MARKDOWN,
                name = "Markdown",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )

        // AiTts node properties
        is LogicNode.AiTts -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.AUDIO_URL,
                name = "URL",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.AUDIO_MARKDOWN,
                name = "Markdown",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )

        // Minigame node properties
        is LogicNode.Minigame -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.MINIGAME_TYPE,
                name = "Type",
                type = PropertyType.ENUM,
                direction = PropertyDirection.INPUT,
                defaultValue = "Flashcards",
                options = MinigameType.entries.map { it.name }
            ),
            NodePropertyDefinition(
                key = PropertyKey.TITLE,
                name = "Title",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "Minigame"
            ),
            NodePropertyDefinition(
                key = PropertyKey.TIMED,
                name = "Timed",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "true"
            ),
            NodePropertyDefinition(
                key = PropertyKey.CONFIG_JSON,
                name = "Configure",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "[]"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.REFLEX,
                name = "Reflex (s)",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.ACCURACY,
                name = "Accuracy",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // JsonExtract node properties
        is LogicNode.JsonExtract -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.JSON,
                name = "JSON",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.KEY,
                name = "Key",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.VALUE,
                name = "Value",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT
            )
        )

        // UpdatePlayerQuestStep node properties
        is LogicNode.UpdatePlayerQuestStep -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.STEP_INDEX,
                name = "Step",
                type = PropertyType.QUEST_STEP,
                direction = PropertyDirection.INPUT,
                defaultValue = "0"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETED,
                name = "Completed",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "false"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // StartQuest node properties
        is LogicNode.StartQuest -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // QuestPlayerStatus node properties
        is LogicNode.QuestPlayerStatus -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NOT_STARTED,
                name = "Not started",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.IN_PROGRESS,
                name = "In progress",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // QuestStep node properties
        is LogicNode.QuestStep -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUEST_ID,
                name = "Quest",
                type = PropertyType.QUEST,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.STEP_INDEX,
                name = "Step",
                type = PropertyType.QUEST_STEP,
                direction = PropertyDirection.INPUT,
                defaultValue = "0"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Completed",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.INCOMPLETE,
                name = "Incomplete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // LocateObject node properties
        is LogicNode.LocateObject -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.TARGET_WORLD_ID,
                name = "World ID",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.ID,
                name = "Object ID",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.NEXT,
                name = "Next",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // SetAsset node properties
        is LogicNode.SetAsset -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.FILE,
                name = "File",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.COLLECTION,
                name = "Collection",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // IncreasePlayerSkill node properties
        is LogicNode.IncreasePlayerSkill -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "In",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.SKILL_ID,
                name = "Skill",
                type = PropertyType.SKILL,
                direction = PropertyDirection.INPUT,
                description = "Skill to increase"
            ),
            NodePropertyDefinition(
                key = PropertyKey.QUANTITY,
                name = "Amount",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT,
                defaultValue = "1"
            ),
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Out",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // CheckPlayerSkill node properties
        is LogicNode.CheckPlayerSkill -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "In",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.SKILL_ID,
                name = "Skill",
                type = PropertyType.SKILL,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.OPERATOR,
                name = "Operator",
                type = PropertyType.ENUM,
                direction = PropertyDirection.INPUT,
                options = listOf("At least", "At most", "Equals")
            ),
            NodePropertyDefinition(
                key = PropertyKey.VALUE,
                name = "Value",
                type = PropertyType.NUMBER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.YES,
                name = "Meets condition",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.NO,
                name = "Doesn't meet",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // MoveObject node properties
        is LogicNode.MoveObject -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "In",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.SAVED_POSITION_ID,
                name = "Saved position",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                description = "Choose a saved position"
            ),
            NodePropertyDefinition(
                key = PropertyKey.TARGET_POSITION,
                name = "Position",
                type = PropertyType.VECTOR3,
                direction = PropertyDirection.INPUT,
                description = "New position for the object (fallback if no saved position selected)"
            ),
            NodePropertyDefinition(
                key = PropertyKey.ANIMATE,
                name = "Animate",
                type = PropertyType.BOOLEAN,
                direction = PropertyDirection.INPUT,
                defaultValue = "true",
                description = "Whether to animate the movement"
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Out",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // TranscribeAudio node properties (pure value, no triggers)
        is LogicNode.TranscribeAudio -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.AUDIO_URL,
                name = "Audio URL",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                description = "The URL of the audio file to transcribe"
            ),
            NodePropertyDefinition(
                key = PropertyKey.RESULT,
                name = "Result",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT,
                description = "The transcribed text"
            )
        )

        // ChangeWorldMusic node properties
        is LogicNode.ChangeWorldMusic -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TRIGGER,
                name = "Trigger",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.INPUT
            ),
            NodePropertyDefinition(
                key = PropertyKey.MUSIC_URL,
                name = "Music",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.MUSIC_NAME,
                name = "Music Name",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = ""
            ),
            NodePropertyDefinition(
                key = PropertyKey.COMPLETE,
                name = "Complete",
                type = PropertyType.TRIGGER,
                direction = PropertyDirection.OUTPUT
            )
        )

        // Plain text node properties (pure value, no triggers)
        is LogicNode.PlainText -> listOf(
            NodePropertyDefinition(
                key = PropertyKey.TEXT_CONTENT,
                name = "Text",
                type = PropertyType.STRING,
                direction = PropertyDirection.INPUT,
                defaultValue = "",
                description = "The text content"
            ),
            NodePropertyDefinition(
                key = PropertyKey.TEXT,
                name = "Output",
                type = PropertyType.STRING,
                direction = PropertyDirection.OUTPUT,
                description = "The text content"
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
