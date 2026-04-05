package main.components.worlddocs

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.*
import main.theme.Styles
import main.theme.WorldDocsStyles
import main.utils.ellipsize
import models.Doc
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

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


    fun loadDocs() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val tree = DocApi.getDocTree(worldId)
                docs = WorldDocsUtils.flatten(tree)
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
                DialogInput(
                    value = query,
                    onInput = { query = it },
                    placeholder = str("Search docs")
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
                    style = DialogButtonStyle.Primary,
                ) { Text(confirmButtonLabel) }
            } else if (onSelectNew != null) {
                DialogButton(
                    onClick = { onSelectNew() },
                    style = DialogButtonStyle.Primary,
                ) { Translated("New doc") }
            }
        }
    )
}
