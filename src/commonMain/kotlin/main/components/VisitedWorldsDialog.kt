package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import main.api.ChatApi
import main.theme.Styles
import models.VisitedWorld
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.utils.TimeUtils

@Composable
fun VisitedWorldsDialog(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var items by remember { mutableStateOf<List<VisitedWorld>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Track confirmation dialog state
    var confirmWorldId by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            items = ChatApi.listVisitedWorlds().toList()
            isLoading = false
        } catch (t: Throwable) {
            console.error("Failed to load visited worlds", t)
            error = t.message ?: "An error occurred"
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    BaseDialog(
        onClose = onClose,
        title = translated("Visited Worlds"),
        maxWidth = "560px",
        content = {
            when {
                isLoading -> {
                    Div(attrs = { classes(Styles.chatLoadingText) }) { Translated("Loading...") }
                }
                error != null -> {
                    Div(attrs = { classes(Styles.formErrorMessage) }) { Text(error!!) }
                }
                items.isEmpty() -> {
                    Div(attrs = { classes(Styles.chatEmptyText) }) {
                        P(attrs = { classes(Styles.objectEditorDialogLogicTitle) }) {
                            Translated("No visited worlds yet")
                        }
                    }
                }
                else -> {
                    Div(attrs = { classes(Styles.formGroup) }) {
                        items.forEach { vw ->
                            Div(attrs = { classes(Styles.requestRow) }) {
                                // Clickable area to navigate
                                Div(attrs = {
                                    classes(Styles.clickable)
                                    onClick {
                                        onClose()
                                        val path = if (vw.world.url.isEmpty()) "/" else "/${vw.world.url}"
                                        onNavigate(path)
                                    }
                                }) {
                                    // Thumbnail
                                    val thumb = vw.world.screenshot
                                    if (!thumb.isNullOrBlank()) {
                                        org.jetbrains.compose.web.dom.Img(
                                            src = thumb,
                                            attrs = {
                                                classes(Styles.imageFullWidth, Styles.clickable)
                                                onClick { ev ->
                                                    ev.stopPropagation()
                                                    previewImageUrl = thumb
                                                }
                                            }
                                        )
                                    }

                                    // Title
                                    Span(attrs = { classes(Styles.objectEditorDialogLogicTitle) }) {
                                        Text(vw.world.title.ifEmpty { translated("Untitled World") })
                                    }
                                    // Description
                                    if (vw.world.description.isNotEmpty()) {
                                        P(attrs = { classes(Styles.balanceSubtext) }) {
                                            Text(vw.world.description)
                                        }
                                    }
                                    // URL
                                    Span(attrs = { classes(Styles.requestUserId) }) {
                                        Text("/" + vw.world.url)
                                    }
                                    // Subscription state
                                    P(attrs = { classes(Styles.balanceSubtext) }) {
                                        Text(if (vw.subscribed) translated("Subscribed") else translated("Not subscribed"))
                                    }
                                    // Last visited (pretty)
                                    P(attrs = { classes(Styles.balanceSubtext) }) {
                                        Translated("Last visited")
                                        Text(": ")
                                        Text(TimeUtils.formatTimestamp(vw.lastVisited))
                                    }
                                }

                                // Remove visited world
                                Button(
                                    attrs = {
                                        classes(Styles.secondaryButton)
                                        style { marginTop(4.px) }
                                        onClick { event ->
                                            try {
                                                event.stopPropagation()
                                                val worldId = vw.world.key
                                                if (worldId.isNotEmpty()) {
                                                    confirmWorldId = worldId
                                                }
                                            } catch (t: Throwable) {
                                                console.error("Failed to initiate remove visited world", t)
                                            }
                                        }
                                    }
                                ) { Translated("Remove") }
                            }
                        }
                    }
                }
            }

            // Confirmation dialog for removal
            val worldIdToRemove = confirmWorldId
            if (worldIdToRemove != null) {
                BasicConfirmDialog(
                    title = translated("Remove visited world"),
                    message = translated("Are you sure you want to remove this visited world?"),
                    onCancel = { confirmWorldId = null },
                    onConfirm = {
                        // Optimistic update
                        items = items.filter { it.world.key != worldIdToRemove }
                        // Fire and forget API call
                        scope.launch {
                            val ok = ChatApi.deleteVisitedWorld(worldIdToRemove)
                            if (!ok) {
                                // Revert on failure
                                load()
                            }
                        }
                        confirmWorldId = null
                    },
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )

    val previewUrl = previewImageUrl
    if (previewUrl != null) {
        ImageDialog(
            imageUrl = previewUrl,
            onClose = { previewImageUrl = null },
        )
    }
}
