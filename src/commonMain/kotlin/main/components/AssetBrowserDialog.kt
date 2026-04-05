package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.AssetItemApi
import main.theme.Styles
import models.AssetSearchResult
import models.AssetType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import Translations.str
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import models.UserRequestType
import org.jetbrains.compose.web.attributes.autoFocus

@Composable
fun AssetBrowserDialog(
    type: AssetType,
    onSelect: (AssetSearchResult) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var titleQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<AssetSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadCategories() {
        scope.launch {
            try {
                val base = AssetItemApi.searchWithAsset(
                    type = type,
                    title = titleQuery.ifBlank { null },
                    category = null,
                )
                categories = base.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }
                    .distinct()
                    .sorted()
            } catch (t: Throwable) {
                console.error("Failed to load categories", t)
            }
        }
    }

    fun loadResults() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                results = AssetItemApi.searchWithAsset(
                    type = type,
                    title = titleQuery.ifBlank { null },
                    category = selectedCategory?.ifBlank { null },
                )
            } catch (t: Throwable) {
                console.error("Failed to search assets", t)
                errorMessage = t.message
            } finally {
                isLoading = false
            }
        }
    }

    // Debounce title search
    LaunchedEffect(titleQuery) {
        delay(300L)
        loadCategories()
        loadResults()
    }

    var showRequestsDialog by remember { mutableStateOf(false) }

    BaseDialog(
        onClose = onClose,
        title = translated("Asset browser"),
        maxWidth = "800px",
        headerActions = {
            if (type == AssetType.Objects) {
                DialogButton(
                    onClick = { showRequestsDialog = true },
                    style = DialogButtonStyle.Secondary
                ) { Text("💡 "); Translated("Request new object type") }
            }
        },
        content = {
            Div(attrs = {
                classes(main.theme.AssetStyles.assetDialogContent)
            }) {
                // Filters row
                Div(attrs = {
                    classes(main.theme.AssetStyles.assetFiltersRow)
                }) {
                    // Title search
                    DialogInput(
                        value = titleQuery,
                        onInput = {
                            titleQuery = it
                        },
                        label = "Title",
                        placeholder = str("Search by title"),
                        autoFocus = true,
                        attrs = {
                            ref {
                                it.focus()
                                onDispose { }
                            }
                        }
                    )

                    // Category select
                    Label { Translated("Category") }
                    Select(attrs = {
                        classes(Styles.inputField)
                        onInput { event ->
                            val v = event.value ?: ""
                            selectedCategory = v.ifBlank { null }
                            loadResults()
                        }
                    }) {
                        Option(value = "") { Translated("All categories") }
                        for (cat in categories) {
                            Option(value = cat) { Text(cat) }
                        }
                    }
                }

                if (errorMessage != null) {
                    P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
                }

                when {
                    isLoading -> P { Translated("Loading...") }
                    results.isEmpty() -> P { Translated("No results") }
                    else -> {
                        // Grid of results
                        Div(attrs = {
                            classes(main.theme.AssetStyles.assetGrid)
                        }) {
                            for (r in results) {
                                Div(
                                    attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            flexDirection(FlexDirection.Column)
                                        }
                                    }
                                ) {
                                    AssetRenderer(result = r)
                                    DialogButton(
                                        onClick = {
                                            onSelect(r)
                                            onClose()
                                        },
                                        style = DialogButtonStyle.Primary
                                    ) { Translated("Select") }
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

    if (showRequestsDialog) {
        UserRequestsDialog(
            preselectedType = UserRequestType.Object,
            onClose = { showRequestsDialog = false },
        )
    }
}
