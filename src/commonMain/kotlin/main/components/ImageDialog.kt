package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img

@Composable
fun ImageDialog(
    imageUrl: String,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    BaseDialog(
        onClose = onClose,
        title = null,
        showCloseButton = false,
        maxWidth = "1200px",
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
                            property("max-width", "100%")
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
            DialogButton(
                onClick = {
                    if (!copied) {
                        scope.launch {
                            try {
                                val clip = window.navigator.clipboard
                                if (clip != null) {
                                    clip.writeText(imageUrl).await()
                                    copied = true
                                } else {
                                    console.error("Clipboard API not available in this context")
                                }
                            } catch (t: Throwable) {
                                console.error("Failed to copy image URL", t)
                            }
                        }
                    }
                },
                style = DialogButtonStyle.Secondary,
                isDisabled = copied,
            ) {
                Translated(
                    if (copied) "Copied!" else "Copy link",
                )
            }

            DialogButton(
                onClick = onClose,
                style = DialogButtonStyle.Primary,
            ) { Translated("Close") }
        }
    )
}
