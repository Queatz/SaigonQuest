package main.components.`object`

import androidx.compose.runtime.Composable
import main.theme.Theme
import models.Object
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Content for the Owned tab - lists objects created by the user with create button.
 * 
 * @param objects List of objects to display
 * @param isLoading Whether the objects are currently loading
 * @param errorMessage Error message to display if there was an error loading objects
 * @param onCreateObject Callback when the create object button is clicked
 * @param onDragStart Callback when an object drag starts
 */
@Composable
fun OwnedTabContent(
    objects: List<Object>,
    isLoading: Boolean,
    errorMessage: String,
    onCreateObject: () -> Unit,
    onDragStart: (dynamic, Object) -> Unit
) {
    // Container for the content
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                height(100.percent)
                padding(16.px)
            }
        }
    ) {
        // Create object button
        Button(
            attrs = {
                style {
                    backgroundColor(Theme.colors.primary)
                    color(Theme.colors.onPrimary)
                    property("border", "0")
                    property("border-radius", "4px")
                    padding(8.px, 16.px)
                    marginRight(8.px)
                    cursor("pointer")
                    fontSize(14.px)
                    margin(0.px, 0.px, 16.px, 0.px)
                }
                onClick { onCreateObject() }
            }
        ) {
            Text("+ Create Object")
        }

        // Loading or error message
        if (isLoading) {
            P(
                attrs = {
                    style {
                        color(Color.white)
                        property("text-align", "center")
                    }
                }
            ) {
                Text("Loading objects...")
            }
        } else if (errorMessage.isNotEmpty()) {
            P(
                attrs = {
                    style {
                        color(Color.red)
                        property("text-align", "center")
                    }
                }
            ) {
                Text(errorMessage)
            }
        } else if (objects.isEmpty()) {
            P(
                attrs = {
                    style {
                        color(rgba(255, 255, 255, 0.7))
                        property("text-align", "center")
                    }
                }
            ) {
                Text("You don't have any objects yet. Click 'Create Object' to create one.")
            }
        } else {
            // Grid of objects
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Grid)
                        property("grid-template-columns", "repeat(auto-fill, minmax(80px, 1fr))")
                        property("gap", "12px")
                        property("overflow-y", "auto")
                        property("flex-grow", "1")
                        property("align-content", "flex-start")
                    }
                }
            ) {
                // Display objects
                objects.forEach { obj ->
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                alignItems(AlignItems.Center)
                                property("gap", "4px")
                            }
                        }
                    ) {
                        // Object box (draggable)
                        Div(
                            attrs = {
                                style {
                                    width(64.px)
                                    height(64.px)
                                    backgroundColor(rgba(0, 120, 255, 0.3))
                                    property("border", "1px solid rgba(0, 120, 255, 0.7)")
                                    property("border-radius", "4px")
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.Center)
                                    alignItems(AlignItems.Center)
                                    cursor("grab")
                                    // Prevent text selection during drag
                                    property("user-select", "none")
                                    // Make box draggable
                                    property("draggable", "true")
                                }
                                // Set up drag events
                                onDragStart { event ->
                                    // Prevent default behavior to avoid text selection
                                    event.preventDefault()
                                    onDragStart(event.nativeEvent, obj)
                                }
                            }
                        ) {
                            // Display a cube icon
                            Span {
                                Text("□")
                            }
                        }
                        
                        // Object title
                        Span(
                            attrs = {
                                style {
                                    color(Color.white)
                                    fontSize(12.px)
                                    property("text-align", "center")
                                    property("word-break", "break-word")
                                    property("max-width", "80px")
                                }
                            }
                        ) {
                            Text(obj.title.takeIf { it.isNotEmpty() } ?: "Untitled")
                        }
                    }
                }
            }
        }
    }
}
