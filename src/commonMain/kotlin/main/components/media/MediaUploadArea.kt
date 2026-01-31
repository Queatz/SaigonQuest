package main.components.media

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.components.Translated
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
fun MediaUploadArea(
    onUpload: (File) -> Unit,
    uploading: Boolean,
    onError: (String) -> Unit = {},
) {
    var dragOver by remember { mutableStateOf(false) }
    var inputElement by remember { mutableStateOf<HTMLInputElement?>(null) }
    val scope = rememberCoroutineScope()

    Div(attrs = {
        style {
            property("border", if (dragOver) "2px dashed #4A90E2" else "2px dashed #aaa")
            property("border-radius", "8px")
            padding(24.px)
            textAlign("center")
            property("cursor", if (uploading) "progress" else "pointer")
            marginBottom(16.px)
        }
        onClick {
            if (!uploading) {
                inputElement?.click()
            }
        }
        onDragOver { ev ->
            ev.preventDefault(); dragOver = true
        }
        onDragLeave { dragOver = false }
        onDrop { event ->
            event.preventDefault(); dragOver = false
            val files = (event.nativeEvent as DragEvent).dataTransfer?.files
            val first = files?.item(0)
            if (first != null) onUpload(first)
        }
        onPaste { event ->
            val file = findImageInClipboardEvent(event)
            if (file != null) {
                event.preventDefault()
                onUpload(file)
            }
        }

        ref {
            it.focus()
            onDispose { }
        }
    }) {
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    justifyContent(JustifyContent.Center)
                    flexDirection(FlexDirection.Column)
                    gap(16.px)
                }
            }
        ) {
            Translated(if (uploading) "Uploading..." else "Click or drop image to upload")
            if (!uploading) {
                Button(attrs = {
                    classes(Styles.baseDialogButtonSecondary)
                    style {
                        padding(4.px, 8.px)
                        fontSize(14.px)
                    }
                    onClick { ev ->
                        ev.stopPropagation()
                        scope.launch {
                            try {
                                val file = readImageFromClipboard()
                                if (file != null) {
                                    onUpload(file)
                                }
                            } catch (t: Throwable) {
                                onError(t.message ?: "Paste failed")
                            }
                        }
                    }
                }) { Translated("Paste") }
            }
        }
        // Hidden file input
        Input(type = InputType.File, attrs = {
            attr("accept", "image/*")
            style {
                display(DisplayStyle.None)
            }
            onInput { ev ->
                val input = ev.target
                val f = input.files?.item(0)
                if (f != null) onUpload(f)
                input.value = ""
            }
            ref {
                inputElement = it
                onDispose {
                    inputElement = null
                }
            }
        })
    }
}
