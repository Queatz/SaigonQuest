package main.components.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import main.api.ContentApiConstants
import main.api.MediaApi
import main.api.UserApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.ImageDialog
import main.components.BasicConfirmDialog
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.UserMedia
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.maxHeight
import org.jetbrains.compose.web.css.minHeight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLInputElement
import org.w3c.fetch.RequestInit
import org.w3c.files.File

@Composable
fun MediaLibraryDialog(
    onClose: () -> Unit,
) {
    var items by remember { mutableStateOf(listOf<UserMedia>()) }
    var loading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<UserMedia?>(null) }

    fun imageUrlFor(path: String) = ContentApiConstants.CONTENT_BASE_URL + path

    fun refresh() {
        val scope = MainScope()
        scope.launch {
            loading = true
            error = null
            try {
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
                val form = org.w3c.xhr.FormData()
                form.append("API_USER_BEARER_TOKEN", token)
                form.append("file", file, file.name)

                val url = ContentApiConstants.CONTENT_BASE_URL + "/upload"
                val init = js("({})").unsafeCast<RequestInit>().also {
                    it.method = "POST"
                    it.body = form
                }
                val res = window.fetch(url, init).await()
                val text = res.text().await()
                if (!res.ok) throw RuntimeException(text)
                refresh()
            } catch (t: Throwable) {
                error = t.message ?: "Upload failed"
            } finally {
                uploading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    BaseDialog(
        onClose = onClose,
        title = translated("Media Library"),
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

            if (error != null) {
                Div({ style { color(Color.red); marginBottom(8.px) } }) { Text(error!!) }
            }

            // Library grid
            Div(attrs = {
                style {
                    display(DisplayStyle.Grid)
                    property("grid-template-columns", "repeat(auto-fill, minmax(120px, 1fr))")
                    gap(12.px)
                    property("overflow-y", "auto")
                    minHeight(120.px)
                    maxHeight(480.px)
                }
            }) {
                if (loading) {
                    P { Translated("Loading media...") }
                } else if (items.isEmpty()) {
                    P { Translated("No media yet. Upload an image above.") }
                } else {
                    items.forEach { item ->
                        val src = imageUrlFor(item.path)
                        Div(attrs = {
                            style {
                                property("border-radius", "6px")
                                property("border", "1px solid #ccc")
                                padding(6.px)
                            }
                        }) {
                            Img(src = src, attrs = {
                                style {
                                    width(100.percent)
                                    height(100.px)
                                    property("object-fit", "cover")
                                    property("border-radius", "4px")
                                    marginBottom(6.px)
                                    property("cursor", "pointer")
                                }
                                onClick { previewUrl = src }
                            })

                            // Actions row
                            Div(attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.SpaceBetween)
                                    alignItems(AlignItems.Center)
                                }
                            }) {
                                SmallText(item.originalFileName)
                                Button(attrs = {
                                    classes(Styles.baseDialogButtonDangerPrimary)
                                    onClick {
                                        confirmDeleteFor = item
                                    }
                                }) { Translated("Delete") }
                            }
                        }
                    }
                }
            }

            // Image preview dialog
            if (previewUrl != null) {
                ImageDialog(
                    imageUrl = previewUrl!!,
                    onClose = { previewUrl = null }
                )
            }

            // Confirm delete dialog
            if (confirmDeleteFor != null) {
                BasicConfirmDialog(
                    message = translated("Are you sure you want to delete this media?"),
                    onConfirm = {
                        val item = confirmDeleteFor
                        confirmDeleteFor = null
                        if (item != null) {
                            val scope = MainScope()
                            scope.launch {
                                try {
                                    MediaApi.delete(item.key)
                                    refresh()
                                } catch (e: Throwable) {
                                    error = e.message ?: "Delete failed"
                                }
                            }
                        }
                    },
                    onCancel = { confirmDeleteFor = null },
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}

@Composable
private fun SmallText(text: String) {
    Span(attrs = {
        style {
            fontSize(12.px)
            color(Color("#555"))
            property("overflow", "hidden")
            property("text-overflow", "ellipsis")
            property("white-space", "nowrap")
            width(100.percent)
            marginRight(8.px)
        }
    }) { Text(text) }
}
