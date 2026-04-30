package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img

@Composable
fun ScreenshotDialog(
    imageDataUrl: String,
    isSending: Boolean,
    onSendToChat: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
) {
    BaseDialog(
        onClose = onClose,
        title = null,
        showCloseButton = true,
        maxWidth = "900px",
        content = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        property("gap", "12px")
                    }
                }
            ) {
                Img(
                    src = imageDataUrl,
                    attrs = {
                        style {
                            width(100.percent)
                            property("max-width", "860px")
                            property("object-fit", "contain")
                            property("border-radius", "8px")
                        }
                    }
                )
            }
        },
        actions = {
            DialogButton(
                onClick = onDownload,
            ) {
                Translated("Download")
            }
            DialogButton(
                onClick = onSendToChat,
                style = DialogButtonStyle.Primary,
                isDisabled = isSending,
                isLoading = isSending
            ) {
                Translated("Send to chat")
            }
        }
    )
}
