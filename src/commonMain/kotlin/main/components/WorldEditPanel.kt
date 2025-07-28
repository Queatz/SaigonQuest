package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.WorldApi
import models.World
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StyleScope
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import kotlin.js.Promise

// Common style for input elements with box-sizing: border-box
private fun StyleScope.inputStyle() {
    width(100.percent)
    padding(8.px)
    backgroundColor(rgba(255, 255, 255, 0.1))
    color(Color.white)
    property("border", "1px solid rgba(255, 255, 255, 0.2)")
    property("border-radius", "4px")
    outline("none")
    property("box-sizing", "border-box")
}

/**
 * A side panel for creating and editing worlds.
 * 
 * @param currentWorld The world being edited, or null if creating a new world
 * @param onClose Callback when the panel is closed
 * @param onSave Callback when a world is saved (created or updated)
 */
@Composable
fun WorldEditPanel(
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
    val panelTitle = if (isEditing) "Edit World" else "Create World"
    
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
    
    // Main panel container
    Div(
        attrs = {
            style {
                position(Position.Absolute)
                top(0.px)
                right(0.px)
                width(320.px)
                height(100.vh)
                backgroundColor(rgba(0, 0, 0, 0.7))
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                // Make panel interactive
                property("pointer-events", "auto")
            }
        }
    ) {
        // Panel header
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
                Text(panelTitle)
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
                            inputStyle()
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
                            inputStyle()
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
                            inputStyle()
                            height(100.px)
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
                            inputStyle()
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
                            inputStyle()
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
