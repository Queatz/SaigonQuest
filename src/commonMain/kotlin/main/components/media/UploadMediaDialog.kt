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
import main.components.DialogButtonStyle
import main.components.BasicInputDialog
import main.components.Translated
import main.components.translated
import main.theme.Styles
import main.theme.Theme
import models.MediaType
import models.UserMedia
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.files.File

@Composable
fun UploadMediaDialog(
    onClose: () -> Unit,
    onSelect: (String, MediaType, String) -> Unit,
    filter: MediaType? = null,
    preferredSize: String? = null
) {
    var items by remember { mutableStateOf(listOf<UserMedia>()) }
    var loading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var renameItem by remember { mutableStateOf<UserMedia?>(null) }
    var showAi by remember { mutableStateOf(false) }

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
                
                onSelect(finalUrl, type, record.originalFileName)
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
            if (filter == MediaType.Image || filter == null) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                        marginBottom(12.px)
                    }
                }) {
                    DialogButton(
                        onClick = { showAi = false },
                        style = if (!showAi) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                        small = true,
                        attrs = { style { flex(1) } }
                    ) { Translated("Upload") }

                    DialogButton(
                        onClick = { showAi = true },
                        style = if (showAi) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                        small = true,
                        attrs = { style { flex(1) } }
                    ) { Translated("AI Image") }
                }
            }

            if (showAi) {
                MediaAiArea(
                    onSelect = onSelect,
                    onError = { error = it },
                    filter = filter,
                    onRefreshLibrary = { refresh() },
                    initialSize = preferredSize,
                )
            } else {
                // Top upload area
                MediaUploadArea(
                    onUpload = { uploadFile(it) },
                    uploading = uploading,
                    onError = { error = it },
                    filter = filter,
                )
            }

            // Error
            if (error != null) {
                Div({ classes(Styles.mediaLibraryErrorMessage) }) { Text(error!!) }
            }

            // Library grid (bounded height, scrollable)
            Div(attrs = {
                classes(Styles.mediaLibraryGrid)
                style {
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
                            classes(Styles.mediaLibraryItem)
                            style {
                                height(100.px)
                            }
                            onClick { onSelect(src, item.type, item.originalFileName); onClose() }
                        }) {
                            if (item.type == MediaType.Image) {
                                Img(src = src, attrs = {
                                    classes(Styles.mediaLibraryItemImage)
                                    style {
                                        height(100.percent)
                                    }
                                })
                            } else if (item.type == MediaType.Audio) {
                                Div({
                                    classes(Styles.mediaLibraryItemAudioContainer)
                                    style {
                                        height(100.percent)
                                    }
                                }) {
                                    Div({
                                        classes(Styles.mediaLibraryItemMusicIcon)
                                    }) {
                                        Text("🎵")
                                    }
                                    Audio(attrs = {
                                        attr("src", src)
                                        attr("controls", "true")
                                        classes(Styles.mediaLibraryItemAudioPlayer)
                                        onClick { it.stopPropagation() }
                                    })
                                }
                                Div({
                                    classes(Styles.mediaLibraryItemFilename)
                                }) {
                                    Text(item.originalFileName)
                                }
                            } else if (item.type == MediaType.Video) {
                                Div({
                                    classes(Styles.mediaLibraryItemAudioContainer)
                                    style {
                                        height(100.percent)
                                    }
                                }) {
                                    Video(attrs = {
                                        attr("src", src)
                                        attr("controls", "true")
                                        classes(Styles.mediaLibraryItemVideoPlayer)
                                        onClick { it.stopPropagation() }
                                    })
                                }
                            } else {
                                Text(item.type.name)
                            }

                            // Favorite toggle overlay
                            Button(attrs = {
                                classes(Styles.mediaLibraryItemFavoriteOverlay)
                                if (item.favorite) {
                                    classes(Styles.mediaLibraryItemFavoriteActive)
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

                            // Rename button
                            if (item.type == MediaType.Audio) {
                                Button(attrs = {
                                    classes(Styles.mediaLibraryItemRenameOverlay)
                                    onClick {
                                        it.stopPropagation()
                                        renameItem = item
                                    }
                                }) {
                                    Text("✏️")
                                }
                            }
                        }
                    }
                }
            }
            // Rename dialog
            if (renameItem != null) {
                BasicInputDialog(
                    title = translated("Rename media"),
                    initialValue = renameItem!!.originalFileName,
                    onConfirm = { newName ->
                        val item = renameItem!!
                        renameItem = null
                        val scope = MainScope()
                        scope.launch {
                            try {
                                MediaApi.rename(item.key, newName)
                                refresh()
                            } catch (e: Throwable) {
                                error = e.message ?: "Rename failed"
                            }
                        }
                    },
                    onCancel = { renameItem = null },
                )
            }
        },
        actions = { DialogButton(onClick = onClose) { Translated("Close") } }
    )
}
