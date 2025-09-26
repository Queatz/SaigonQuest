package main.components.media

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import main.api.ContentApiConstants
import main.api.UserApi
import main.api.MediaApi
import main.api.ContentApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.UserMedia
import models.MediaType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLInputElement
import org.w3c.fetch.RequestInit
import org.w3c.files.File
import utils.json

@Composable
fun UploadMediaDialog(
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var items by remember { mutableStateOf(listOf<UserMedia>()) }
    var loading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun imageUrlFor(path: String) = ContentApiConstants.CONTENT_BASE_URL + path

    fun refresh() {
        val scope = MainScope()
        scope.launch {
            loading = true
            error = null
            try {
                // Ensure we have a valid user/token (visitor or player)
                UserApi.getCurrentUser()
                items = MediaApi.list()
            } catch (t: Throwable) {
                error = t.message ?: "Failed to load media"
            } finally {
                loading = false
            }
        }
    }

    fun uploadFile(file: File) {
        val scope = MainScope()
        scope.launch {
            uploading = true
            error = null
            try {
                val token = UserApi.getToken() ?: ""
                val record = ContentApi.upload(file, token)
                val finalUrl = ContentApi.imageUrlFor(record.uploadedFilePath)
                onSelect(finalUrl)
                onClose()
            } catch (t: Throwable) {
                t.printStackTrace()
                error = t.message ?: "Upload failed"
            } finally {
                uploading = false
                refresh()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    BaseDialog(
        onClose = onClose,
        title = translated("Upload or choose image"),
        isFullscreen = false,
        maxWidth = "720px",
        isFullHeight = false,
        content = {
            // Top drop/click area
            var dragOver by remember { mutableStateOf(false) }
            var inputElement by remember { mutableStateOf<HTMLInputElement?>(null) }

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
                    if (first != null) uploadFile(first)
                }
                onPaste { event ->
                    try {
                        val dt = event.clipboardData
                        val files = dt?.files
                        val length = files?.length ?: 0
                        for (i in 0 until length) {
                            val f = files?.item(i)
                            if (f != null && f.type.startsWith("image/")) {
                                event.preventDefault()
                                uploadFile(f)
                                break
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }

                ref {
                    it.focus()

                    onDispose {

                    }
                }
            }) {
                P { Translated(if (uploading) "Uploading..." else "Click or drop image to upload") }
                // Hidden file input
                Input(type = InputType.File, attrs = {
                    attr("accept", "image/*")
                    style {
                        display(DisplayStyle.None)
                    }
                    onInput { ev ->
                        val input = ev.target
                        val f = input.files?.item(0)
                        if (f != null) uploadFile(f)
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

            // Error
            if (error != null) {
                Div({ style { color(Color.red); marginBottom(8.px) } }) { Text(error!!) }
            }

            // Library grid (bounded height, scrollable)
            Div(attrs = {
                style {
                    display(DisplayStyle.Grid)
                    property("grid-template-columns", "repeat(auto-fill, minmax(120px, 1fr))")
                    gap(12.px)
                    property("overflow-y", "auto")
                    minHeight(120.px)
                    maxHeight(360.px)
                }
            }) {
                if (loading) {
                    P { Translated("Loading media...") }
                } else if (items.isEmpty()) {
                    P { Translated("No media yet. Upload an image above.") }
                } else {
                    items.forEach { item ->
                        // Only show images for now
                        val src = imageUrlFor(item.path)
                        Div(attrs = {
                            style {
                                property("border-radius", "6px")
                                height(100.px)
                                property("cursor", "pointer")
                                property("position", "relative")
                            }
                        }) {
                            Img(src = src, attrs = {
                                style {
                                    width(100.percent)
                                    height(100.percent)
                                    property("object-fit", "cover")
                                    borderRadius(8.px)
                                }
                                onClick { onSelect(src); onClose() }
                            })

                            // Favorite toggle overlay
                            Button(attrs = {
                                style {
                                    property("position", "absolute")
                                    property("top", "0")
                                    property("right", "0")
                                    property("background", "transparent")
                                    property("border", "none")
                                    property("cursor", "pointer")
                                    width(24.px)
                                    height(24.px)
                                    fontSize(18.px)
                                    if (item.favorite) {
                                        color(Color("#ffee00"))
                                    }
                                }
                                onClick {
                                    val scope = MainScope()
                                    scope.launch {
                                        try {
                                            if (item.favorite) {
                                                MediaApi.unfavorite(item.key)
                                            } else {
                                                MediaApi.favorite(item.key)
                                            }
                                            refresh()
                                        } catch (t: Throwable) {
                                            error = t.message ?: "Failed to update favorite"
                                        }
                                    }
                                }
                            }) {
                                Text(if (item.favorite) "★" else "☆")
                            }
                        }
                    }
                }
            }
        },
        actions = { DialogButton(onClick = onClose) { Translated("Close") } }
    )
}
