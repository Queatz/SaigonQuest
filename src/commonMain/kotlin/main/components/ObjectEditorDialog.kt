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
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Promise

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
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = objectModel != null
    val dialogTitle = if (isEditing) "Edit Object" else "Create Object"
    
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
                categories = categoryList
            )
        } else {
            // Create new object
            Object(
                title = title,
                categories = categoryList,
                data = "{}",
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
                    property("max-width", "600px")
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
            
            // Form content
            Div(
                attrs = {
                    style {
                        property("flex-grow", "1")
                        padding(16.px)
                        property("overflow-y", "auto")
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        property("gap", "16px")
                    }
                }
            ) {
                // Error message
                if (errorMessage.isNotEmpty()) {
                    P(
                        attrs = {
                            style {
                                color(Color.red)
                                margin(0.px)
                            }
                        }
                    ) {
                        Text(errorMessage)
                    }
                }
                
                // Title field
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            property("gap", "4px")
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                color(Color.white)
                                fontSize(14.px)
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
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            property("gap", "4px")
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                color(Color.white)
                                fontSize(14.px)
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
                            placeholder("Enter categories separated by commas")
                        }
                    )
                }
                
                // Logic Editor preview
                Div(
                    attrs = {
                        style {
                            width(100.percent)
                            height(200.px)
                            backgroundColor(rgba(20, 20, 20, 0.5))
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            justifyContent(JustifyContent.Center)
                            alignItems(AlignItems.Center)
                            color(Color.white)
                            property("border", "1px solid rgba(255, 255, 255, 0.3)")
                            property("border-radius", "4px")
                            marginTop(16.px)
                            cursor("pointer")
                            // Add hover effect
                            property("transition", "background-color 0.2s, border-color 0.2s")
                        }
                        // Make the entire div clickable to open the logic editor
                        onClick {
                            // Create a current object with the form data
                            val currentObject = if (isEditing) {
                                objectModel!!.copy(
                                    title = title,
                                    categories = categories.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                )
                            } else {
                                Object(
                                    title = title,
                                    categories = categories.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() },
                                    worldId = worldId,
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
                            style {
                                margin(0.px)
                                marginBottom(8.px)
                                fontSize(16.px)
                                fontWeight("bold")
                            }
                        }
                    ) {
                        Text("Logic Editor")
                    }
                    
                    // Preview of the logic data or a placeholder message
                    Div(
                        attrs = {
                            style {
                                fontSize(14.px)
                                color(rgba(255, 255, 255, 0.7))
                                textAlign("center")
                                padding(0.px, 16.px)
                            }
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
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.FlexEnd)
                        property("gap", "8px")
                        property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
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
                            marginLeft(8.px)
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
