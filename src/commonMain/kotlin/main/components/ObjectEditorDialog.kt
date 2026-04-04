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
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogInput
import main.theme.Styles
import models.AssetType
import models.Object
import models.Rotation
import models.Scale
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flex
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.RangeInput
import org.jetbrains.compose.web.dom.Text
import web.cssom.PropertyName.Companion.display
import web.cssom.PropertyName.Companion.flexDirection
import web.cssom.PropertyName.Companion.gap
import web.cssom.PropertyName.Companion.justifyContent
import web.cssom.atrule.displayMode

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
    var verticalOffset by remember { mutableStateOf(objectModel?.verticalOffset ?: 0f) }
    var rotationYDeg by remember {
        mutableStateOf(
            ((objectModel?.rotation?.y ?: 0f) * 180f / kotlin.math.PI.toFloat()).let { deg ->
                // normalize to [0, 359]
                val n = ((deg % 360f) + 360f) % 360f
                n
            }
        )

    }
    // Asset browser dialog toggle
    var showCollectionBrowser by remember { mutableStateOf(false) }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showConfirmRemove by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = objectModel != null
    val dialogTitle = if (isEditing) "Edit object" else "Create object"
    
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
                verticalOffset = verticalOffset,
            )
        } else {
            // Create new object at the user's selected position if available; otherwise, fall back to player's current position
            val selectedPos = SceneManager.getObjectManager()?.consumePendingCreatePosition()
            val playerPos = SceneManager.getPlayerController()?.getPlayerPosition()
            val initialPosition = when {
                selectedPos != null -> models.Position(
                    x = selectedPos.x,
                    y = selectedPos.y,
                    z = selectedPos.z,
                )
                playerPos != null -> models.Position(
                    x = playerPos.x,
                    y = playerPos.y,
                    z = playerPos.z,
                )
                else -> models.Position(0f, 0f, 0f)
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
                status = status,
                verticalOffset = verticalOffset
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
        // Remove button (only when editing)
        if (isEditing) {
                    DialogButton(
                        onClick = {
                            if (!isSubmitting && !isDeleting) {
                                showConfirmRemove = true
                            }
                        },
                        style = DialogButtonStyle.Danger
                    ) {
                        Text(if (isDeleting) "Removing..." else "Remove")
                    }
        }
        
        // Spacer could be handled by CSS; buttons will align according to Styles
        
        // Cancel button
        DialogButton(
            onClick = { if (!isSubmitting && !isDeleting) onClose() },
            style = DialogButtonStyle.Secondary,
            isDisabled = isSubmitting || isDeleting
        ) {
            Text("Cancel")
        }
        
        // Save button
        DialogButton(
            onClick = { if (!isSubmitting && !isDeleting) handleSubmit() },
            style = DialogButtonStyle.Primary,
            isDisabled = isSubmitting || isDeleting
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
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                    }
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
                DialogInput(
                    label = "Title",
                    value = title,
                    onInput = { title = it },
                    placeholder = "Object title",
                    containerAttrs = {
                        style {
                            marginTop(16.px)
                        }
                    }
                )
                
                // Asset field
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                        }
                    }
                ) {
                    Div(
                        attrs = {
                            style {
                                marginTop(16.px)
                            }
                        }
                    ) {
                        Translated("Asset")
                    }
                    Div {
                        DialogButton(
                            onClick = { showCollectionBrowser = true },
                            style = DialogButtonStyle.Primary,
                            attrs = {
                                style {
                                    marginBottom(8.px)
                                }
                            }
                        ) {
                            Translated("Browse")
                        }
                    }
                }

                // Preview the selected asset
                if (file.isNotEmpty() && collection.isNotEmpty()) {
                    Div(attrs = {
                    }) {
                        BasicSceneRenderer(
                            filename = file,
                            collection = collection,
                            transparentBackground = true
                        )
                    }
                }

                // Status field
                DialogInput(
                    label = "Status",
                    value = status,
                    onInput = { status = it },
                    placeholder = "Text displayed above object",
                    containerAttrs = {
                        style {
                            marginTop(16.px)
                        }
                    }
                )
                
                // Z rotation field
                Div {
                    Div(
                        attrs = {
                            style {
                                marginTop(16.px)
                            }
                        }
                    ) {
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
                    // Quick set buttons for rotation
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                gap(4.px)
                                justifyContent(JustifyContent.Stretch)
                            }
                        }
                    ) {
                        listOf(0, 90, 180, 270).forEach { deg ->
                            Button(
                                attrs = {
                                    classes(Styles.secondaryButton)
                                    style {
                                        flex(1)
                                    }
                                    onClick { rotationYDeg = deg.toFloat() }
                                }
                            ) {
                                Text("$deg°")
                            }
                        }
                    }
                    Div(
                        attrs = {
                            style {
                                marginTop(8.px)
                            }
                        }
                    ) {
                        Text("${rotationYDeg.toInt()}°")
                    }
                }

                // Vertical offset field
                Div {
                    Div(
                        attrs = {
                            style {
                                marginTop(16.px)
                            }
                        }
                    ) {
                        Translated("Vertical Offset")
                    }
                    RangeInput(
                        value = verticalOffset,
                        min = 0,
                        max = 10,
                        step = .1f,
                        attrs = {
                            classes(Styles.inputField)
                            onInput { event ->
                                verticalOffset = event.value?.toFloat()?.coerceIn(0f, 10f) ?: 0f
                            }
                        }
                    )
                    Div(
                        attrs = {
                            style {
                                marginTop(8.px)
                            }
                        }
                    ) {
                        Text("${verticalOffset}")
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
                                        status = status,
                                        verticalOffset = verticalOffset
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
                            if (objectModel.data != "{}" && objectModel.data.isNotEmpty()) {
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
                                        } catch (e: Throwable) {
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

    // Asset browsers
    if (showCollectionBrowser) {
        AssetBrowserDialog(
            type = AssetType.Objects,
            onSelect = { r ->
                file = r.asset!!.file
                collection = r.collection ?: ""
            },
            onClose = { showCollectionBrowser = false },
        )
    }

    // Confirm remove dialog
    if (showConfirmRemove && isEditing) {
        BasicConfirmDialog(
            message = translated("Are you sure you want to remove this object?"),
            onConfirm = {
                if (!isSubmitting && !isDeleting) {
                    showConfirmRemove = false
                    isDeleting = true
                    errorMessage = ""
                    coroutineScope.launch {
                        try {
                            ObjectApi.deleteObject(objectModel!!.key)
                            isDeleting = false
                            onClose()
                        } catch (error: Throwable) {
                            isDeleting = false
                            errorMessage = error.message ?: "An error occurred"
                        }
                    }
                }
            },
            onCancel = { showConfirmRemove = false },
        )
    }
}
