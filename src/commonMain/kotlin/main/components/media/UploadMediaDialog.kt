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
import models.MediaType
import models.UserMedia
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.files.File

@Composable
fun UploadMediaDialog(
    onClose: () -> Unit,
    onSelect: (String, MediaType) -> Unit,
    filter: MediaType? = null,
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
                var allItems = MediaApi.list()
                if (filter != null) {
                    allItems = allItems.filter { it.type == filter }
                }
                items = allItems
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
                
                val type = when {
                    file.type.startsWith("audio/") -> MediaType.Audio
                    file.type.startsWith("video/") -> MediaType.Video
                    else -> MediaType.Image
                }
                
                onSelect(finalUrl, type)
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
        title = translated(when (filter) {
            MediaType.Image -> "Upload or choose image"
            MediaType.Audio -> "Upload or choose audio"
            else -> "Upload or choose media"
        }),
        isFullscreen = false,
        maxWidth = "720px",
        isFullHeight = false,
        content = {
            // Top upload area
            MediaUploadArea(
                onUpload = { uploadFile(it) },
                uploading = uploading,
                onError = { error = it },
                filter = filter,
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
                    P { Translated("No media yet. Upload some media above.") }
                } else {
                    items.forEach { item ->
                        val src = imageUrlFor(item.path)
                        Div(attrs = {
                            style {
                                property("border-radius", "6px")
                                height(100.px)
                                property("cursor", "pointer")
                                property("position", "relative")
                                property("background", "#f5f5f5")
                                display(DisplayStyle.Flex)
                                alignItems(AlignItems.Center)
                                justifyContent(JustifyContent.Center)
                                overflow("hidden")
                            }
                            onClick { onSelect(src, item.type); onClose() }
                        }) {
                            if (item.type == models.MediaType.Image) {
                                Img(src = src, attrs = {
                                    style {
                                        width(100.percent)
                                        height(100.percent)
                                        property("object-fit", "cover")
                                        borderRadius(8.px)
                                    }
                                })
                            } else if (item.type == models.MediaType.Audio) {
                                Div({
                                    style {
                                        display(DisplayStyle.Flex)
                                        flexDirection(FlexDirection.Column)
                                        alignItems(AlignItems.Center)
                                        justifyContent(JustifyContent.Center)
                                        width(100.percent)
                                        height(100.percent)
                                        paddingBottom(12.px)
                                    }
                                }) {
                                    Div({
                                        style {
                                            fontSize(24.px)
                                            marginBottom(4.px)
                                        }
                                    }) {
                                        Text("🎵")
                                    }
                                    Audio(attrs = {
                                        attr("src", src)
                                        attr("controls", "true")
                                        style {
                                            width(100.px)
                                            height(24.px)
                                        }
                                        onClick { it.stopPropagation() }
                                    })
                                }
                                Div({
                                    style {
                                        property("position", "absolute")
                                        bottom(4.px)
                                        left(4.px)
                                        right(4.px)
                                        fontSize(10.px)
                                        textAlign("center")
                                        property("text-overflow", "ellipsis")
                                        whiteSpace("nowrap")
                                        overflow("hidden")
                                        color(Color.gray)
                                    }
                                }) {
                                    Text(item.originalFileName)
                                }
                            } else {
                                Text(item.type.name)
                            }

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
                                    it.stopPropagation()
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
