package main.components.nodeeditor

// Import the node data types and parser
import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import main.components.Translated
import main.components.translated
import main.api.ApiClient
import main.api.ContentApiConstants
import main.api.UserApi
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
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.TextInput
import kotlin.collections.getValue
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.fetch.RequestInit
import org.w3c.xhr.FormData

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
                Text(node.type.id.name) // todo translate
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
                Text(str("Label"))
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
                                    val fileInputId = "image-upload-${nodeId}"

                                    // Hidden file input for selecting an image
                                    Input(InputType.File, attrs = {
                                        id(fileInputId)
                                        attr("accept", "image/*")
                                        attr("style", "display:none")
                                        onInput { event ->
                                            val input = event.target as HTMLInputElement
                                            val file = input.files?.item(0)
                                            if (file != null) {
                                                val scope = MainScope()
                                                scope.launch {
                                                    try {
                                                        // Ensure we have a valid token
                                                        val user = UserApi.getCurrentUser()
                                                        val token = UserApi.getToken() ?: ""

                                                        val formData = FormData()
                                                        formData.append("API_USER_BEARER_TOKEN", token)
                                                        formData.append("file", file, file.name)

                                                        val uploadUrl = ContentApiConstants.CONTENT_BASE_URL + "/upload"
                                                        val init = js("({})").unsafeCast<RequestInit>().also {
                                                            it.method = "POST"
                                                            it.body = formData
                                                        }
                                                        val response = window.fetch(uploadUrl, init).await()
                                                        val text = response.text().await()

                                                        if (!response.ok) {
                                                            console.error("Upload failed: ", text)
                                                        } else {
                                                            try {
                                                                val record = ApiClient.json.decodeFromString<models.ContentUpload>(text)
                                                                val finalUrl = ContentApiConstants.CONTENT_BASE_URL + record.uploadedFilePath
                                                                propertyValues = propertyValues.edit {
                                                                    it[PropertyKey.IMAGE_URL] = finalUrl
                                                                }
                                                            } catch (e: Throwable) {
                                                                console.error("Failed parsing upload response", e)
                                                            }
                                                        }
                                                    } catch (e: Throwable) {
                                                        console.error("Error uploading image", e)
                                                    } finally {
                                                        // Reset input to allow re‑selecting the same file
                                                        event.target.value = ""
                                                    }
                                                }
                                            }
                                        }
                                    })

                                    if (imageUrl.isNotBlank()) {
                                        // Show preview and remove button
                                        Img(
                                            src = imageUrl,
                                            attrs = {
                                                style {
                                                    property("border-radius", "8px")
                                                    property("overflow", "hidden")
                                                }
                                            }
                                        )
                                        Button(attrs = {
                                            classes(NodeEditorStyles.removeButton, Styles.primaryButton)
                                            onClick {
                                                propertyValues = propertyValues.edit {
                                                    it[PropertyKey.IMAGE_URL] = ""
                                                }
                                            }
                                        }) { Translated("Remove image") }
                                    } else {
                                        // Add image button triggers file input click
                                        Button(attrs = {
                                            classes(NodeEditorStyles.addButton, Styles.primaryButton)
                                            onClick {
                                                val el = document.getElementById(fileInputId) as? HTMLInputElement
                                                el?.click()
                                            }
                                        }) { Translated("Add image") }
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
                                    value = propertyValues[propDef.key]?.toFloat() ?: 0.0f,
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
                Label(
                    attrs = {
                        classes(NodeEditorStyles.propertyLabel)
                    }
                ) {
                    Text(translated("Buttons"))
                }
                
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
                                    Text("×")
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
