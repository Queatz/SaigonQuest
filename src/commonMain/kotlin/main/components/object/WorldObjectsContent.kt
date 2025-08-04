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
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Content for the World tab - lists all objects in the world with locate, edit, and remove functionality.
 * 
 * @param objects List of objects to display
 * @param isLoading Whether the objects are currently loading
 * @param errorMessage Error message to display if there was an error loading objects
 * @param onRemove Callback when an object is removed
 * @param onLocate Callback when an object should be located in the 3D view
 * @param onEdit Callback when an object should be edited
 */
@Composable
fun WorldObjectsContent(
    objects: List<Object>,
    isLoading: Boolean,
    errorMessage: String,
    onCreateObject: () -> Unit = {},
    onRemove: (String) -> Unit,
    onLocate: (String) -> Unit = {},
    onEdit: (String) -> Unit = {}
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
            Text("Create Object")
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
                Text("No objects in this world yet.")
            }
        } else {
            // List of objects
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        property("gap", "8px")
                        property("overflow-y", "auto")
                        property("flex-grow", "1")
                    }
                }
            ) {
                // Display objects
                objects.forEach { obj ->
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.SpaceBetween)
                                alignItems(AlignItems.Center)
                                padding(8.px)
                                backgroundColor(rgba(255, 255, 255, 0.1))
                                property("border-radius", "4px")
                            }
                        }
                    ) {
                        // Object title
                        Span(
                            attrs = {
                                style {
                                    color(Color.white)
                                }
                            }
                        ) {
                            Text(obj.title.takeIf { it.isNotEmpty() } ?: "Untitled")
                        }
                        
                        // Action buttons container
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    property("gap", "4px")
                                }
                            }
                        ) {
                            // Locate button
                            Button(
                                attrs = {
                                    style {
                                        backgroundColor(rgba(0, 120, 255, 0.7))
                                        color(Color.white)
                                        property("border", "0")
                                        property("border-radius", "4px")
                                        padding(4.px, 8.px)
                                        cursor("pointer")
                                        fontSize(12.px)
                                    }
                                    onClick { onLocate(obj.key) }
                                }
                            ) {
                                Text("Locate")
                            }
                            
                            // Edit button
                            Button(
                                attrs = {
                                    style {
                                        backgroundColor(rgba(0, 180, 0, 0.7))
                                        color(Color.white)
                                        property("border", "0")
                                        property("border-radius", "4px")
                                        padding(4.px, 8.px)
                                        cursor("pointer")
                                        fontSize(12.px)
                                    }
                                    onClick { onEdit(obj.key) }
                                }
                            ) {
                                Text("Edit")
                            }
                            
                            // Remove button
                            Button(
                                attrs = {
                                    style {
                                        backgroundColor(rgba(255, 0, 0, 0.7))
                                        color(Color.white)
                                        property("border", "0")
                                        property("border-radius", "4px")
                                        padding(4.px, 8.px)
                                        cursor("pointer")
                                        fontSize(12.px)
                                    }
                                    onClick { onRemove(obj.key) }
                                }
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
