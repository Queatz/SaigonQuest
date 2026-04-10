package main.components.nodeeditor

// Import the node data types and parser
import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogInput
import main.components.DialogSelect
import main.components.IconRenderer
import main.components.LabeledCheckbox
import main.components.Translated
import main.components.translated
import main.theme.NodeEditorStyles
import models.NodeCategory
import models.nodeeditor.NodeData
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyDirection
import models.nodeeditor.PropertyKey
import models.nodeeditor.PropertyType
import models.nodeeditor.PropertyValue
import models.nodeeditor.getPropertyDefinitions
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text

/**
 * A panel for editing node properties.
 *
 * @param nodeId The ID of the selected node
 * @param node The selected node data
 * @param onNodeUpdate Callback when the node is updated
 */
@Composable
fun PropertyPanel(
    nodeId: String,
    node: NodeData,
    worldId: String,
    onNodeUpdate: (String, NodeData) -> Unit,
) {
    // State for node properties
    var label by remember(nodeId) { mutableStateOf(node.label) }

    // Parse node data
    val nodeData = NodeDataParser.parseNodeData(node.type, node.data)

    // Get property definitions for this node type
    val propertyDefinitions = remember(nodeId) { node.type.getPropertyDefinitions() }

    // Create a map to store property values, initialized from node data
    var propertyValues by remember(nodeId) {
        mutableStateOf(
            propertyDefinitions.filter { it.type != PropertyType.TRIGGER }
                .associate { propDef ->
                    val value = NodeDataParser.getPropertyValue(nodeData, propDef.key)
                    propDef.key to value.ifEmpty { propDef.defaultValue }
                }
        )
    }

    // Auto-update when propertyValues changes
    LaunchedEffect(propertyValues, label) {
        // Create updated property values
        val updatedProperties = propertyValues.map { (key, value) ->
            PropertyValue(key, value)
        }

        // Create updated node data
        val updatedNodeData = PropertyBasedNodeData(properties = updatedProperties)

        // Serialize the node data to a JSON string
        val updatedData = NodeDataParser.serializeNodeData(updatedNodeData)

        // Update the node with new data and outputs
        val updatedNode = updateNodeOutputs(
            node = node.copy(label = label),
            nodeId = nodeId,
            data = updatedData
        )

        // Call the update callback
        onNodeUpdate(nodeId, updatedNode)
    }

    // Property panel UI
    Div(
        attrs = {
            classes(NodeEditorStyles.propertyPanel)
        }
    ) {
        // Panel header
        H4(
            attrs = {
                classes(NodeEditorStyles.propertyPanelHeader)
            }
        ) {
            Text(translated("Node properties"))
        }

        // Node type
        Div(
            attrs = {
                classes(NodeEditorStyles.propertyGroup)
            }
        ) {
            Label(
                attrs = {
                    classes(NodeEditorStyles.propertyLabel)
                }
            ) {
                Text(translated("Type"))
            }

            Div(
                attrs = {
                    classes(NodeEditorStyles.propertyValue)
                }
            ) {
                // Convert category to string and capitalize
                val typeString = when (node.type.category) {
                    NodeCategory.Action -> translated("Action")
                    NodeCategory.Event -> translated("Event")
                    NodeCategory.Dialogue -> translated("Dialogue")
                    NodeCategory.Input -> translated("Input")
                    NodeCategory.Quest -> translated("Quest")
                }
                Text(typeString)
                Text(" • ")
                Text(node.type.label)
            }
        }

        // Node label
        Div(
            attrs = {
                classes(NodeEditorStyles.propertyGroup)
            }
        ) {
            Label(
                attrs = {
                    classes(NodeEditorStyles.propertyLabel)
                    attr("for", "node-label")
                }
            ) {
                Translated("Label")
            }

            DialogInput(
                value = label,
                onInput = { label = it },
                attrs = {
                    id("node-label")
                }
            )
        }

        // Render editable properties (excluding trigger properties which are handled by ports)
        propertyDefinitions
            .filter { it.type != PropertyType.TRIGGER }
            .filter { it.key !in listOf(PropertyKey.QUANTITIES, PropertyKey.QUANTITY) }
            .forEach { propDef ->
                // Only render input properties in the property panel
                if (propDef.direction == PropertyDirection.INPUT) {
                    Div(
                        attrs = {
                            classes(NodeEditorStyles.propertyGroup)
                        }
                    ) {
                        if (propDef.type != PropertyType.BOOLEAN) {
                            Label(
                                attrs = {
                                    classes(NodeEditorStyles.propertyLabel)
                                    attr("for", "prop-${propDef.key.name.lowercase()}")
                                }
                            ) {
                                Text(propDef.name)
                            }
                        }

                        when (propDef.type) {
                            PropertyType.STRING -> {
                                if (propDef.key == PropertyKey.CONFIG_JSON && node.type.id == models.NodeId.Minigame) {
                                    val typeStr = propertyValues[PropertyKey.MINIGAME_TYPE] ?: "Flashcards"
                                    val minigameType = try {
                                        models.MinigameType.valueOf(typeStr)
                                    } catch (e: Throwable) {
                                        models.MinigameType.Flashcards
                                    }

                                    MinigameConfigEditor(
                                        type = minigameType,
                                        configJson = propertyValues[propDef.key] ?: "[]",
                                        onConfigChange = { newValue ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = newValue }
                                        }
                                    )
                                } else if (
                                    propDef.key in listOf(
                                        PropertyKey.TEXT_CONTENT,
                                        PropertyKey.SYSTEM_PROMPT,
                                        PropertyKey.USER_PROMPT,
                                        PropertyKey.TEMPLATE,
                                    )
                                ) {
                                    RichTextPropertyEditor(
                                        value = propertyValues[propDef.key] ?: "",
                                        onUpdate = { newValue ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = newValue }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.ID && (node.type is models.LogicNode.DocContent || node.type is models.LogicNode.CreateDoc || node.type is models.LogicNode.OpenDoc)) {
                                    DocPropertyEditor(
                                        worldId = worldId,
                                        currentId = propertyValues[propDef.key] ?: "",
                                        onUpdate = { newValue ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = newValue }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.IMAGE_URL) {
                                    MediaSelector(
                                        url = propertyValues[propDef.key] ?: "",
                                        type = models.MediaType.Image,
                                        onSelect = { url ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = url }
                                        },
                                        onClear = {
                                            propertyValues = propertyValues.edit { it[propDef.key] = "" }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.SOUND_URL) {
                                    MediaSelector(
                                        url = propertyValues[propDef.key] ?: "",
                                        type = models.MediaType.Audio,
                                        onSelect = { url ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = url }
                                        },
                                        onClear = {
                                            propertyValues = propertyValues.edit { it[propDef.key] = "" }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.ITEM_ID) {
                                    ItemPropertyEditor(
                                        currentId = propertyValues[propDef.key] ?: "",
                                        quantity = propertyValues[PropertyKey.QUANTITY]?.toIntOrNull() ?: 1,
                                        onUpdate = { id, qty ->
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.ITEM_ID] = id
                                                it[PropertyKey.QUANTITY] = qty.toString()
                                            }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.ITEMS) {
                                    ItemListPropertyEditor(
                                        itemsCsv = propertyValues[PropertyKey.ITEMS] ?: "",
                                        quantitiesCsv = propertyValues[PropertyKey.QUANTITIES] ?: "",
                                        onUpdate = { items, qtys ->
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.ITEMS] = items
                                                it[PropertyKey.QUANTITIES] = qtys
                                            }
                                        }
                                    )
                                } else {
                                    // Use Input for other string properties
                                    DialogInput(
                                        value = propertyValues[propDef.key] ?: "",
                                        onInput = { newValue ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = newValue
                                            }
                                        },
                                        placeholder = propDef.defaultValue,
                                        attrs = {
                                            id("prop-${propDef.key.name.lowercase()}")
                                        }
                                    )
                                }
                            }

                            PropertyType.NUMBER -> {
                                DialogInput(
                                    value = propertyValues[propDef.key] ?: "0",
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                        }
                                    },
                                    type = InputType.Number,
                                    attrs = {
                                        id("prop-${propDef.key.name.lowercase()}")
                                    }
                                )
                            }

                            PropertyType.BOOLEAN -> {
                                LabeledCheckbox(
                                    checked = (propertyValues[propDef.key] ?: "false") == "true",
                                    enabled = true,
                                    label = propDef.name,
                                    onCheckedChange = { isChecked ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = if (isChecked) "true" else "false"
                                        }
                                    },
                                )
                            }

                            PropertyType.VECTOR3 -> {
                                DialogInput(
                                    value = propertyValues[propDef.key] ?: "0,0,0",
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                        }
                                    },
                                    placeholder = str("x,y,z"),
                                    attrs = {
                                        id("prop-${propDef.key.name.lowercase()}")
                                    }
                                )
                            }

                            PropertyType.COLOR -> {
                                Input(
                                    type = InputType.Color,
                                    attrs = {
                                        classes(NodeEditorStyles.propertyInput)
                                        id("prop-${propDef.key.name.lowercase()}")
                                        value(propertyValues[propDef.key] ?: "#000000")
                                        onInput { event ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = event.value
                                            }
                                        }
                                    }
                                )
                            }

                            PropertyType.ENUM -> {
                                DialogSelect(
                                    options = propDef.options.map { it to it },
                                    value = propertyValues[propDef.key] ?: propDef.defaultValue,
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                        }
                                    },
                                    label = null
                                )
                            }

                            PropertyType.QUEST -> {
                                QuestPropertySelector(
                                    value = propertyValues[propDef.key] ?: "",
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                            // Reset state ID when quest changes
                                            it[PropertyKey.QUEST_STATE_ID] = ""
                                        }
                                    }
                                )
                            }

                            PropertyType.QUEST_STATE -> {
                                val questId = propertyValues[PropertyKey.QUEST_ID] ?: ""
                                QuestStatePropertySelector(
                                    questId = questId,
                                    value = propertyValues[propDef.key] ?: "",
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                        }
                                    }
                                )
                            }

                            else -> {
                                // Default to text input for other types
                                DialogInput(
                                    value = propertyValues[propDef.key] ?: "",
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                        }
                                    },
                                    attrs = {
                                        id("prop-${propDef.key.name.lowercase()}")
                                    }
                                )
                            }
                        }
                    }
                }
            }

        // For dialogue nodes, add button management UI
        if (node.type.id == models.NodeId.Dialogue) {
            // Get current buttons or default to "OK"
            val buttonsValue = propertyValues[PropertyKey.BUTTONS] ?: str("Ok")
            val buttons = buttonsValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            // State for new button text
            var newButtonText by remember(nodeId) { mutableStateOf("") }

            Div(
                attrs = {
                    classes(NodeEditorStyles.propertyGroup)
                }
            ) {
                // List of current buttons
                Div(
                    attrs = {
                        classes(NodeEditorStyles.buttonList)
                    }
                ) {
                    buttons.forEachIndexed { index, buttonText ->
                        Div(
                            attrs = {
                                classes(NodeEditorStyles.buttonItem)
                            }
                        ) {
                            Text(buttonText)

                            // Only show remove button if there's more than one button
                            if (buttons.size > 1) {
                                DialogButton(
                                    onClick = {
                                        // Remove this button
                                        val updatedButtons = buttons.toMutableList()
                                        updatedButtons.removeAt(index)
                                        propertyValues = propertyValues.edit {
                                            it[PropertyKey.BUTTONS] = updatedButtons.joinToString(",")
                                        }
                                    },
                                    style = DialogButtonStyle.Danger
                                ) {
                                    IconRenderer(
                                        url = "raw/data/ui/ui.png",
                                        index = 20,
                                        size = 24,
                                    )
                                }
                            }
                        }
                    }
                }

                // Add new button UI
                Div(
                    attrs = {
                        classes(NodeEditorStyles.addButtonContainer)
                    }
                ) {
                    DialogInput(
                        value = newButtonText,
                        onInput = { newButtonText = it },
                        placeholder = str("New button text"),
                    )

                    DialogButton(
                        onClick = {
                            if (newButtonText.isNotEmpty()) {
                                // Add new button
                                val updatedButtons = buttons.toMutableList()
                                updatedButtons.add(newButtonText)
                                propertyValues = propertyValues.edit {
                                    it[PropertyKey.BUTTONS] = updatedButtons.joinToString(",")
                                }
                                newButtonText = ""
                            }
                        },
                        style = DialogButtonStyle.Primary
                    ) {
                        Translated("Add")
                    }
                }
            }
        }
    }
}

fun <K, V> Map<K, V>.edit(block: (MutableMap<K, V>) -> Unit): Map<K, V> =
    toMutableMap().apply(block).toMap()
