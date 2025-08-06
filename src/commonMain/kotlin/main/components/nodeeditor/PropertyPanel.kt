package main.components.nodeeditor

// Import the node data types and parser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.NodeEditorStyles
import models.toTypeString
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
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
    var label by remember { mutableStateOf(node.label) }
    
    // Parse node data
    val nodeData = NodeDataParser.parseNodeData(node.type, node.data)
    
    // Get property definitions for this node type
    val propertyDefinitions = node.type.getPropertyDefinitions()
    
    // Create a map to store property values
    val propertyValues = remember { mutableStateMapOf<PropertyKey, String>() }
    
    // Initialize property values from node data
    propertyDefinitions.forEach { propDef ->
        if (propDef.type != PropertyType.TRIGGER) {
            val value = NodeDataParser.getPropertyValue(nodeData, propDef.key)
            propertyValues[propDef.key] = value.ifEmpty { propDef.defaultValue }
        }
    }
    
    // Function to update the node
    val updateNode = {
        // Create updated property values
        val updatedProperties = propertyValues.map { (key, value) ->
            PropertyValue(key, value)
        }
        
        // Create updated node data
        val updatedNodeData = PropertyBasedNodeData(properties = updatedProperties)
        
        // Serialize the node data to a JSON string
        val updatedData = NodeDataParser.serializeNodeData(updatedNodeData)
        
        // Create updated node
        val updatedNode = node.copy(
            label = label,
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
            Text("Node Properties")
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
                Text("Type")
            }
            
            Div(
                attrs = {
                    classes(NodeEditorStyles.propertyValue)
                }
            ) {
                Text(node.type.toTypeString().replaceFirstChar { it.uppercase() })
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
                Text("Label")
            }
            
            Input(
                type = InputType.Text,
                attrs = {
                    classes(NodeEditorStyles.propertyInput)
                    id("node-label")
                    value(label)
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
                                                propertyValues[propDef.key] = event.value 
                                            }
                                            attr("rows", "4")
                                            placeholder("Enter text")
                                        }
                                    )
                                } else {
                                    // Use Input for other string properties
                                    TextInput(
                                        value = propertyValues[propDef.key] ?: "",
                                        attrs = {
                                            classes(NodeEditorStyles.propertyInput)
                                            id("prop-${propDef.key.name.lowercase()}")
                                            onInput { event ->
                                                propertyValues[propDef.key] = event.value 
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
                                            propertyValues[propDef.key] = event.value.toString()
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
                                            propertyValues[propDef.key] = if (isChecked) "true" else "false"
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
                                            propertyValues[propDef.key] = event.value 
                                        }
                                        placeholder("x,y,z")
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
                                            propertyValues[propDef.key] = event.value 
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
                                            propertyValues[propDef.key] = event.value 
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        
        // Apply button
        Button(
            attrs = {
                classes(NodeEditorStyles.propertyButton)
                onClick { updateNode() }
            }
        ) {
            Text("Apply Changes")
        }
    }
}
