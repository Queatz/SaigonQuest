package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.WorldApi
import models.World
import app.softwork.routingcompose.Router
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog listing all worlds owned by the current user.
 */
@Composable
fun MyWorldsDialog(
    router: Router,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var worlds by remember { mutableStateOf<List<World>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewWorldPrompt by remember { mutableStateOf(false) }


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
        title = translated("My Worlds"),
        headerActions = {
            DialogButton(
                onClick = { showNewWorldPrompt = true },
                isPrimary = true,
            ) { Translated("New world") }
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
                    Div(attrs = { classes(Styles.chatEmptyText) }) {
                        Translated("You don't own any worlds yet")
                    }
                }
                else -> {
                    Div(attrs = { classes(Styles.formGroup) }) {
                        worlds.forEach { world ->
                            Button(
                                attrs = {
                                    classes(
                                        Styles.requestRow,
                                        Styles.clickable,
                                    )
                                    onClick {
                                        // Close dialog then navigate to the selected world's URL
                                        onClose()
                                        val path = if (world.url.isEmpty()) "/" else "/${world.url}"
                                        onNavigate(path)
                                    }
                                }
                            ) {
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
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )

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
                    router.navigate("/$slug")
                }
            },
            onCancel = { showNewWorldPrompt = false },
        )
    }
}
