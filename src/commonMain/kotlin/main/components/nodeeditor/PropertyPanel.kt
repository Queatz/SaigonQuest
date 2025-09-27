package main.components.nodeeditor

// Import the node data types and parser
import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.ItemApi
import main.components.Translated
import main.components.inventory.ItemRenderer
import main.components.inventory.SelectItemDialog
import main.components.media.UploadMediaDialog
import main.components.translated
import main.theme.NodeEditorStyles
import main.theme.Styles
import models.NodeCategory
import models.nodeeditor.NodeData
import models.nodeeditor.PropertyDirection
import models.nodeeditor.PropertyKey
import models.nodeeditor.PropertyType
import models.nodeeditor.PropertyValue
import models.nodeeditor.getPropertyDefinitions
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.TextInput

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
    onNodeUpdate: (String, NodeData) -> Unit
) {
    // State for node properties
    var label by remember(nodeId) { mutableStateOf(node.label) }
    
    // Parse node data
    val nodeData = NodeDataParser.parseNodeData(node.type, node.data)
    
    // Get property definitions for this node type
    val propertyDefinitions = remember(nodeId) { node.type.getPropertyDefinitions() }
    
    // Create a map to store property values
    var propertyValues by remember(nodeId) { mutableStateOf(mapOf<PropertyKey, String>()) }
    
    // Initialize property values from node data
    LaunchedEffect(nodeId, propertyDefinitions) {
        propertyDefinitions.forEach { propDef ->
            if (propDef.type != PropertyType.TRIGGER) {
                val value = NodeDataParser.getPropertyValue(nodeData, propDef.key)
                propertyValues = propertyValues.edit {
                    it[propDef.key] = value.ifEmpty { propDef.defaultValue }
                }
            }
        }
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
                val typeString = when(node.type.category) {
                    NodeCategory.Action -> translated("Action")
                    NodeCategory.Event -> translated("Event")
                    NodeCategory.Dialogue -> translated("Dialogue")
                    NodeCategory.Input -> translated("Input")
                }
                Text(typeString)
                Text(" • " )
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
            
            TextInput(
                value = label,
                attrs = {
                    classes(NodeEditorStyles.propertyInput)
                    id("node-label")
                    onInput { event -> label = event.value }
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
                        Label(
                            attrs = {
                                classes(NodeEditorStyles.propertyLabel)
                                attr("for", "prop-${propDef.key.name.lowercase()}")
                            }
                        ) {
                            Text(propDef.name)
                        }
                        
                        when (propDef.type) {
                            PropertyType.STRING -> {
                                if (propDef.key == PropertyKey.TEXT_CONTENT) {
                                    // Use TextArea for text content
                                    TextArea(
                                        value = propertyValues[propDef.key] ?: "",
                                        attrs = {
                                            classes(NodeEditorStyles.propertyTextArea)
                                            id("prop-${propDef.key.name.lowercase()}")
                                            onInput { event ->
                                                propertyValues = propertyValues.edit {
                                                    it[propDef.key] = event.value
                                                }
                                            }
                                            attr("rows", "4")
                                            placeholder(str("Enter text"))
                                        }
                                    )
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
                                        Button(attrs = {
                                            classes(NodeEditorStyles.addButton, Styles.primaryButton)
                                            onClick { showDialog = true }
                                        }) { Translated(if (imageUrl.isBlank()) "Choose image" else "Change image") }

                                        if (imageUrl.isNotBlank()) {
                                            Button(attrs = {
                                                classes(NodeEditorStyles.removeButton, Styles.primaryButton)
                                                onClick {
                                                    propertyValues = propertyValues.edit { it[PropertyKey.IMAGE_URL] = "" }
                                                }
                                            }) { Translated("Remove image") }
                                        }
                                    }

                                    if (showDialog) {
                                        UploadMediaDialog(
                                            onClose = { showDialog = false },
                                            onSelect = { selectedUrl ->
                                                propertyValues = propertyValues.edit { it[PropertyKey.IMAGE_URL] = selectedUrl }
                                            }
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
                                        Button(attrs = {
                                            classes(NodeEditorStyles.addButton, Styles.primaryButton)
                                            onClick { showSelect = true }
                                        }) { Translated("Select item") }

                                        if (currentId.isNotBlank()) {
                                            Button(attrs = {
                                                classes(NodeEditorStyles.removeButton, Styles.primaryButton)
                                                onClick {
                                                    propertyValues = propertyValues.edit {
                                                        it[PropertyKey.ITEM_ID] = ""
                                                    }
                                                }
                                            }) { Translated("Clear") }
                                        }
                                    }

                                    if (showSelect) {
                                        SelectItemDialog(
                                            initial = if (selectedItem != null) listOf(selectedItem!! to (propertyValues[PropertyKey.QUANTITY]?.toIntOrNull() ?: 1)) else emptyList(),
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
                                    var previewPairs by remember(nodeId) { mutableStateOf<List<Pair<models.Item, Int>>>(emptyList()) }
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
                                        Button(attrs = {
                                            classes(NodeEditorStyles.addButton, Styles.primaryButton)
                                            onClick { showSelect = true }
                                        }) { Translated("Select items") }

                                        if (!itemsCsv.isBlank()) {
                                            Button(attrs = {
                                                classes(NodeEditorStyles.removeButton, Styles.primaryButton)
                                                onClick {
                                                    propertyValues = propertyValues.edit {
                                                        it[PropertyKey.ITEMS] = ""
                                                        it[PropertyKey.QUANTITIES] = ""
                                                    }
                                                }
                                            }) { Translated("Clear") }
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
                                    TextInput(
                                        value = propertyValues[propDef.key] ?: "",
                                        attrs = {
                                            classes(NodeEditorStyles.propertyInput)
                                            id("prop-${propDef.key.name.lowercase()}")
                                            onInput { event ->
                                                propertyValues = propertyValues.edit {
                                                    it[propDef.key] = event.value
                                                }
                                            }
                                            placeholder(propDef.defaultValue)
                                        }
                                    )
                                }
                            }
                            PropertyType.NUMBER -> {
                                NumberInput(
                                    value = propertyValues[propDef.key]?.toFloatOrNull() ?: 0.0f,
                                    attrs = {
                                        classes(NodeEditorStyles.propertyInput)
                                        id("prop-${propDef.key.name.lowercase()}")
                                        onInput { event ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = event.value.toString()
                                            }
                                        }
                                    }
                                )
                            }
                            PropertyType.BOOLEAN -> {
                                CheckboxInput(
                                    checked = (propertyValues[propDef.key] ?: "false") == "true",
                                    attrs = {
                                        classes(NodeEditorStyles.propertyInput)
                                        id("prop-${propDef.key.name.lowercase()}")
                                        onInput { event ->
                                            val isChecked = event.target.checked
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = if (isChecked) "true" else "false"
                                            }
                                        }
                                    }
                                )
                            }
                            PropertyType.VECTOR3 -> {
                                TextInput(
                                    value = propertyValues[propDef.key] ?: "0,0,0",
                                    attrs = {
                                        classes(NodeEditorStyles.propertyInput)
                                        id("prop-${propDef.key.name.lowercase()}")
                                        onInput { event ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = event.value
                                            }
                                        }
                                        placeholder(str("x,y,z"))
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
                            else -> {
                                // Default to text input for other types
                                TextInput(
                                    value = propertyValues[propDef.key] ?: "",
                                    attrs = {
                                        classes(NodeEditorStyles.propertyInput)
                                        id("prop-${propDef.key.name.lowercase()}")
                                        onInput { event ->
                                            propertyValues = propertyValues.edit {
                                                it[propDef.key] = event.value
                                            }
                                        }
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
                                Button(
                                    attrs = {
                                        classes(NodeEditorStyles.removeButton, Styles.primaryButton)
                                        onClick {
                                            // Remove this button
                                            val updatedButtons = buttons.toMutableList()
                                            updatedButtons.removeAt(index)
                                            propertyValues = propertyValues.edit {
                                                it[PropertyKey.BUTTONS] = updatedButtons.joinToString(",")
                                            }
                                        }
                                    }
                                ) {
                                    Text("✖️")
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
                    TextInput(
                        value = newButtonText,
                        attrs = {
                            classes(NodeEditorStyles.propertyInput)
                            placeholder(str("New button text"))
                            onInput { event ->
                                newButtonText = event.value
                            }
                        }
                    )
                    
                    Button(
                        attrs = {
                            classes(NodeEditorStyles.addButton, Styles.primaryButton)
                            onClick {
                                if (newButtonText.isNotEmpty()) {
                                    // Add new button
                                    val updatedButtons = buttons.toMutableList()
                                    updatedButtons.add(newButtonText)
                                    propertyValues = propertyValues.edit {
                                        it[PropertyKey.BUTTONS] = updatedButtons.joinToString(",")
                                    }
                                    newButtonText = ""
                                }
                            }
                        }
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
