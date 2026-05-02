package main.components.nodeeditor

// Import the node data types and parser
import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.ObjectApi
import main.api.SavedPositionApi
import main.api.WorldApi
import main.components.*
import main.theme.NodeEditorStyles
import models.AssetType
import models.LogicNode
import models.NodeCategory
import models.SavedPosition
import models.nodeeditor.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
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
            .filter { it.key !in listOf(PropertyKey.QUANTITIES, PropertyKey.QUANTITY, PropertyKey.COLLECTION) }
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
                                Text(translated(propDef.name))
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
                                } else if (propDef.key == PropertyKey.SAVED_POSITION_ID) {
                                    val editorWorldId = propertyValues[PropertyKey.TARGET_WORLD_ID]?.takeIf { it.isNotEmpty() } ?: worldId
                                    SavedPositionPropertyEditor(
                                        worldId = editorWorldId,
                                        currentId = propertyValues[propDef.key] ?: "",
                                        onUpdate = { selectedId, posStr ->
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.SAVED_POSITION_ID] = selectedId
                                                if (posStr.isNotEmpty()) {
                                                    it[PropertyKey.TARGET_POSITION] = posStr
                                                }
                                            }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.TARGET_WORLD_ID) {
                                    WorldPropertyEditor(
                                        currentId = propertyValues[propDef.key] ?: "",
                                        preselectWorldId = worldId,
                                        onUpdate = { newValue ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = newValue }
                                        }
                                    )
                                    
                                    // Add manual entry for Target World URL
                                    DialogInput(
                                        value = propertyValues[propDef.key] ?: "",
                                        onInput = { newValue ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = newValue }
                                        },
                                        placeholder = str("Manual World URL / ID"),
                                        attrs = {
                                            id("prop-${propDef.key.name.lowercase()}-manual")
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.ID && node.type is models.LogicNode.LocateObject) {
                                    ObjectPropertyEditor(
                                        worldId = propertyValues[PropertyKey.TARGET_WORLD_ID] ?: "",
                                        currentId = propertyValues[propDef.key] ?: "",
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
                                        onSelect = { url, _ ->
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
                                        onSelect = { url, _ ->
                                            propertyValues = propertyValues.edit { it[propDef.key] = url }
                                        },
                                        onClear = {
                                            propertyValues = propertyValues.edit { it[propDef.key] = "" }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.MUSIC_URL) {
                                    MediaSelector(
                                        url = propertyValues[propDef.key] ?: "",
                                        type = models.MediaType.Audio,
                                        onSelect = { url, name ->
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.MUSIC_URL] = url
                                                it[PropertyKey.MUSIC_NAME] = name
                                            }
                                        },
                                        onClear = {
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.MUSIC_URL] = ""
                                                it[PropertyKey.MUSIC_NAME] = ""
                                            }
                                        }
                                    )
                                } else if (propDef.key == PropertyKey.MUSIC_NAME) {
                                    // Hidden or read-only if desired, but let's allow manual edit too
                                    DialogInput(
                                        value = propertyValues[propDef.key] ?: "",
                                        onInput = { newValue ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = newValue
                                            }
                                        },
                                        placeholder = propDef.defaultValue,
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
                                } else if (propDef.key == PropertyKey.ITEMS || (propDef.options.contains("ITEM_LIST") && propDef.key != PropertyKey.QUANTITIES)) {
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
                                } else if (propDef.key == PropertyKey.FILE) {
                                    AssetPropertyEditor(
                                        file = propertyValues[PropertyKey.FILE] ?: "",
                                        collection = propertyValues[PropertyKey.COLLECTION] ?: "",
                                        onUpdate = { newFile, newCollection ->
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.FILE] = newFile
                                                it[PropertyKey.COLLECTION] = newCollection
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
                                    label = translated(propDef.name),
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

                            PropertyType.QUEST_STEP -> {
                                val questId = propertyValues[PropertyKey.QUEST_ID] ?: ""
                                QuestStepPropertySelector(
                                    questId = questId,
                                    value = propertyValues[propDef.key] ?: "0",
                                    onInput = { newValue ->
                                        propertyValues = propertyValues.edit {
                                            it[propDef.key] = newValue
                                        }
                                    }
                                )
                            }

                            PropertyType.SKILL -> {
                                SkillSelector(
                                    currentId = propertyValues[propDef.key] ?: "",
                                    onUpdate = { newValue ->
                                        propertyValues = propertyValues.edit { it[propDef.key] = newValue }
                                    },
                                    label = null
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

@Composable
fun WorldPropertyEditor(
    currentId: String,
    preselectWorldId: String?,
    onUpdate: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var title by remember(currentId) { mutableStateOf(currentId) }

    LaunchedEffect(currentId) {
        if (currentId.isNotEmpty()) {
            try {
                title = WorldApi.getWorld(currentId).title
            } catch (e: Throwable) {
                // Ignore, keep ID as fallback
            }
        } else {
            title = ""
        }
    }

    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            gap(8.px)
            marginBottom(4.px)
        }
    }) {
        Span(attrs = {
            style {
                flexGrow(1)
                fontSize(0.9.em)
                opacity(0.8)
                overflow("hidden")
                property("text-overflow", "ellipsis")
                whiteSpace("nowrap")
            }
        }) {
            Text(if (title.isEmpty()) str("None") else title)
        }

        DialogButton(
            onClick = { showDialog = true },
            style = DialogButtonStyle.Secondary,
            small = true
        ) {
            Translated("Select")
        }
    }

    if (showDialog) {
        SelectWorldDialog(
            onClose = { showDialog = false },
            preselectWorldId = preselectWorldId,
            onSelect = { world ->
                onUpdate(world.key)
                showDialog = false
            }
        )
    }
}

@Composable
fun ObjectPropertyEditor(
    worldId: String,
    currentId: String,
    onUpdate: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var title by remember(currentId) { mutableStateOf(currentId) }

    LaunchedEffect(currentId, worldId) {
        if (currentId.isNotEmpty() && worldId.isNotEmpty()) {
            try {
                val obj = ObjectApi.getObject(currentId)
                title = obj.title
            } catch (e: Throwable) {
                // Ignore, keep ID as fallback
            }
        } else {
            title = ""
        }
    }

    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            gap(8.px)
            marginBottom(4.px)
        }
    }) {
        Span(attrs = {
            style {
                flexGrow(1)
                fontSize(0.9.em)
                opacity(0.8)
                overflow("hidden")
                property("text-overflow", "ellipsis")
                whiteSpace("nowrap")
            }
        }) {
            Text(if (title.isEmpty()) str("None") else title)
        }

        DialogButton(
            onClick = { showDialog = true },
            style = DialogButtonStyle.Secondary,
            small = true,
            isDisabled = worldId.isBlank()
        ) {
            Translated("Select")
        }
    }

    if (showDialog) {
        SelectObjectDialog(
            worldId = worldId,
            onClose = { showDialog = false },
            onSelect = { obj ->
                onUpdate(obj.key)
                showDialog = false
            }
        )
    }
}

@Composable
fun AssetPropertyEditor(
    file: String,
    collection: String,
    onUpdate: (file: String, collection: String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(8.px)
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                gap(8.px)
            }
        }) {
            Span(attrs = {
                style {
                    flexGrow(1)
                    fontSize(0.9.em)
                    opacity(0.8)
                    overflow("hidden")
                    property("text-overflow", "ellipsis")
                    whiteSpace("nowrap")
                }
            }) {
                Text(if (file.isEmpty()) str("None selected") else "$collection / $file")
            }

            DialogButton(
                onClick = { showDialog = true },
                style = DialogButtonStyle.Secondary,
                small = true
            ) {
                Translated("Browse")
            }
        }

        if (file.isNotEmpty() && collection.isNotEmpty()) {
            Div(attrs = {
                style {
                    alignSelf(AlignSelf.Center)
                    width(100.percent)
                    height(200.px)
                }
            }) {
                BasicSceneRenderer(
                    filename = file,
                    collection = collection,
                    transparentBackground = true,
                )
            }
        }
    }

    if (showDialog) {
        AssetBrowserDialog(
            type = AssetType.Objects,
            onSelect = { r ->
                onUpdate(r.asset!!.file, r.collection ?: "")
                showDialog = false
            },
            onClose = { showDialog = false },
        )
    }
}

@Composable
fun SavedPositionPropertyEditor(
    worldId: String,
    currentId: String,
    onUpdate: (String, String) -> Unit
) {
    var savedPositions by remember { mutableStateOf<List<SavedPosition>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(worldId) {
        if (worldId.isNotEmpty()) {
            try {
                val fetched = SavedPositionApi.getByWorldId(worldId)
                savedPositions = fetched
                
                // If we have a selected position, ensure the x,y,z is synced on load
                if (currentId.isNotEmpty()) {
                    val pos = fetched.find { it.key == currentId }
                    if (pos != null) {
                        onUpdate(currentId, "${pos.position.x},${pos.position.y},${pos.position.z}")
                    }
                }
            } catch (e: Throwable) {
                console.error("Failed to fetch saved positions for editor", e)
            } finally {
                isLoading = false
            }
        }
    }

    val options = listOf("" to "None") + savedPositions.map { it.key to it.name }

    DialogSelect(
        options = options,
        value = currentId,
        onInput = { selectedId ->
            val pos = savedPositions.find { it.key == selectedId }
            if (pos != null) {
                onUpdate(selectedId, "${pos.position.x},${pos.position.y},${pos.position.z}")
            } else {
                onUpdate(selectedId, "")
            }
        },
        label = null
    )
}
