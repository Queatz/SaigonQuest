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
        useDefaultContentPadding = false,
        isFullWidth = true,
        isFullHeight = true,
        content = {
            Div({
                style {
                    width(100.percent)
                    height(100.percent)
                    property("background-image", "url(\"$imageUrl\")")
                    property("background-size", "contain")
                    property("background-position", "center")
                    property("background-repeat", "no-repeat")
                    property("border-radius", "8px")
                    cursor("pointer")
                }

                onClick {
                    onClose()
                }
            })
        },
        actions = {
            DialogButton(
                onClick = {
                    if (!copied) {
                        scope.launch {
                            try {
                                val clip = window.navigator.clipboard
                                clip.writeText(imageUrl).await()
                                copied = true
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
                    if (copied) "Copied!" else "Copy image link",
                )
            }

            DialogButton(
                onClick = onClose,
                style = DialogButtonStyle.Primary,
            ) { Translated("Close") }
        }
    )
}
