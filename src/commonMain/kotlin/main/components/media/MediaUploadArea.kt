package main.components.media

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import main.theme.Styles
import models.MediaType
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
    filter: MediaType? = null,
) {
    var dragOver by remember { mutableStateOf(false) }
    var inputElement by remember { mutableStateOf<HTMLInputElement?>(null) }
    val scope = rememberCoroutineScope()

    Div(attrs = {
        classes(Styles.mediaUploadArea)
        if (dragOver) classes(Styles.mediaUploadAreaActive)
        style {
            if (uploading) property("cursor", "progress")
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
            val file = findMediaInClipboardEvent(event)
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
                classes(Styles.mediaUploadAreaContent)
            }
        ) {
            Translated(if (uploading) "Uploading..." else when (filter) {
                MediaType.Image -> "Click or drop image to upload"
                MediaType.Audio -> "Click or drop audio to upload"
                else -> "Click or drop media to upload"
            })
            if (!uploading) {
                val recorderState = rememberAudioRecorderState(
                    onUpload = { onUpload(it) },
                    onError = { onError(it) }
                )

                if (recorderState.isRecording) {
                    AudioRecorderUI(
                        state = recorderState,
                        isFullWidth = true,
                        finishButtonText = "Done"
                    )
                } else {
                    Div(attrs = {
                        classes(Styles.mediaUploadAreaActions)
                    }) {
                        DialogButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val file = readMediaFromClipboard()
                                        if (file != null) {
                                            onUpload(file)
                                        }
                                    } catch (t: Throwable) {
                                        onError(t.message ?: "Paste failed")
                                    }
                                }
                            },
                            style = DialogButtonStyle.Secondary,
                            attrs = {
                                classes(Styles.mediaRecorderButton)
                                onClick { ev -> ev.stopPropagation() }
                            }
                        ) { Translated("Paste") }

                        if (filter == MediaType.Audio) {
                            AudioRecorder(
                                onUpload = { onUpload(it) },
                                onError = { onError(it) },
                                state = recorderState,
                                finishButtonText = "Done"
                            )
                        }
                    }
                }
            }
        }
        // Hidden file input
        Input(type = InputType.File, attrs = {
            val acceptTypes = when (filter) {
                MediaType.Image -> "image/*"
                MediaType.Audio -> "audio/*"
                else -> "image/*,audio/*"
            }
            attr("accept", acceptTypes)
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
