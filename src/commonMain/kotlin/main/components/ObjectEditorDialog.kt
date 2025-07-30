package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.theme.Styles
import models.Object
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

/**
 * A dialog for creating and editing objects.
 * 
 * @param objectModel The object being edited, or null if creating a new object
 * @param worldId The ID of the world this object belongs to
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when an object is saved (created or updated)
 * @param onOpenLogicEditor Callback when the logic editor should be opened
 */
@Composable
fun ObjectEditorDialog(
    objectModel: Object? = null,
    worldId: String,
    onClose: () -> Unit,
    onSave: (Object) -> Unit,
    onOpenLogicEditor: (Object) -> Unit = {}
) {
    // State for form fields
    var title by remember { mutableStateOf(objectModel?.title ?: "") }
    var categories by remember { 
        mutableStateOf(
            objectModel?.categories?.joinToString(", ") ?: ""
        ) 
    }
    var file by remember { mutableStateOf(objectModel?.file ?: "") }
    var collection by remember { mutableStateOf(objectModel?.collection ?: "") }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = objectModel != null
    val dialogTitle = if (isEditing) "Edit Object" else "Create Object"
    
    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Function to handle form submission
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""
        
        // Convert categories string to list
        val categoryList = categories.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        val newObject = if (isEditing) {
            // Update existing object
            objectModel!!.copy(
                title = title,
                categories = categoryList,
                file = file,
                collection = collection
            )
        } else {
            // Create new object
            Object(
                title = title,
                categories = categoryList,
                file = file,
                collection = collection,
                data = "{}",
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
                classes(Styles.objectEditorDialogContent)
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            Div(
                attrs = {
                    classes(Styles.objectEditorDialogHeader)
                }
            ) {
                H3(
                    attrs = {
                        classes(Styles.objectEditorDialogTitle)
                    }
                ) {
                    Text(dialogTitle)
                }
                
                Button(
                    attrs = {
                        classes(Styles.objectEditorDialogCloseButton)
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Form content
            Div(
                attrs = {
                    classes(Styles.objectEditorDialogFormContent)
                }
            ) {
                // Error message
                if (errorMessage.isNotEmpty()) {
                    P(
                        attrs = {
                            classes(Styles.objectEditorDialogErrorMessage)
                        }
                    ) {
                        Text(errorMessage)
                    }
                }
                
                // Title field
                Div {
                    P {
                        Text("Title")
                    }
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("Object title")
                            value(title)
                            onInput { event -> title = event.value }
                        }
                    )
                }
                
                // Categories field
                Div {
                    P {
                        Text("Categories (comma-separated)")
                    }
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("e.g. furniture, interactive, decoration")
                            value(categories)
                            onInput { event -> categories = event.value }
                        }
                    )
                }
                
                // File field
                Div {
                    P {
                        Text("File (without .glb)")
                    }
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("e.g. world1")
                            value(file)
                            onInput { event -> file = event.value }
                        }
                    )
                }
                
                // Collection field
                Div {
                    P {
                        Text("Collection")
                    }
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("e.g. chair_collection")
                            value(collection)
                            onInput { event -> collection = event.value }
                        }
                    )
                }
                
                // Logic Editor preview
                Div(
                    attrs = {
                        classes(Styles.objectEditorDialogLogicPreview)
                        // Make the entire div clickable to open the logic editor
                        onClick {
                            // Create a current object with the form data
                            val currentObject = if (isEditing) {
                                objectModel!!.copy(
                                    title = title,
                                    categories = categories.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() },
                                    file = file,
                                    collection = collection
                                )
                            } else {
                                Object(
                                    title = title,
                                    categories = categories.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() },
                                    file = file,
                                    collection = collection,
                                    worldId = worldId,
                                    data = "{}",
                                    position = models.Position(0f, 0f, 0f),
                                    rotation = Rotation(0f, 0f, 0f),
                                    scale = Scale(1f, 1f, 1f)
                                )
                            }
                            
                            // Close this dialog and open the logic editor
                            onClose()
                            onOpenLogicEditor(currentObject)
                        }
                    }
                ) {
                    // Title for the logic editor section
                    H4(
                        attrs = {
                            classes(Styles.objectEditorDialogLogicTitle)
                        }
                    ) {
                        Text("Logic Editor")
                    }
                    
                    // Preview of the logic data or a placeholder message
                    Div(
                        attrs = {
                            classes(Styles.objectEditorDialogLogicDescription)
                        }
                    ) {
                        if (objectModel?.data != null && objectModel.data != "{}" && objectModel.data.isNotEmpty()) {
                            // Show a preview of the logic data
                            Text("Click to edit object logic (${
                                try {
                                    val nodeCount = js("Object.keys(JSON.parse(objectModel.data).nodes || {}).length")
                                    "$nodeCount nodes configured"
                                } catch (e: Exception) {
                                    "Logic data available"
                                }
                            })")
                        } else {
                            // Show a message for empty logic data
                            Text("Click to configure object logic")
                        }
                    }
                }
            }
            
            // Footer with action buttons
            Div(
                attrs = {
                    classes(Styles.objectEditorDialogFooter)
                }
            ) {
                // Cancel button
                Button(
                    attrs = {
                        classes(Styles.objectEditorDialogCancelButton)
                        onClick { onClose() }
                    }
                ) {
                    Text("Cancel")
                }
                
                // Save button
                Button(
                    attrs = {
                        classes(Styles.objectEditorDialogSaveButton)
                        classes(if (isSubmitting) Styles.objectEditorDialogSaveButtonInactive else Styles.objectEditorDialogSaveButtonActive)
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
