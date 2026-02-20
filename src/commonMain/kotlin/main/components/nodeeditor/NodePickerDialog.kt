package main.components.nodeeditor

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.components.BaseDialog
import main.components.Translated
import main.components.translated
import main.theme.NodeEditorStyles
import main.theme.Styles
import models.LogicNode
import models.NodeCategory
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

/**
 * Dialog that lets the user search and select a logic node type.
 * Clicking a node immediately selects it via [onSelect] and closes the dialog via [onClose].
 */
@Composable
fun NodePickerDialog(
    onSelect: (LogicNode) -> Unit,
    onClose: () -> Unit,
) {
    var nodeTypesByCategory by remember { mutableStateOf<Map<NodeCategory, List<LogicNode>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    // Load node types when opened
    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            try {
                nodeTypesByCategory = ObjectApi.getNodeTypesByCategory()
                isLoading = false
            } catch (t: Throwable) {
                error = t.message
                isLoading = false
            }
        }
        onDispose { job.cancel() }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Select node"),
        maxWidth = "480px",
        content = {
            // Search input
            Div {
                TextInput(
                    value = searchQuery,
                    attrs = {
                        classes(Styles.inputField)
                        autoFocus()
                        placeholder(str("Search nodes"))
                        onInput { ev -> searchQuery = ev.value }
                        ref {
                            it.focus()

                            onDispose { }
                        }
                    }
                )
            }

            when {
                isLoading -> {
                    Div { Text(translated("Loading node types...")) }
                }

                error != null -> {
                    Div { Text("${translated("Error loading node types")}: $error") }
                }

                else -> {
                    Div(
                        {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(8.px)
                            }
                        }
                    ) {
                        val filteredByCategory: Map<NodeCategory, List<LogicNode>> =
                            if (searchQuery.isBlank()) nodeTypesByCategory
                            else nodeTypesByCategory
                                .mapValues { (_, nodes) ->
                                    val q = searchQuery.trim()
                                    nodes.filter { n ->
                                        n.label.contains(q, ignoreCase = true) ||
                                                n.description.contains(q, ignoreCase = true)
                                    }
                                }
                                .filterValues { it.isNotEmpty() }

                        filteredByCategory.forEach { (category, nodes) ->
                            if (nodes.isEmpty()) return@forEach

                            H4(attrs = { classes(NodeEditorStyles.sectionHeader) }) {
                                Text(
                                    when (category) {
                                        NodeCategory.Action -> translated("Actions")
                                        NodeCategory.Dialogue -> translated("Dialogues")
                                        NodeCategory.Event -> translated("Events")
                                        NodeCategory.Input -> translated("Inputs")
                                    },
                                )
                            }

                            nodes.forEach { node ->
                                // Reuse the draggable item styling for consistent look, but use click to select
                                Div(
                                    attrs = {
                                        classes(NodeEditorStyles.draggableItem)
                                        tabIndex(0)
                                        onClick {
                                            onSelect(node)
                                            onClose()
                                        }
                                    }
                                ) {
                                    Div { Text(node.label) }
                                    if (node.description.isNotBlank()) {
                                        Div(
                                            attrs = {
                                                style {
                                                    fontSize(11.px)
                                                    marginTop(3.px)
                                                }
                                            }
                                        ) {
                                            Text(node.description)
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
            // Only a cancel/close button is needed; selection is immediate on click
            main.components.DialogButton(onClick = onClose) { Translated("Cancel") }
        }
    )
}
