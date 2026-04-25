package main.components

import Translations.str
import androidx.compose.runtime.*
import main.components.media.UploadMediaDialog
import models.MediaType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun MediaSelector(
    url: String,
    type: MediaType,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    preferredSize: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(4.px)
        }
    }) {
        if (url.isNotBlank()) {
            if (type == MediaType.Image) {
                FramedBorder(
                    attrs = {
                        style {
                            width(100.percent)
                            boxSizing("border-box")
                            marginBottom(8.px)
                        }
                    }
                ) {
                    Img(src = url, attrs = {
                        style {
                            width(100.percent)
                            display(DisplayStyle.Block)
                            property("object-fit", "contain")
                        }
                    })
                }
            } else {
                Audio(attrs = {
                    attr("src", url)
                    attr("controls", "true")
                    style { width(100.percent); height(32.px); marginBottom(8.px) }
                })
            }
        }

        Div({
            style {
                display(DisplayStyle.Flex)
                gap(4.px)
            }
        }) {
            DialogButton(
                onClick = { showDialog = true },
                style = DialogButtonStyle.Primary
            ) {
                Text(if (url.isBlank()) str("Add ${type.name}") else "✎")
            }
            if (url.isNotBlank()) {
                DialogButton(
                    onClick = onClear,
                    style = DialogButtonStyle.Danger
                ) {
                    Text("✕")
                }
            }
        }

        if (showDialog) {
            UploadMediaDialog(
                onClose = { showDialog = false },
                onSelect = { selectedUrl, _, _ ->
                    onSelect(selectedUrl)
                    showDialog = false
                },
                filter = type,
                preferredSize = preferredSize
            )
        }
    }
}
