package main.components.nodeeditor

// Import the node data types and parser
import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.DocApi
import main.api.ItemApi
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogInput
import main.components.DialogSelect
import main.components.IconRenderer
import main.components.LabeledCheckbox
import main.components.Translated
import main.components.chat.RichTextEditorDialog
import main.components.inventory.ItemRenderer
import main.components.inventory.SelectItemDialog
import main.components.media.UploadMediaDialog
import main.components.translated
import main.theme.NodeEditorStyles
import models.MediaType
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
import org.jetbrains.compose.web.dom.Audio
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Img
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
                                if (
                                    propDef.key in listOf(
                                        PropertyKey.TEXT_CONTENT,
                                        PropertyKey.SYSTEM_PROMPT,
                                        PropertyKey.USER_PROMPT,
                                        PropertyKey.TEMPLATE,
                                    )
                                ) {
                                    var showRichEditorDialog by remember(nodeId, propDef.key) { mutableStateOf(false) }

                                    // Use DialogInput with multiline for text content
                                    DialogInput(
                                        value = propertyValues[propDef.key] ?: "",
                                        onInput = { newValue ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = newValue
                                            }
                                        },
                                        multiline = true,
                                        placeholder = str("Enter text"),
                                        attrs = {
                                            id("prop-${propDef.key.name.lowercase()}")
                                            attr("rows", "4")
                                        }
                                    )

                                    // Rich editor launcher
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                            padding(0.px)
                                        }
                                    }) {
                                        DialogButton(
                                            onClick = { showRichEditorDialog = true },
                                            style = DialogButtonStyle.Primary
                                        ) { Translated("Rich editor") }
                                    }

                                    if (showRichEditorDialog) {
                                        RichTextEditorDialog(
                                            onClose = { showRichEditorDialog = false },
                                            onSend = { markdown ->
                                                propertyValues = propertyValues.edit { it[propDef.key] = markdown }
                                                showRichEditorDialog = false
                                            },
                                            initialText = propertyValues[propDef.key] ?: "",
                                            confirmButtonText = translated("Save"),
                                            title = translated("Dialogue"),
                                            placeholder = ""
                                        )
                                    }
                                } else if (propDef.key == PropertyKey.ID && (node.type is models.LogicNode.DocContent || node.type is models.LogicNode.CreateDoc || node.type is models.LogicNode.OpenDoc)) {
                                    val currentId = propertyValues[propDef.key] ?: ""
                                    var selectedDoc by remember(nodeId) { mutableStateOf<models.Doc?>(null) }
                                    var loadingDoc by remember(nodeId) { mutableStateOf(false) }
                                    var showSelect by remember(nodeId) { mutableStateOf(false) }

                                    LaunchedEffect(currentId) {
                                        if (currentId.isNotBlank()) {
                                            loadingDoc = true
                                            try {
                                                selectedDoc = DocApi.getDoc(currentId)
                                            } catch (t: Throwable) {
                                                console.error("Failed to load doc by id", t)
                                                selectedDoc = null
                                            } finally {
                                                loadingDoc = false
                                            }
                                        } else {
                                            selectedDoc = null
                                        }
                                    }

                                    // Preview and info
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                            property("align-items", "center")
                                        }
                                    }) {
                                        when {
                                            loadingDoc -> Text(translated("Loading..."))
                                            selectedDoc != null -> Text(selectedDoc!!.title.ifBlank { translated("Selected doc") })
                                            else -> Text(translated("No doc selected"))
                                        }
                                    }

                                    // Actions
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                        }
                                    }) {
                                        DialogButton(
                                            onClick = { showSelect = true },
                                            style = DialogButtonStyle.Primary
                                        ) { Translated(if (currentId.isBlank()) "Select doc" else "Change") }

                                        if (currentId.isNotBlank()) {
                                            DialogButton(
                                                onClick = {
                                                    propertyValues = propertyValues.edit {
                                                        it[PropertyKey.ID] = ""
                                                    }
                                                },
                                                style = DialogButtonStyle.Danger
                                            ) { Translated("Clear") }
                                        }
                                    }

                                    if (showSelect) {
                                        main.components.worlddocs.SelectDocDialog(
                                            worldId = worldId,
                                            onSelect = { d ->
                                                propertyValues = propertyValues.edit { it[PropertyKey.ID] = d.key }
                                                showSelect = false
                                            },
                                            onClose = { showSelect = false },
                                        )
                                    }
                                } else if (propDef.key == PropertyKey.IMAGE_URL) {
                                    val imageUrl = propertyValues[propDef.key] ?: ""
                                    var showDialog by remember(nodeId) { mutableStateOf(false) }

                                    if (imageUrl.isNotBlank()) {
                                        Img(
                                            src = imageUrl,
                                            attrs = {
                                                style {
                                                    property("border-radius", "8px")
                                                    property("overflow", "hidden")
                                                }
                                            }
                                        )
                                    }

                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                        }
                                    }) {
                                        DialogButton(
                                            onClick = { showDialog = true },
                                            style = DialogButtonStyle.Primary
                                        ) { Translated(if (imageUrl.isBlank()) "Choose image" else "Change image") }

                                        if (imageUrl.isNotBlank()) {
                                            DialogButton(
                                                onClick = {
                                                    propertyValues =
                                                        propertyValues.edit { it[PropertyKey.IMAGE_URL] = "" }
                                                },
                                                style = DialogButtonStyle.Danger
                                            ) { Translated("Remove image") }
                                        }
                                    }

                                    if (showDialog) {
                                        UploadMediaDialog(
                                            onClose = { showDialog = false },
                                            onSelect = { selectedUrl, _, _ ->
                                                propertyValues =
                                                    propertyValues.edit { it[PropertyKey.IMAGE_URL] = selectedUrl }
                                            },
                                            filter = MediaType.Image,
                                        )
                                    }
                                } else if (propDef.key == PropertyKey.SOUND_URL) {
                                    val soundUrl = propertyValues[propDef.key] ?: ""
                                    var showDialog by remember(nodeId) { mutableStateOf(false) }

                                    if (soundUrl.isNotBlank()) {
                                        Audio(attrs = {
                                            attr("src", soundUrl)
                                            attr("controls", "true")
                                            style {
                                                width(100.percent)
                                            }
                                        })
                                    }

                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                        }
                                    }) {
                                        DialogButton(
                                            onClick = { showDialog = true },
                                            style = DialogButtonStyle.Primary
                                        ) { Translated(if (soundUrl.isBlank()) "Choose sound" else "Change sound") }

                                        if (soundUrl.isNotBlank()) {
                                            DialogButton(
                                                onClick = {
                                                    propertyValues =
                                                        propertyValues.edit { it[PropertyKey.SOUND_URL] = "" }
                                                },
                                                style = DialogButtonStyle.Danger
                                            ) { Translated("Remove sound") }
                                        }
                                    }

                                    if (showDialog) {
                                        UploadMediaDialog(
                                            onClose = { showDialog = false },
                                            onSelect = { selectedUrl, _, _ ->
                                                propertyValues =
                                                    propertyValues.edit { it[PropertyKey.SOUND_URL] = selectedUrl }
                                            },
                                            filter = MediaType.Audio,
                                        )
                                    }
                                } else if (propDef.key == PropertyKey.ITEM_ID) {
                                    val currentId = propertyValues[propDef.key] ?: ""
                                    var selectedItem by remember(nodeId) { mutableStateOf<models.Item?>(null) }
                                    var loadingItem by remember(nodeId) { mutableStateOf(false) }
                                    var showSelect by remember(nodeId) { mutableStateOf(false) }

                                    LaunchedEffect(currentId) {
                                        if (currentId.isNotBlank()) {
                                            loadingItem = true
                                            try {
                                                selectedItem = ItemApi.getItemById(currentId)
                                            } catch (t: Throwable) {
                                                console.error("Failed to load item by id", t)
                                                selectedItem = null
                                            } finally {
                                                loadingItem = false
                                            }
                                        } else {
                                            selectedItem = null
                                        }
                                    }

                                    // Preview and info
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                            property("align-items", "center")
                                        }
                                    }) {
                                        if (selectedItem != null) {
                                            ItemRenderer(item = selectedItem, size = 32)
                                            val qty = propertyValues[PropertyKey.QUANTITY]?.toIntOrNull() ?: 1
                                            Text(selectedItem!!.title.ifBlank { translated("Selected item") } + "  x" + qty)
                                        } else if (loadingItem) {
                                            Text(translated("Loading..."))
                                        } else {
                                            Text(translated("No item selected"))
                                        }
                                    }

                                    // Actions
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                        }
                                    }) {
                                        DialogButton(
                                            onClick = { showSelect = true },
                                            style = DialogButtonStyle.Primary
                                        ) { Translated("Select item") }

                                        if (currentId.isNotBlank()) {
                                            DialogButton(
                                                onClick = {
                                                    propertyValues = propertyValues.edit {
                                                        it[PropertyKey.ITEM_ID] = ""
                                                    }
                                                },
                                                style = DialogButtonStyle.Danger
                                            ) { Translated("Clear") }
                                        }
                                    }

                                    if (showSelect) {
                                        SelectItemDialog(
                                            initial = if (selectedItem != null) listOf(
                                                selectedItem!! to (propertyValues[PropertyKey.QUANTITY]?.toIntOrNull()
                                                    ?: 1)
                                            ) else emptyList(),
                                            multiple = false,
                                            useQuantities = true,
                                            onConfirm = { list ->
                                                val choice = list.firstOrNull()
                                                propertyValues = propertyValues.edit {
                                                    if (choice != null) {
                                                        it[PropertyKey.ITEM_ID] = choice.first.key
                                                        it[PropertyKey.QUANTITY] = choice.second.toString()
                                                    }
                                                }
                                            },
                                            onClose = { showSelect = false },
                                        )
                                    }
                                } else if (propDef.key == PropertyKey.ITEMS) {
                                    val itemsCsv = propertyValues[PropertyKey.ITEMS] ?: ""
                                    val quantitiesCsv = propertyValues[PropertyKey.QUANTITIES] ?: ""
                                    var previewPairs by remember(nodeId) {
                                        mutableStateOf<List<Pair<models.Item, Int>>>(
                                            emptyList()
                                        )
                                    }
                                    var loadingList by remember(nodeId) { mutableStateOf(false) }
                                    var showSelect by remember(nodeId) { mutableStateOf(false) }

                                    LaunchedEffect(itemsCsv, quantitiesCsv) {
                                        val ids = itemsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
                                        if (ids.isEmpty()) {
                                            previewPairs = emptyList()
                                            return@LaunchedEffect
                                        }
                                        loadingList = true
                                        try {
                                            val qtys = quantitiesCsv.split(',').map { it.trim().toIntOrNull() ?: 1 }
                                            val fetched = ItemApi.getItemsByIds(ids)
                                            val byId = fetched.associateBy { it.key }
                                            val pairs = ids.mapIndexedNotNull { index, id ->
                                                val item = byId[id]
                                                item?.let { it to (qtys.getOrNull(index) ?: 1) }
                                            }
                                            previewPairs = pairs
                                        } catch (t: Throwable) {
                                            console.error("Failed to load items for preview", t)
                                            previewPairs = emptyList()
                                        } finally {
                                            loadingList = false
                                        }
                                    }

                                    // Preview list
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("flex-direction", "column")
                                            property("gap", "6px")
                                        }
                                    }) {
                                        when {
                                            loadingList -> Text(translated("Loading..."))
                                            previewPairs.isEmpty() -> Text(translated("No item selected"))
                                            else -> {
                                                for ((item, qty) in previewPairs) {
                                                    Div({
                                                        style {
                                                            property("display", "flex")
                                                            property("gap", "8px")
                                                            property("align-items", "center")
                                                        }
                                                    }) {
                                                        ItemRenderer(item = item, size = 24)
                                                        Text(item.title.ifBlank { translated("Selected item") } + "  x" + qty)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Actions
                                    Div({
                                        classes(NodeEditorStyles.propertyValue)
                                        style {
                                            property("display", "flex")
                                            property("gap", "8px")
                                        }
                                    }) {
                                        DialogButton(
                                            onClick = { showSelect = true },
                                            style = DialogButtonStyle.Primary
                                        ) { Translated("Select items") }

                                        if (!itemsCsv.isBlank()) {
                                            DialogButton(
                                                onClick = {
                                                    propertyValues = propertyValues.edit {
                                                        it[PropertyKey.ITEMS] = ""
                                                        it[PropertyKey.QUANTITIES] = ""
                                                    }
                                                },
                                                style = DialogButtonStyle.Danger
                                            ) { Translated("Clear") }
                                        }
                                    }

                                    if (showSelect) {
                                        SelectItemDialog(
                                            initial = previewPairs,
                                            multiple = true,
                                            useQuantities = true,
                                            onConfirm = { list ->
                                                val itemsJoined = list.joinToString(",") { it.first.key }
                                                val quantitiesJoined = list.joinToString(",") { it.second.toString() }
                                                propertyValues = propertyValues.edit {
                                                    it[PropertyKey.ITEMS] = itemsJoined
                                                    it[PropertyKey.QUANTITIES] = quantitiesJoined
                                                }
                                            },
                                            onClose = { showSelect = false },
                                        )
                                    }
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
