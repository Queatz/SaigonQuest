package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import main.api.ObjectApi
import main.theme.Styles
import models.Object
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.RangeInput
import org.jetbrains.compose.web.dom.Text

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
    var file by remember { mutableStateOf(objectModel?.file ?: "") }
    var collection by remember { mutableStateOf(objectModel?.collection ?: "") }
    var status by remember { mutableStateOf(objectModel?.status ?: "") }
    var rotationYDeg by remember {
        mutableStateOf(
            ((objectModel?.rotation?.z ?: 0f) * 180f / kotlin.math.PI.toFloat()).let { deg ->
                // normalize to [0, 359]
                val n = ((deg % 360f) + 360f) % 360f
                n
            }
        )
    }
    
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
        
        val newObject = if (isEditing) {
            // Update existing object
            objectModel.copy(
                title = title,
                file = file,
                collection = collection,
                status = status,
                rotation = objectModel.rotation.copy(
                    y = rotationYDeg * kotlin.math.PI.toFloat() / 180f,
                ),
            )
        } else {
            // Create new object at the player's current position (fallback to 0,0,0 if unavailable)
            val playerPos = SceneManager.getPlayerController()?.getPlayerPosition()
            val initialPosition = if (playerPos != null) {
                models.Position(
                    x = playerPos.x,
                    y = playerPos.y,
                    z = playerPos.z,
                )
            } else {
                models.Position(0f, 0f, 0f)
            }
            Object(
                title = title,
                file = file,
                collection = collection,
                data = "{}",
                worldId = worldId,
                position = initialPosition,
                rotation = Rotation(0f, rotationYDeg * kotlin.math.PI.toFloat() / 180f, 0f),
                scale = Scale(1f, 1f, 1f),
                status = status
            )
        }
        
        coroutineScope.launch {
            try {
                val savedObject = if (isEditing) {
                    ObjectApi.updateObject(objectModel.key, newObject)
                } else {
                    ObjectApi.createObject(newObject)
                }
                
                isSubmitting = false
                onSave(savedObject)
                onClose()
            } catch (error: Throwable) {
                isSubmitting = false
                errorMessage = error.message ?: "An error occurred"
            }
        }
    }
    
    // Define the footer actions
    val footerActions = @Composable {
        // Cancel button
        DialogButton(
            onClick = { onClose() },
            isPrimary = false
        ) {
            Text("Cancel")
        }
        
        // Save button
        DialogButton(
            onClick = { if (!isSubmitting) handleSubmit() },
            isPrimary = true
        ) {
            Text(if (isSubmitting) "Saving..." else "Save")
        }
    }
    
    // Use BaseDialog for consistent styling and behavior
    BaseDialog(
        onClose = onClose,
        title = dialogTitle,
        maxWidth = "500px",
        content = {
            // Form content
            Div {
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
                
                // Status field
                Div {
                    P {
                        Text("Status")
                    }
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("Status text (displayed above object)")
                            value(status)
                            onInput { event -> status = event.value }
                        }
                    )
                }
                
                // Z rotation field
                Div {
                    P {
                        Text("Rotation")
                    }
                    RangeInput(
                        value = rotationYDeg,
                        min = 0,
                        max = 359,
                        step = 1,
                        attrs = {
                            classes(Styles.inputField)
                            onInput { event ->
                                rotationYDeg = event.value?.toFloat()?.coerceIn(0f, 359f) ?: 0f
                            }
                        }
                    )
                    P {
                        Text("${rotationYDeg.toInt()}°")
                    }
                }
                
                // Logic Editor preview (disabled)
                Div(
                    attrs = {
                        classes(Styles.objectEditorDialogLogicPreview)
                        // Make the entire div clickable to open the logic editor

                        if (isEditing) {
                            onClick {
                                // Create a current object with the form data
                                val currentObject = if (isEditing) {
                                    objectModel.copy(
                                        title = title,
                                        file = file,
                                        collection = collection,
                                        status = status
                                    )
                                } else {
                                    Object(
                                        title = title,
                                        file = file,
                                        collection = collection,
                                        worldId = worldId,
                                        data = "{}",
                                        position = models.Position(0f, 0f, 0f),
                                        rotation = Rotation(0f, 0f, 0f),
                                        scale = Scale(1f, 1f, 1f)
                                    )
                                }

                                // Open the logic editor without closing this dialog
                                onOpenLogicEditor(currentObject)
                            }
                        } else {
                            style {
                                opacity(.5)
                            }
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
                        if (isEditing) {
                            if (objectModel?.data != null && objectModel.data != "{}" && objectModel.data.isNotEmpty()) {
                                // Show a preview of the logic data
                                Text(
                                    "Object logic (${
                                        try {
                                            // Parse the JSON data using kotlinx.serialization
                                            val json = utils.json
                                            val jsonObject = json.parseToJsonElement(objectModel.data).jsonObject
                                            val nodes = jsonObject["nodes"]?.jsonObject ?: JsonObject(emptyMap())
                                            val nodeCount = nodes.size
                                            "$nodeCount nodes configured"
                                        } catch (e: Exception) {
                                            "Logic data available"
                                        }
                                    })"
                                )
                            } else {
                                // Show a message for empty logic data
                                Text("Object has no logic yet.")
                            }
                        } else {
                            Text("Create the object to add logic.")
                        }
                    }
                }
            }
        },
        actions = footerActions
    )
}
