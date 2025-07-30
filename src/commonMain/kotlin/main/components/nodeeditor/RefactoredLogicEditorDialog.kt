package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.theme.NodeEditorStyles
import main.theme.Styles
import models.Object
import models.Position
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * A refactored dialog for editing object logic.
 * This version uses the new NodeEditorController and related components.
 * 
 * @param objectModel The object being edited, or null if creating a new object
 * @param worldId The ID of the world this object belongs to
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when an object is saved (created or updated)
 */
@Composable
fun RefactoredLogicEditorDialog(
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
    
    // Parse nodes and connections from object data
    val initialNodes = remember { parseNodes(objectModel?.data ?: "{}") }
    val initialConnections = remember { parseConnections(objectModel?.data ?: "{}") }
    
    // Create the node editor controller
    val controller = remember { NodeEditorController(initialNodes, initialConnections) }
    
    // State for fullscreen mode
    var isFullscreen by remember { mutableStateOf(false) }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = objectModel != null
    val dialogTitle = if (isEditing) "Edit Object Logic" else "Create Object"
    
    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Function to toggle fullscreen mode
    fun toggleFullscreen() {
        try {
            val doc = document
            
            if (doc.fullscreenElement == null) {
                // Enter fullscreen mode
                document.documentElement?.requestFullscreen()
                // State will be updated by the fullscreenchange event listener
            } else {
                // Exit fullscreen mode
                doc.exitFullscreen()
                // State will be updated by the fullscreenchange event listener
            }
        } catch (e: dynamic) {
            console.error("Fullscreen API error: ", e)
        }
    }
    
    // Update fullscreen state when fullscreen changes
    DisposableEffect(Unit) {
        val fullscreenChangeListener: (dynamic) -> Unit = {
            isFullscreen = document.fullscreenElement != null
        }
        
        document.addEventListener("fullscreenchange", fullscreenChangeListener)
        
        onDispose {
            document.removeEventListener("fullscreenchange", fullscreenChangeListener)
        }
    }
    
    // Function to handle form submission
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""
        
        // Convert categories string to list
        val categoryList = categories.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // Serialize node editor data to JSON
        val nodeData = controller.serializeToJson()
        
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
                position = Position(0f, 0f, 0f),
                rotation = Rotation(0f, 0f, 0f),
                scale = Scale(1f, 1f, 1f)
            )
        }
        
        coroutineScope.launch {
            try {
                val savedObject = if (isEditing) {
                    ObjectApi.updateObject(objectModel!!.key, newObject)
                } else {
                    ObjectApi.createObject(newObject)
                }
                
                isSubmitting = false
                onSave(savedObject)
                onClose()
            } catch (error: Exception) {
                isSubmitting = false
                errorMessage = error.message ?: "An error occurred"
            }
        }
    }
    
    // Main dialog container
    Div(
        attrs = {
            classes(Styles.overlayBase)
            // Add click outside to close
            onClick { onClose() }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                classes(NodeEditorStyles.dialogContent)
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            Div(
                attrs = {
                    classes(NodeEditorStyles.dialogHeader)
                }
            ) {
                H3(
                    attrs = {
                        classes(NodeEditorStyles.dialogTitle)
                    }
                ) {
                    Text(dialogTitle)
                }
                
                // Fullscreen button
                Button(
                    attrs = {
                        classes(NodeEditorStyles.closeButton)
                        onClick { toggleFullscreen() }
                    }
                ) {
                    Text(if (isFullscreen) "⤓" else "⤢")
                }
                    
                Button(
                    attrs = {
                        classes(NodeEditorStyles.closeButton)
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Main content area (flex row with left panel, node editor, and property panel)
            Div(
                attrs = {
                    classes(NodeEditorStyles.mainContentArea)
                }
            ) {
                // Left panel (Events, Dialogue, Actions)
                Div(
                    attrs = {
                        classes(NodeEditorStyles.sidebarContainer)
                    }
                ) {
                    // Events section
                    H4(
                        attrs = {
                            classes(NodeEditorStyles.sectionHeader)
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
                            classes(NodeEditorStyles.sectionHeader)
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
                            classes(NodeEditorStyles.sectionHeader)
                        }
                    ) {
                        Text("Actions")
                    }
                    
                    // List of draggable actions
                    DraggableItem("Teleport player", "action", "teleport")
                    DraggableItem("Play sound", "action", "play_sound")
                    DraggableItem("Show effect", "action", "show_effect")
                    
                    // Inputs section
                    H4(
                        attrs = {
                            classes(NodeEditorStyles.sectionHeader)
                        }
                    ) {
                        Text("Inputs")
                    }
                    
                    // List of draggable inputs
                    DraggableItem("Player info", "input", "player_info")
                    DraggableItem("Visitor info", "input", "visitor_info")
                    DraggableItem("World info", "input", "world_info")
                    DraggableItem("Object info", "input", "object_info")
                }
                
                // Node editor canvas
                NodeEditorCanvas(
                    controller = controller,
                    onKeyDown = { event ->
                        // Check if delete key is pressed
                        if (event.key == "Delete" || event.key == "Backspace") {
                            // If a node is selected, delete it
                            if (controller.selectedNode != null) {
                                controller.deleteNode(controller.selectedNode!!)
                                event.preventDefault()
                            }
                            // If a connection is selected, delete it
                            else if (controller.selectedConnection != null) {
                                controller.deleteConnection(controller.selectedConnection!!)
                                event.preventDefault()
                            }
                        }
                        // Check for undo (Ctrl+Z)
                        else if (event.key.lowercase() == "z" && event.ctrlKey && !event.shiftKey) {
                            controller.undo()
                            event.preventDefault()
                        }
                        // Check for redo (Ctrl+Y or Ctrl+Shift+Z)
                        else if ((event.key.lowercase() == "y" && event.ctrlKey) || 
                                 (event.key.lowercase() == "z" && event.ctrlKey && event.shiftKey)) {
                            controller.redo()
                            event.preventDefault()
                        }
                    }
                )
                
                // Property panel (shown when a node is selected)
                if (controller.selectedNode != null) {
                    val node = controller.nodes[controller.selectedNode]
                    if (node != null) {
                        PropertyPanel(
                            nodeId = controller.selectedNode!!,
                            node = node,
                            onNodeUpdate = { nodeId, updatedNode ->
                                controller.updateNode(nodeId, updatedNode)
                            }
                        )
                    }
                }
            }
            
            // Footer with action buttons
            Div(
                attrs = {
                    classes(NodeEditorStyles.dialogFooter)
                }
            ) {
                // Error message
                if (errorMessage.isNotEmpty()) {
                    Span(
                        attrs = {
                            classes(NodeEditorStyles.errorMessage)
                        }
                    ) {
                        Text(errorMessage)
                    }
                }
                
                // Cancel button
                Button(
                    attrs = {
                        classes(NodeEditorStyles.cancelButton)
                        onClick { onClose() }
                    }
                ) {
                    Text("Cancel")
                }
                
                // Save button
                Button(
                    attrs = {
                        classes(NodeEditorStyles.saveButton)
                        classes(if (isSubmitting) NodeEditorStyles.saveButtonDisabled else NodeEditorStyles.saveButtonEnabled)
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
