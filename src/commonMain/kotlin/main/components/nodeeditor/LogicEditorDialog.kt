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
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.events.SyntheticKeyboardEvent

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
    
    // Initialize the NodeEditorController with initial nodes and connections
    val controller = remember { 
        NodeEditorController(
            initialNodes = parseNodes(objectModel?.data ?: "{}"),
            initialConnections = parseConnections(objectModel?.data ?: "{}")
        )
    }
    
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
                position = models.Position(0f, 0f, 0f),
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
                // Apply fullscreen or regular dialog content style based on fullscreen state
                classes(if (isFullscreen) NodeEditorStyles.dialogContentFullscreen else NodeEditorStyles.dialogContent)
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            DialogHeader(
                title = dialogTitle,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { toggleFullscreen() },
                onClose = onClose
            )
            
            // Main content area (flex row with left panel, node editor, and property panel)
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
            
            // Footer with action buttons
            DialogFooter(
                errorMessage = errorMessage,
                isSubmitting = isSubmitting,
                onCancel = onClose,
                onSave = { if (!isSubmitting) handleSubmit() }
            )
        }
    }
}
