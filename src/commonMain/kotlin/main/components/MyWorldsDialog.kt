package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.WorldApi
import main.theme.Styles
import models.World
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog listing all worlds owned by the current user.
 */
@Composable
fun MyWorldsDialog(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var worlds by remember { mutableStateOf<List<World>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewWorldPrompt by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            worlds = WorldApi.getWorlds()
            isLoading = false
        } catch (e: Throwable) {
            console.error("Error fetching user worlds", e)
            error = e.message ?: "Failed to load worlds"
            isLoading = false
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("My worlds"),
        headerActions = {
            if (worlds.isNotEmpty()) {
                DialogButton(
                    onClick = { showNewWorldPrompt = true },
                    style = DialogButtonStyle.Primary,
                ) { Translated("New world") }
            }
        },
        maxWidth = "560px",
        content = {
            when {
                isLoading -> {
                    Div(attrs = { classes(Styles.chatLoadingText) }) {
                        Translated("Loading...")
                    }
                }
                error != null -> {
                    Div(attrs = { classes(Styles.formErrorMessage) }) { Text(error!!) }
                }
                worlds.isEmpty() -> {
                    // Exciting empty state encouraging the user to create their first world
                    Div(attrs = { classes(Styles.chatEmptyText) }) {
                        // Fun, celebratory emoji row
                        P(attrs = { classes(Styles.objectEditorDialogLogicTitle) }) {
                            Text("🚀✨")
                        }
                        // Headline
                        P(attrs = { classes(Styles.objectEditorDialogLogicTitle) }) {
                            Translated("Start your first world!")
                        }
                        // Primary call-to-action
                        DialogButton(
                            onClick = { showNewWorldPrompt = true },
                            style = DialogButtonStyle.Primary,
                        ) { Translated("New world") }
                    }
                }
                else -> {
                    Div(attrs = { classes(Styles.formGroup) }) {
                        worlds.forEach { world ->
                            DialogButton(
                                onClick = {
                                    // Close dialog then navigate to the selected world's URL
                                    onClose()
                                    val path = if (world.url.isBlank()) "/" else "/${world.url}"
                                    onNavigate(path)
                                },
                                style = DialogButtonStyle.Secondary,
                                attrs = {
                                    classes(
                                        Styles.requestRow,
                                        Styles.clickable,
                                    )
                                }
                            ) {
                                // Thumbnail
                                val thumb = world.screenshot
                                if (!thumb.isNullOrBlank()) {
                                    org.jetbrains.compose.web.dom.Img(
                                        src = thumb,
                                        attrs = {
                                            classes(Styles.imageFullWidth, Styles.clickable)
                                        }
                                    )
                                }

                                // Title
                                Span(attrs = { classes(Styles.objectEditorDialogLogicTitle) }) {
                                    Text(world.title.ifEmpty { translated("Untitled World") })
                                }

                                // Description
                                if (world.description.isNotEmpty()) {
                                    P(attrs = { classes(Styles.balanceSubtext) }) {
                                        Text(world.description)
                                    }
                                }

                                // URL
                                Span(attrs = { classes(Styles.requestUserId) }) {
                                    Text("/${world.url}")
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) {
                Translated("Close")
            }
        }
    )

    val previewUrl = previewImageUrl
    if (previewUrl != null) {
        ImageDialog(
            imageUrl = previewUrl,
            onClose = { previewImageUrl = null },
        )
    }

    if (showNewWorldPrompt) {
        BasicPromptDialog(
            title = translated("New world"),
            message = translated("Enter world URL"),
            defaultValue = "",
            isNumber = false,
            onSubmit = { input ->
                val slug = input.trim().trim('/')
                if (slug.isNotEmpty()) {
                    showNewWorldPrompt = false
                    onClose()
                    onNavigate("/$slug")
                }
            },
            onCancel = { showNewWorldPrompt = false },
        )
    }
}
