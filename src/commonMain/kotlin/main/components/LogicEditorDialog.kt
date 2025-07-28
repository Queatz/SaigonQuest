package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.ObjectApi
import models.Object
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Promise
import kotlin.js.json

/**
 * A dialog for editing object logic.
 * 
 * @param objectModel The object being edited, or null if creating a new object
 * @param worldId The ID of the world this object belongs to
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when an object is saved (created or updated)
 */
@Composable
fun LogicEditorDialog(
    objectModel: Object? = null,
    worldId: String,
    onClose: () -> Unit,
    onSave: (Object) -> Unit
) {
    // State for form fields
    var title by remember { mutableStateOf(objectModel?.title ?: "") }
    var categories by remember { 
        mutableStateOf(
            objectModel?.categories?.joinToString(", ") ?: ""
        )
    }
    var data by remember { mutableStateOf(objectModel?.data ?: "{}") }
    
    // State for node editor
    var selectedNode by remember { mutableStateOf<String?>(null) }
    var nodes by remember { mutableStateOf(parseNodes(objectModel?.data ?: "{}")) }
    var connections by remember { mutableStateOf(parseConnections(objectModel?.data ?: "{}")) }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = objectModel != null
    val dialogTitle = if (isEditing) "Edit Object Logic" else "Create Object"
    
    // Function to handle form submission
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""
        
        // Convert categories string to list
        val categoryList = categories.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // Serialize node editor data to JSON
        val nodeData = serializeNodeData(nodes, connections)
        
        val newObject = if (isEditing) {
            // Update existing object
            objectModel!!.copy(
                title = title,
                categories = categoryList,
                data = nodeData
            )
        } else {
            // Create new object
            Object(
                title = title,
                categories = categoryList,
                data = nodeData,
                worldId = worldId,
                position = models.Position(0f, 0f, 0f),
                rotation = Rotation(0f, 0f, 0f),
                scale = Scale(1f, 1f, 1f)
            )
        }
        
        val promise = if (isEditing) {
            ObjectApi.updateObject(objectModel!!.key, newObject)
        } else {
            ObjectApi.createObject(newObject)
        }
        
        promise
            .then { savedObject ->
                isSubmitting = false
                onSave(savedObject)
                onClose()
                Promise.resolve(Unit)
            }
            .catch { error ->
                isSubmitting = false
                errorMessage = error.message ?: "An error occurred"
                Promise.resolve(Unit)
            }
    }
    
    // Main dialog container
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.vh)
                backgroundColor(rgba(0, 0, 0, 0.7))
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                // Make dialog interactive
                property("pointer-events", "auto")
                // Set high z-index to appear above other UI
                property("z-index", "1000")
            }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                style {
                    width(90.percent)
                    property("max-width", "1200px")
                    height(90.vh)
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
            }
        ) {
            // Dialog header
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                H3(
                    attrs = {
                        style {
                            margin(0.px)
                            color(Color.white)
                        }
                    }
                ) {
                    Text(dialogTitle)
                }
                
                Button(
                    attrs = {
                        style {
                            backgroundColor(Color.transparent)
                            property("border", "0")
                            color(Color.white)
                            cursor("pointer")
                            fontSize(20.px)
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Main content area (flex row with left panel and node editor)
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Row)
                        property("flex-grow", "1")
                        property("overflow", "hidden")
                    }
                }
            ) {
                // Left panel (Events, Dialogue, Actions)
                Div(
                    attrs = {
                        style {
                            width(250.px)
                            backgroundColor(rgba(20, 20, 20, 0.8))
                            padding(16.px)
                            property("overflow-y", "auto")
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            property("gap", "16px")
                        }
                    }
                ) {
                    // Basic object info
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                property("gap", "8px")
                                marginBottom(16.px)
                            }
                        }
                    ) {
                        // Title field
                        Div {
                            Label(
                                attrs = {
                                    style {
                                        color(Color.white)
                                        fontSize(14.px)
                                        display(DisplayStyle.Block)
                                        marginBottom(4.px)
                                    }
                                }
                            ) {
                                Text("Title")
                            }
                            
                            Input(
                                type = InputType.Text,
                                attrs = {
                                    style {
                                        width(100.percent)
                                        padding(8.px)
                                        backgroundColor(rgba(255, 255, 255, 0.1))
                                        color(Color.white)
                                        property("border", "1px solid rgba(255, 255, 255, 0.2)")
                                        property("border-radius", "4px")
                                        outline("none")
                                    }
                                    value(title)
                                    onInput { event -> title = event.value }
                                    placeholder("Enter object title")
                                }
                            )
                        }
                        
                        // Categories field
                        Div {
                            Label(
                                attrs = {
                                    style {
                                        color(Color.white)
                                        fontSize(14.px)
                                        display(DisplayStyle.Block)
                                        marginBottom(4.px)
                                    }
                                }
                            ) {
                                Text("Categories (comma-separated)")
                            }
                            
                            Input(
                                type = InputType.Text,
                                attrs = {
                                    style {
                                        width(100.percent)
                                        padding(8.px)
                                        backgroundColor(rgba(255, 255, 255, 0.1))
                                        color(Color.white)
                                        property("border", "1px solid rgba(255, 255, 255, 0.2)")
                                        property("border-radius", "4px")
                                        outline("none")
                                    }
                                    value(categories)
                                    onInput { event -> categories = event.value }
                                }
                            )
                        }
                    }
                    
                    // Events section
                    H4(
                        attrs = {
                            style {
                                color(Color.white)
                                margin(0.px)
                                marginTop(8.px)
                            }
                        }
                    ) {
                        Text("Events")
                    }
                    
                    // List of draggable events
                    DraggableItem("Visitor nearby", "event", "visitor_nearby")
                    DraggableItem("Visitor interact", "event", "visitor_interact")
                    DraggableItem("Player nearby", "event", "player_nearby")
                    DraggableItem("Player interact", "event", "player_interact")
                    DraggableItem("Local time", "event", "local_time")
                    
                    // Dialogue section
                    H4(
                        attrs = {
                            style {
                                color(Color.white)
                                margin(0.px)
                                marginTop(16.px)
                            }
                        }
                    ) {
                        Text("Dialogue")
                    }
                    
                    // List of draggable dialogue options
                    DraggableItem("Present text", "dialogue", "present_text")
                    DraggableItem("Show buttons", "dialogue", "show_buttons")
                    
                    // Actions section
                    H4(
                        attrs = {
                            style {
                                color(Color.white)
                                margin(0.px)
                                marginTop(16.px)
                            }
                        }
                    ) {
                        Text("Actions")
                    }
                    
                    // List of draggable actions
                    DraggableItem("Teleport player", "action", "teleport")
                    DraggableItem("Play sound", "action", "play_sound")
                    DraggableItem("Show effect", "action", "show_effect")
                }
                
                // Node editor canvas
                Div(
                    attrs = {
                        style {
                            property("flex-grow", "1")
                            backgroundColor(rgba(40, 40, 40, 0.8))
                            padding(16.px)
                            property("overflow", "auto")
                            position(Position.Relative)
                        }
                    }
                ) {
                    // Placeholder for node editor
                    // In a real implementation, this would be a canvas or a more complex component
                    // For now, we'll just show a placeholder
                    Div(
                        attrs = {
                            style {
                                width(100.percent)
                                height(100.percent)
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.Center)
                                alignItems(AlignItems.Center)
                                color(Color.white)
                                property("border", "2px dashed rgba(255, 255, 255, 0.3)")
                                property("border-radius", "8px")
                            }
                        }
                    ) {
                        Text("Node Editor Canvas - Drag items from the left panel to create nodes")
                    }
                    
                    // Render existing nodes
                    nodes.forEach { (id, node) ->
                        NodeElement(
                            id = id,
                            node = node,
                            isSelected = id == selectedNode,
                            onSelect = { selectedNode = id },
                            onEdit = { /* Open node edit dialog */ }
                        )
                    }
                }
            }
            
            // Footer with action buttons
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        property("gap", "8px")
                        property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                // Error message
                if (errorMessage.isNotEmpty()) {
                    Span(
                        attrs = {
                            style {
                                color(Color.red)
                                property("flex-grow", "1")
                            }
                        }
                    ) {
                        Text(errorMessage)
                    }
                }
                
                // Cancel button
                Button(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(rgba(255, 255, 255, 0.1))
                            color(Color.white)
                            property("border", "1px solid rgba(255, 255, 255, 0.2)")
                            property("border-radius", "4px")
                            cursor("pointer")
                            marginRight(8.px)
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("Cancel")
                }
                
                // Save button
                Button(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(if (isSubmitting) rgba(100, 100, 100, 0.5) else rgba(0, 120, 255, 0.7))
                            color(Color.white)
                            property("border", "0")
                            property("border-radius", "4px")
                            cursor(if (isSubmitting) "default" else "pointer")
                        }
                        // Only allow clicking if not submitting
                        onClick { if (!isSubmitting) handleSubmit() }
                    }
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save")
                }
            }
        }
    }
}

/**
 * A draggable item in the left panel.
 */
@Composable
fun DraggableItem(label: String, type: String, id: String) {
    Div(
        attrs = {
            style {
                padding(8.px, 12.px)
                backgroundColor(rgba(60, 60, 60, 0.8))
                color(Color.white)
                property("border-radius", "4px")
                cursor("grab")
                marginBottom(8.px)
            }
            // Add draggable attributes
            attr("draggable", "true")
            attr("data-type", type)
            attr("data-id", id)
        }
    ) {
        Text(label)
    }
}

/**
 * A simple data class to represent a node in the editor.
 */
data class NodeData(
    val type: String,
    val x: Float,
    val y: Float,
    val label: String
)

/**
 * A node element in the node editor.
 */
@Composable
fun NodeElement(
    id: String,
    node: NodeData,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    // Use the NodeData properties
    val type = node.type
    val x = node.x
    val y = node.y
    val label = node.label
    
    // Determine background color based on type
    val bgColor = when(type) {
        "event" -> rgba(70, 130, 180, 0.8) // Steel blue
        "dialogue" -> rgba(60, 179, 113, 0.8) // Medium sea green
        "action" -> rgba(178, 34, 34, 0.8) // Firebrick
        else -> rgba(100, 100, 100, 0.8) // Gray
    }
    
    Div(
        attrs = {
            style {
                position(Position.Absolute)
                left(x.px)
                top(y.px)
                width(180.px)
                padding(12.px)
                backgroundColor(bgColor)
                color(Color.white)
                property("border-radius", "6px")
                property("box-shadow", "0 2px 10px rgba(0, 0, 0, 0.3)")
                cursor("pointer")
                // Add border if selected
                if (isSelected) {
                    property("border", "2px solid white")
                } else {
                    property("border", "2px solid transparent")
                }
            }
            onClick { onSelect() }
        }
    ) {
        // Node header
        Div(
            attrs = {
                style {
                    fontWeight("bold")
                    marginBottom(8.px)
                }
            }
        ) {
            Text(label)
        }
        
        // Node inputs/outputs would go here in a real implementation
    }
}

/**
 * Parse node data from JSON string.
 */
fun parseNodes(jsonData: String): Map<String, NodeData> {
    try {
        val data = JSON.parse<dynamic>(jsonData)
        val result = mutableMapOf<String, NodeData>()
        
        if (data.nodes != undefined) {
            val nodes = data.nodes
            for (key in js("Object").keys(nodes)) {
                val node = nodes[key]
                result[key as String] = NodeData(
                    type = node.type as? String ?: "",
                    x = (node.x as? Number)?.toFloat() ?: 0f,
                    y = (node.y as? Number)?.toFloat() ?: 0f,
                    label = node.label as? String ?: "Node"
                )
            }
        }
        
        return result
    } catch (e: Exception) {
        console.error("Error parsing nodes", e)
        return emptyMap()
    }
}

/**
 * Parse connection data from JSON string.
 */
fun parseConnections(jsonData: String): List<dynamic> {
    try {
        val data = JSON.parse<dynamic>(jsonData)
        val result = mutableListOf<dynamic>()
        
        if (data.connections != undefined) {
            val connections = data.connections
            for (i in 0 until connections.length) {
                result.add(connections[i])
            }
        }
        
        return result
    } catch (e: Exception) {
        console.error("Error parsing connections", e)
        return emptyList()
    }
}

/**
 * Serialize node editor data to JSON string.
 */
fun serializeNodeData(nodes: Map<String, NodeData>, connections: List<dynamic>): String {
    val nodesObj = js("{}")
    
    for ((id, node) in nodes) {
        // Convert NodeData back to a JS object
        val nodeObj = js("{}")
        nodeObj["type"] = node.type
        nodeObj["x"] = node.x
        nodeObj["y"] = node.y
        nodeObj["label"] = node.label
        
        nodesObj[id] = nodeObj
    }
    
    val connectionsArray = js("[]")
    for (connection in connections) {
        connectionsArray.push(connection)
    }
    
    val data = json(
        "nodes" to nodesObj,
        "connections" to connectionsArray
    )
    
    return JSON.stringify(data)
}
