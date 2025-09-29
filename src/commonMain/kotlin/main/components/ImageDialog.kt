package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img

@Composable
fun ImageDialog(
    imageUrl: String,
    onClose: () -> Unit,
) {
    BaseDialog(
        onClose = onClose,
        title = null,
        maxWidth = "900px",
        content = {
            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    alignItems(AlignItems.Center)
                    property("gap", "12px")
                }
            }) {
                Img(
                    src = imageUrl,
                    attrs = {
                        style {
                            width(100.percent)
                            property("max-width", "860px")
                            property("object-fit", "contain")
                            property("border-radius", "8px")
                        }

                        onClick {
                            onClose()
                        }
                    }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}
