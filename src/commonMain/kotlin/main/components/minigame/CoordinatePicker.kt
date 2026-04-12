package main.components.minigame

import androidx.compose.runtime.*
import main.components.BaseDialog
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.FramedBorder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.MouseEvent
import Translations.str
import main.components.Translated
import main.components.translated
import org.jetbrains.compose.web.attributes.InputType

@Composable
fun CoordinatePickerDialog(
    imageUrl: String,
    initialX: Double,
    initialY: Double,
    initialRadius: Double,
    onConfirm: (Double, Double, Double) -> Unit,
    onClose: () -> Unit
) {
    var x by remember { mutableStateOf(initialX) }
    var y by remember { mutableStateOf(initialY) }
    var radius by remember { mutableStateOf(initialRadius) }

    BaseDialog(
        onClose = onClose,
        title = str("Select position"),
        showCloseButton = true,
        content = {
            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(16.px)
                    alignItems(AlignItems.Center)
                }
            }) {
                FramedBorder(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Text(str("Click on the image to set the position"))
                }

                FramedBorder(
                    attrs = {
                        style {
                            position(Position.Relative)
                            width(100.percent)
                            maxWidth(600.px)
                            cursor("crosshair")
                        }
                    }
                ) {
                    Img(src = imageUrl, attrs = {
                        style {
                            width(100.percent)
                            display(DisplayStyle.Block)
                        }
                        onClick { event ->
                            val rect = (event.currentTarget as? org.w3c.dom.Element)?.getBoundingClientRect() ?: return@onClick
                            x = (event.clientX - rect.left) / rect.width
                            y = (event.clientY - rect.top) / rect.height
                        }
                    })

                    // Hotspot indicator
                    Div({
                        style {
                            position(Position.Absolute)
                            left((x * 100).percent)
                            top((y * 100).percent)
                            width((radius * 2 * 100).percent)
                            property("aspect-ratio", "1 / 1")
                            property("transform", "translate(-50%, -50%)")
                            borderRadius(50.percent)
                            border(2.px, LineStyle.Solid, Color("red"))
                            backgroundColor(rgba(255, 0, 0, 0.3))
                            property("pointer-events", "none")
                        }
                    })
                    
                    // Center point
                    Div({
                        style {
                            position(Position.Absolute)
                            left((x * 100).percent)
                            top((y * 100).percent)
                            width(4.px)
                            height(4.px)
                            property("transform", "translate(-50%, -50%)")
                            borderRadius(50.percent)
                            backgroundColor(Color("white"))
                            property("pointer-events", "none")
                        }
                    })
                }

                Div({
                    style {
                        width(100.percent)
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(8.px)
                    }
                }) {
                    Label { Text("${translated("Radius")}: ${radius.toString().take(4)}") }
                    org.jetbrains.compose.web.dom.Input(
                        type = InputType.Range,
                        attrs = {
                            style { width(100.percent) }
                            attr("min", "0.01")
                            attr("max", "0.5")
                            attr("step", "0.01")
                            value(radius.toString())
                            onInput { event ->
                                radius = event.value.toString().toDoubleOrNull() ?: radius
                            }
                        }
                    )
                }

                Div({
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                    }
                }) {
                    DialogButton(
                        onClick = { onConfirm(x, y, radius) },
                        style = DialogButtonStyle.Primary
                    ) { Translated("Save position") }

                    DialogButton(
                        onClick = onClose,
                        style = DialogButtonStyle.Secondary
                    ) { Translated("Cancel") }
                }
            }
        }
    )
}

private fun StyleScope.heroImageUrlStyle(url: String) {
    property("background-image", "url('$url')")
    property("background-size", "contain")
    property("background-repeat", "no-repeat")
    property("background-position", "center")
}
