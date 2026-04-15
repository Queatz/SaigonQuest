package main.components

import androidx.compose.runtime.*
import Translations.str
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.theme.Styles
import models.Object
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun SelectObjectDialog(
    worldId: String,
    onClose: () -> Unit,
    onSelect: (Object) -> Unit,
) {
    var items by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(worldId) {
        if (worldId.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            items = ObjectApi.getObjectsByWorldId(worldId).filter { it.kind == models.ObjectKind.World }
        } catch (t: Throwable) {
            console.error("Failed to load objects", t)
            error = t.message ?: "An error occurred"
        } finally {
            isLoading = false
        }
    }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) items else items.filter { 
            it.title.lowercase().contains(q) || it.file.lowercase().contains(q) 
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Select Object"),
        maxWidth = "560px",
        content = {
            if (worldId.isEmpty()) {
                P { Translated("Please select a world first") }
            } else {
                DialogInput(
                    value = query,
                    onInput = { query = it },
                    placeholder = str("Search objects..."),
                )

                when {
                    isLoading -> Div(attrs = { classes(Styles.chatLoadingText) }) { Translated("Loading...") }
                    error != null -> Div(attrs = { classes(Styles.formErrorMessage) }) { Text(error!!) }
                    filtered.isEmpty() -> Div(attrs = { classes(Styles.chatEmptyText) }) { Translated("No objects found in this world") }
                    else -> {
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(8.px)
                                marginTop(12.px)
                                maxHeight(400.px)
                                overflowY("auto")
                            }
                        }) {
                            filtered.forEach { obj ->
                                DialogButton(
                                    onClick = {
                                        onSelect(obj)
                                        onClose()
                                    },
                                    style = DialogButtonStyle.Secondary,
                                    attrs = {
                                        style { width(100.percent) }
                                    }
                                ) {
                                    Div(attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            flexDirection(FlexDirection.Column)
                                            alignItems(AlignItems.FlexStart)
                                        }
                                    }) {
                                        Span(attrs = { style { fontWeight("bold") } }) {
                                            Text(obj.title.ifEmpty { translated("Untitled Object") })
                                        }
                                        Span(attrs = { style { fontSize(0.8.em); opacity(0.7) } }) {
                                            Text("${obj.file} (${obj.collection})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Cancel") }
        }
    )
}
