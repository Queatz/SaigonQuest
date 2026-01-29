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
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.files.File

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
            // Top upload area
            MediaUploadArea(
                onUpload = { uploadFile(it) },
                uploading = uploading,
                onError = { error = it }
            )

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
