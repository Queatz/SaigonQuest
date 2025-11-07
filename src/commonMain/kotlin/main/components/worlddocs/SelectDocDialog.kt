package main.components.worlddocs

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.Doc
import models.DocTree
import main.utils.ellipsize
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.cursor
import web.cssom.AtRules.Companion.property
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

/**
 * SelectDocDialog
 * - Local search box
 * - Lists docs for a world
 * - Click a doc row to choose it immediately
 * - Cancel button closes without selection
 */
@Composable
fun SelectDocDialog(
    worldId: String,
    onSelect: (Doc) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var docs by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }

    fun flatten(tree: List<DocTree>): List<Doc> {
        val list = mutableListOf<Doc>()
        fun walk(nodes: List<DocTree>) {
            nodes.forEach { n ->
                list.add(n.doc)
                if (n.children.isNotEmpty()) walk(n.children)
            }
        }
        walk(tree)
        return list
    }

    fun loadDocs() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val tree = DocApi.getDocTree(worldId)
                docs = flatten(tree)
            } catch (t: Throwable) {
                console.error("Failed to load docs", t)
                errorMessage = t.message ?: "Failed to load docs"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(worldId) { loadDocs() }

    val filtered = remember(docs, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) docs
        else docs.filter { d ->
            d.title.lowercase().contains(q) || d.key.lowercase().contains(q)
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Select doc"),
        maxWidth = "900px",
        content = {
            // Search and refresh row
            Div(attrs = {
                style {
                    property("display", "flex")
                    property("gap", "8px")
                    property("align-items", "center")
                }
            }) {
                TextInput(
                    value = query,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Search docs"))
                        onInput { ev -> query = ev.value }
                    }
                )
            }

            if (errorMessage != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
            }

            when {
                isLoading -> P { Translated("Loading...") }
                filtered.isEmpty() -> P { Translated("No results") }
                else -> {
                    Div(attrs = {
                        style {
                            property("display", "flex")
                            property("flex-direction", "column")
                            property("gap", "8px")
                            property("margin-top", "12px")
                            property("max-height", "50vh")
                            property("overflow-y", "auto")
                        }
                    }) {
                        filtered.forEach { d ->
                            Div(attrs = {
                                classes(Styles.clickable)
                                style {
                                    property("padding", "11px")
                                    property("border", "1px solid rgba(255,255,255,0.1)")
                                    property("border-radius", "8px")
                                }
                                onClick { onSelect(d) }
                            }) {
                                val titleText = d.title.ifBlank { translated("Untitled doc") }
                                val previewText = d.content
                                    .trim()
                                    .replace(Regex("\\s+"), " ")
                                    .ellipsize(64)
                                Div(attrs = {
                                    style {
                                        property("font-weight", "500")
                                    }
                                }) {
                                    Text(titleText)
                                }
                                if (previewText.isNotBlank()) {
                                    Div(attrs = {
                                        style {
                                            property("margin-top", "4px")
                                            property("font-size", "12px")
                                            property("opacity", "0.7")
                                        }
                                    }) {
                                        Text(previewText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(
                onClick = onClose,
            ) { Translated("Cancel") }
        }
    )
}
