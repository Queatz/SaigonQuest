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
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.translated
import main.theme.NodeEditorStyles
import main.utils.toggleFullscreen
import models.Object
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.events.SyntheticKeyboardEvent

/**
 * A dialog for editing object logic.
 * 
 * @param objectModel The object being edited
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when an object is saved
 */
@Composable
fun LogicEditorDialog(
    objectModel: Object,
    onClose: () -> Unit,
    onSave: (Object) -> Unit
) {
    // State for form fields
    var title by remember { mutableStateOf(objectModel.title) }

    // State for fullscreen mode
    var isFullscreen by remember { mutableStateOf(false) }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Set dialog title
    val dialogTitle = "${translated("Edit")} ${objectModel.title.ifBlank { translated("Object") }} ${translated("Logic")}"
    
    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize the NodeEditorController with initial nodes and connections
    val controller = remember { 
        NodeEditorController(
            initialNodes = parseNodes(objectModel.data),
            initialConnections = parseConnections(objectModel.data),
        )
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
        
        // Serialize node editor data to JSON
        val nodeData = controller.serializeToJson()
        
        // Update existing object
        val newObject = objectModel.copy(
            title = title,
            data = nodeData
        )
        
        coroutineScope.launch {
            try {
                // Update the object
                val savedObject = ObjectApi.updateObject(objectModel.key, newObject)
                
                isSubmitting = false
                onSave(savedObject)
                onClose()
            } catch (error: Throwable) {
                isSubmitting = false
                errorMessage = error.message ?: Translations.str("An error occurred")
            }
        }
    }
    
    // Keyboard event handler for the node editor
    val handleKeyDown = { event: SyntheticKeyboardEvent ->
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
    
    // Use BaseDialog with isFullscreen parameter
    BaseDialog(
        onClose = onClose,
        title = dialogTitle,
        maxWidth = "100%",
        clickOutside = false,
        isFullHeight = true,
        isFullscreen = isFullscreen,
        // Custom header actions for fullscreen toggle
        headerActions = {
            Button(
                attrs = {
                    classes(NodeEditorStyles.closeButton)
                    onClick { toggleFullscreen() }
                }
            ) {
                Text(if (isFullscreen) "⤓" else "⤢")
            }
        },
        // Main content
        content = {
            // Node editor content area
            Div(
                attrs = {
                    classes(NodeEditorStyles.mainContentArea)
                }
            ) {
                // Left panel with draggable items
                NodeSidebar()
                
                // Node editor panel
                NodeEditorPanel(
                    controller = controller,
                    onKeyDown = handleKeyDown,
                    onNodeAdd = { type, id, label, x, y ->
                        controller.addNode(type, id, label, x, y)
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
            
            // Show error message if present
            if (errorMessage.isNotEmpty()) {
                Span(
                    attrs = {
                        classes(NodeEditorStyles.errorMessage)
                    }
                ) {
                    Text(errorMessage)
                }
            }
        },
        // Footer actions
        actions = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                    }
                }
            ) {
                // Cancel button
                DialogButton(
                    onClick = onClose
                ) {
                    Translated("Cancel")
                }
                
                // Save button
                DialogButton(
                    onClick = { if (!isSubmitting) handleSubmit() },
                    isPrimary = true
                ) {
                    Text(if (isSubmitting) translated("Saving...") else translated("Save"))
                }
            }
        }
    )
}
