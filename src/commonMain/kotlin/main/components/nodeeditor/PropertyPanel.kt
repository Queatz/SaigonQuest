package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.NodeEditorStyles
import models.toTypeString
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import web.cssom.AtRules.Companion.property
import kotlinx.serialization.json.Json
// Import the node data types and parser
import main.components.nodeeditor.NodeDataType
import main.components.nodeeditor.TextNodeData
import main.components.nodeeditor.ButtonsNodeData
import main.components.nodeeditor.TeleportNodeData
import main.components.nodeeditor.SoundNodeData
import main.components.nodeeditor.EffectNodeData
import main.components.nodeeditor.EmptyNodeData
import main.components.nodeeditor.NodeDataParser

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
    
    // Additional properties based on node type
    var textContent by remember { mutableStateOf("") }
    var buttonLabels by remember { mutableStateOf("") }
    var targetPosition by remember { mutableStateOf("0,0,0") }
    var soundFile by remember { mutableStateOf("") }
    var effectType by remember { mutableStateOf("") }
    
    // Initialize additional properties based on node type and existing data
    val typedNodeData = NodeDataParser.parseNodeData(node.type, node.data)
    
    when (typedNodeData) {
        is TextNodeData -> textContent = typedNodeData.text
        is ButtonsNodeData -> buttonLabels = typedNodeData.buttons
        is TeleportNodeData -> targetPosition = typedNodeData.position
        is SoundNodeData -> soundFile = typedNodeData.sound
        is EffectNodeData -> effectType = typedNodeData.effect
        else -> { /* No specific data to initialize */ }
    }
    
    // Function to update the node
    val updateNode = {
        // Create updated node data
        val updatedNodeData: NodeDataType = when (node.type.toTypeString()) {
            "dialogue" -> {
                if (node.label == "Present text") {
                    TextNodeData(text = textContent)
                } else if (node.label == "Show buttons") {
                    ButtonsNodeData(buttons = buttonLabels)
                } else {
                    EmptyNodeData()
                }
            }
            "action" -> {
                when (node.label) {
                    "Teleport player" -> TeleportNodeData(position = targetPosition)
                    "Play sound" -> SoundNodeData(sound = soundFile)
                    "Show effect" -> EffectNodeData(effect = effectType)
                    else -> EmptyNodeData()
                }
            }
            else -> EmptyNodeData()
        }
        
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
        
        // Type-specific properties
        when (node.type.toTypeString()) {
            "dialogue" -> {
                if (node.label == "Present text") {
                    // Text content for Present text node
                    Div(
                        attrs = {
                            classes(NodeEditorStyles.propertyGroup)
                        }
                    ) {
                        Label(
                            attrs = {
                                classes(NodeEditorStyles.propertyLabel)
                                attr("for", "text-content")
                            }
                        ) {
                            Text("Text Content")
                        }
                        
                        TextArea(
                            attrs = {
                                classes(NodeEditorStyles.propertyTextArea)
                                id("text-content")
                                value(textContent)
                                onInput { event -> textContent = event.value }
                                attr("rows", "4")
                                placeholder("Enter text to display")
                            }
                        )
                    }
                } else if (node.label == "Show buttons") {
                    // Button labels for Show buttons node
                    Div(
                        attrs = {
                            classes(NodeEditorStyles.propertyGroup)
                        }
                    ) {
                        Label(
                            attrs = {
                                classes(NodeEditorStyles.propertyLabel)
                                attr("for", "button-labels")
                            }
                        ) {
                            Text("Button Labels (comma-separated)")
                        }
                        
                        Input(
                            type = InputType.Text,
                            attrs = {
                                classes(NodeEditorStyles.propertyInput)
                                id("button-labels")
                                value(buttonLabels)
                                onInput { event -> buttonLabels = event.value }
                                placeholder("OK, Cancel, More Info")
                            }
                        )
                    }
                }
            }
            "action" -> {
                when (node.label) {
                    "Teleport player" -> {
                        // Target position for Teleport player node
                        Div(
                            attrs = {
                                classes(NodeEditorStyles.propertyGroup)
                            }
                        ) {
                            Label(
                                attrs = {
                                    classes(NodeEditorStyles.propertyLabel)
                                    attr("for", "target-position")
                                }
                            ) {
                                Text("Target Position (x,y,z)")
                            }
                            
                            Input(
                                type = InputType.Text,
                                attrs = {
                                    classes(NodeEditorStyles.propertyInput)
                                    id("target-position")
                                    value(targetPosition)
                                    onInput { event -> targetPosition = event.value }
                                    placeholder("0,0,0")
                                }
                            )
                        }
                    }
                    "Play sound" -> {
                        // Sound file for Play sound node
                        Div(
                            attrs = {
                                classes(NodeEditorStyles.propertyGroup)
                            }
                        ) {
                            Label(
                                attrs = {
                                    classes(NodeEditorStyles.propertyLabel)
                                    attr("for", "sound-file")
                                }
                            ) {
                                Text("Sound File")
                            }
                            
                            Input(
                                type = InputType.Text,
                                attrs = {
                                    classes(NodeEditorStyles.propertyInput)
                                    id("sound-file")
                                    value(soundFile)
                                    onInput { event -> soundFile = event.value }
                                    placeholder("sounds/beep.mp3")
                                }
                            )
                        }
                    }
                    "Show effect" -> {
                        // Effect type for Show effect node
                        Div(
                            attrs = {
                                classes(NodeEditorStyles.propertyGroup)
                            }
                        ) {
                            Label(
                                attrs = {
                                    classes(NodeEditorStyles.propertyLabel)
                                    attr("for", "effect-type")
                                }
                            ) {
                                Text("Effect Type")
                            }
                            
                            Input(
                                type = InputType.Text,
                                attrs = {
                                    classes(NodeEditorStyles.propertyInput)
                                    id("effect-type")
                                    value(effectType)
                                    onInput { event -> effectType = event.value }
                                    placeholder("sparkle, explosion, etc.")
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

// Helper function to capitalize first letter
private fun String.capitalize(): String {
    return if (this.isNotEmpty()) {
        this[0].uppercase() + this.substring(1)
    } else {
        this
    }
}
