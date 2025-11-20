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
import main.theme.WorldDocsStyles
import models.Doc
import models.DocTree
import main.utils.ellipsize
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Div
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
    onSelectNew: (() -> Unit)? = null,
    excludeIds: Set<String> = emptySet(),
    confirmButtonLabel: String? = null,
) {
    val scope = rememberCoroutineScope()

    var docs by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Doc?>(null) }

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

    val filtered = remember(docs, query, excludeIds) {
        val q = query.trim()
        val base = docs.filter { it.key !in excludeIds }
        if (q.isBlank()) base
        else base.filter { d ->
            d.title.contains(q, ignoreCase = true) || d.key.contains(q, ignoreCase = true)
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Select doc"),
        maxWidth = "900px",
        content = {
            // Search and refresh row
            Div(attrs = {
                classes(WorldDocsStyles.selectDocToolbar)
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
                        classes(WorldDocsStyles.selectDocList)
                    }) {
                        filtered.forEach { d ->
                            Div(attrs = {
                                classes(
                                    Styles.clickable,
                                    WorldDocsStyles.selectDocItem,
                                )
                                if (confirmButtonLabel != null && selected?.key == d.key) {
                                    classes(WorldDocsStyles.selectDocItemSelected)
                                }
                                onClick {
                                    if (confirmButtonLabel == null) {
                                        onSelect(d)
                                    } else {
                                        selected = d
                                    }
                                }
                            }) {
                                val titleText = d.title.ifBlank { translated("Untitled doc") }
                                val previewText = d.content
                                    .trim()
                                    .replace(Regex("\\s+"), " ")
                                    .ellipsize(64)
                                Div(attrs = {
                                    classes(WorldDocsStyles.selectDocItemTitle)
                                }) {
                                    Text(titleText)
                                }
                                if (previewText.isNotBlank()) {
                                    Div(attrs = {
                                        classes(WorldDocsStyles.selectDocItemPreview)
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
            if (confirmButtonLabel != null) {
                DialogButton(
                    onClick = { selected?.let { onSelect(it) } },
                    isPrimary = true,
                ) { Text(confirmButtonLabel) }
            } else if (onSelectNew != null) {
                DialogButton(
                    onClick = { onSelectNew() },
                    isPrimary = true,
                ) { Translated("New doc") }
            }
        }
    )
}
