package main.components

import androidx.compose.runtime.*
import Translations.str
import kotlinx.coroutines.launch
import main.api.WorldApi
import main.theme.Styles
import models.World
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun SelectWorldDialog(
    onClose: () -> Unit,
    onSelect: (World) -> Unit,
    preselectWorldId: String? = null,
) {
    var items by remember { mutableStateOf<List<World>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val worlds = WorldApi.getWorlds()
            // If we have a preselect ID, we could reorder but let's just make sure it's in the list
            items = if (preselectWorldId != null) {
                val preselected = worlds.find { it.key == preselectWorldId }
                if (preselected != null) {
                    // Put it at the top
                    listOf(preselected) + worlds.filter { it.key != preselectWorldId }
                } else worlds
            } else worlds
        } catch (t: Throwable) {
            console.error("Failed to load worlds", t)
            error = t.message ?: "An error occurred"
        } finally {
            isLoading = false
        }
    }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) items else items.filter { 
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q) 
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Select World"),
        maxWidth = "560px",
        content = {
            DialogInput(
                value = query,
                onInput = { query = it },
                placeholder = str("Search worlds..."),
            )

            when {
                isLoading -> Div(attrs = { classes(Styles.chatLoadingText) }) { Translated("Loading...") }
                error != null -> Div(attrs = { classes(Styles.formErrorMessage) }) { Text(error!!) }
                filtered.isEmpty() -> Div(attrs = { classes(Styles.chatEmptyText) }) { Translated("No worlds found") }
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
                        filtered.forEach { world ->
                            DialogButton(
                                onClick = {
                                    onSelect(world)
                                    onClose()
                                },
                                style = if (world.key == preselectWorldId) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
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
                                        Text(world.title.ifEmpty { translated("Untitled World") })
                                        if (world.key == preselectWorldId) {
                                            Span(attrs = { style { marginLeft(8.px); fontSize(0.8.em); fontStyle("italic") } }) {
                                                Translated("(Current)")
                                            }
                                        }
                                    }
                                    Span(attrs = { style { fontSize(0.8.em); opacity(0.7) } }) {
                                        Text("/${world.url}")
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
