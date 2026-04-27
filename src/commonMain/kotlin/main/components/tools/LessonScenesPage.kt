package main.components.tools

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import main.api.LessonApi
import main.components.*
import models.LessonScene
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlinx.coroutines.launch

@Composable
fun LessonScenesPage() {
    val router = Router.current
    val scope = rememberCoroutineScope()
    var scenes by remember { mutableStateOf<List<LessonScene>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var sceneToDelete by remember { mutableStateOf<LessonScene?>(null) }

    LaunchedEffect(Unit) {
        try {
            scenes = LessonApi.getScenes().sortedByDescending { it.createdAt }
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load scenes"
        } finally {
            loading = false
        }
    }

    val darkMode = rememberDarkMode()

    Div(attrs = {
        style {
            padding(24.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(16.px)
            height(100.vh)
            overflowY("auto")
            boxSizing("border-box")
            backgroundColor(if (darkMode) main.theme.Theme.colors.backgroundDark else main.theme.Theme.colors.background)
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                gap(16.px)
            }
        }) {
            DialogButton(
                small = true,
                style = DialogButtonStyle.Secondary,
                onClick = { router.navigate("/tools") },
            ) {
                Translated("Back")
            }
        H2(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("English lesson scenes") }
        }

        sceneToDelete?.let { scene ->
            BasicConfirmDialog(
                title = translated("Delete lesson scene?"),
                message = translated("Are you sure you want to delete this lesson? This cannot be undone."),
                onConfirm = {
                    scope.launch {
                        try {
                            LessonApi.deleteScene(scene.key)
                            scenes = scenes.filter { it.key != scene.key }
                        } catch (e: Throwable) {
                            error = "Failed to delete: ${e.message}"
                        }
                    }
                    sceneToDelete = null
                },
                onCancel = { sceneToDelete = null }
            )
        }

        error?.let {
            Div(attrs = {
                classes(main.theme.Styles.formErrorBanner)
            }) {
                Text(it)
            }
        }
        
        DialogButton(
            isLoading = isCreating,
            onClick = {
                scope.launch {
                    isCreating = true
                    try {
                        val newScene = LessonApi.saveScene(LessonScene(title = ""))
                        router.navigate("/tools/scenes/${newScene.key}")
                    } catch (e: Throwable) {
                        error = e.message ?: "Failed to create scene"
                    } finally {
                        isCreating = false
                    }
                }
            },
        ) {
            Translated("Create new lesson")
        }

        if (loading) {
            Text("Loading...")
        } else {
            scenes.forEach { scene ->
                Paper(attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface)
                    }
                }) {
                    Text(scene.title)
                    Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px) } }) {
                        DialogButton(
                            onClick = { router.navigate("/tools/scenes/${scene.key}") },
                        ) {
                            Translated("Edit")
                        }
                        DialogButton(
                            style = DialogButtonStyle.Danger,
                            onClick = { sceneToDelete = scene },
                        ) {
                            Text("🗑️")
                        }
                    }
                }
            }
        }
    }
}
