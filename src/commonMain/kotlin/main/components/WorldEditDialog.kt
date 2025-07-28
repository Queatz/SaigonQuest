package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.WorldApi
import models.World
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import kotlin.js.Promise

/**
 * A dialog for creating and editing worlds on mobile.
 * 
 * @param currentWorld The world being edited, or null if creating a new world
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when a world is saved (created or updated)
 */
@Composable
fun WorldEditDialog(
    currentWorld: World? = null,
    onClose: () -> Unit,
    onSave: (World) -> Unit
) {
    // State for form fields
    var title by remember { mutableStateOf(currentWorld?.title ?: "") }
    var subtitle by remember { mutableStateOf(currentWorld?.subtitle ?: "") }
    var description by remember { mutableStateOf(currentWorld?.description ?: "") }
    var url by remember { mutableStateOf(currentWorld?.url ?: "") }
    var file by remember { mutableStateOf(currentWorld?.file ?: "") }
    
    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Determine if we're editing or creating
    val isEditing = currentWorld != null
    val dialogTitle = if (isEditing) "Edit World" else "Create World"
    
    // Function to handle form submission
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""
        
        val worldModel = if (isEditing) {
            // Update existing world
            currentWorld!!.copy(
                title = title,
                subtitle = subtitle,
                description = description,
                url = url,
                file = file
            )
        } else {
            // Create new world
            World(
                title = title,
                subtitle = subtitle,
                description = description,
                url = url,
                file = file
            )
        }
        
        val promise = if (isEditing) {
            WorldApi.updateWorld(currentWorld!!.key, worldModel)
        } else {
            WorldApi.createWorld(worldModel)
        }
        
        promise
            .then { savedWorld ->
                isSubmitting = false
                onSave(savedWorld)
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
            }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                style {
                    width(90.percent)
                    property("max-width", "400px")
                    height(80.vh)
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
                
                // Create world button (only shown when not editing)
                if (!isEditing) {
                    Button(
                        attrs = {
                            style {
                                padding(12.px, 16.px)
                                backgroundColor(rgba(0, 120, 255, 0.7))
                                color(Color.white)
                                property("border", "0")
                                property("border-radius", "4px")
                                cursor("pointer")
                                fontSize(16.px)
                                fontWeight("bold")
                                width(100.percent)
                                marginBottom(16.px)
                            }
                        }
                    ) {
                        Text("Create World")
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
                            placeholder("Enter world title")
                        }
                    )
                }
                
                // Subtitle field
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
                        Text("Subtitle")
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
                            value(subtitle)
                            onInput { event -> subtitle = event.value }
                            placeholder("Enter world subtitle")
                        }
                    )
                }
                
                // Description field
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
                        Text("Description")
                    }
                    
                    TextArea(
                        attrs = {
                            style {
                                width(100.percent)
                                height(80.px)
                                padding(8.px)
                                backgroundColor(rgba(255, 255, 255, 0.1))
                                color(Color.white)
                                property("border", "1px solid rgba(255, 255, 255, 0.2)")
                                property("border-radius", "4px")
                                outline("none")
                                property("resize", "vertical")
                            }
                            value(description)
                            onInput { event -> description = event.value }
                            placeholder("Enter world description")
                        }
                    )
                }
                
                // URL field
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
                        Text("URL (optional)")
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
                            value(url)
                            onInput { event -> url = event.value }
                            placeholder("Enter external URL")
                        }
                    )
                }
                
                // File field
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
                        Text("File")
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
                            value(file)
                            onInput { event -> file = event.value }
                            placeholder("Enter world file name (without .glb)")
                        }
                    )
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
                            flexGrow(1)
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
                            flexGrow(1)
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
