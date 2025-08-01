package main.components.`object`

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text

/**
 * A dialog version of the Objects Panel for mobile view.
 * 
 * @param onClose Callback when the dialog is closed
 * @param onOpenObjectEditor Callback to open the object editor dialog
 * @param onEditObject Callback to edit a specific object by ID
 * @param objectManager Reference to the ObjectManager for removing objects
 */
@Composable
fun ObjectsDialog(
    onClose: () -> Unit,
    onOpenObjectEditor: () -> Unit,
    onEditObject: (String) -> Unit = {},
    objectManager: ObjectManager? = null
) {
    ObjectsContent(
        onOpenObjectEditor = onOpenObjectEditor,
        onEditObject = onEditObject,
        objectManager = objectManager
    ) { tabContent ->
        // Dialog container
        Div(
            attrs = {
                style {
                    position(Position.Fixed)
                    top(0.px)
                    left(0.px)
                    width(100.percent)
                    height(100.percent)
                    backgroundColor(rgba(0, 0, 0, 0.7))
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.Center)
                    alignItems(AlignItems.Center)
                    // Make dialog interactive
                    property("pointer-events", "auto")
                }
            }
        ) {
            Div(
                attrs = {
                    style {
                        width(90.percent)
                        property("max-width", "600px")
                        height(80.percent)
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
                        Text("Objects")
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
                
                // Tab content
                Div(
                    attrs = {
                        style {
                            property("flex-grow", "1")
                            property("overflow", "hidden")
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                        }
                    }
                ) {
                    tabContent()
                }
            }
        }
    }
}
