package main.components.inventory

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.ItemApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun AdminItemsDialog(
    onClose: () -> Unit,
) {
    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Item?>(null) }

    val scope = rememberCoroutineScope()

    fun loadItems() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                items = ItemApi.getAllItemsAdmin()
            } catch (t: Throwable) {
                console.error("Failed to load items", t)
                errorMessage = t.message ?: "Failed to load items"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadItems() }

    val headerActions: @Composable () -> Unit = {
        DialogButton(
            style = DialogButtonStyle.Primary,
            onClick = { showCreateItemDialog = true }
        ) { Translated("New") }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Items"),
        headerActions = headerActions,
        maxWidth = "720px",
        content = {
            if (errorMessage != null) {
                P(attrs = { classes(Styles.adminItemsErrorText) }) { Text(errorMessage!!) }
            }
            if (isLoading) {
                P { Translated("Loading...") }
            } else if (items.isEmpty()) {
                P { Translated("No items found") }
            } else {
                // Header row
                Div(attrs = { classes(Styles.adminItemsGridHeader) }) {
                    Span { /* image column header left blank intentionally */ }
                    Span { Translated("Title") }
                    Span { Translated("Cost") }
                    Span { Translated("Asset index") }
                    Span { Translated("Wearable") }
                    Span { Translated("Actions") }
                }

                // List rows
                for (item in items) {
                    Div(attrs = { classes(Styles.adminItemsGridRow) }) {
                        // image
                        Div {
                            ItemRenderer(item = item, size = 32)
                        }
                        Span { Text(item.title.ifBlank { "(untitled)" }) }
                        Span { Text(item.cost.toString()) }
                        Span { Text(item.assetIndex.toString()) }
                        Span {
                            if (item.wearable) {
                                val sex = item.wearableSex?.name?.let { translated(it) } ?: "?"
                                val location = item.wearableLocation?.name?.let { translated(it) } ?: "?"
                                Text("${translated("Yes")} ($sex, $location)")
                            } else {
                                Text(translated("No"))
                            }
                        }
                        Div(attrs = { classes(Styles.adminItemsRowActions) }) {
                            DialogButton(
                                style = DialogButtonStyle.Primary,
                                onClick = { editingItem = item }
                            ) { Translated("Edit") }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )

    if (showCreateItemDialog) {
        CreateItemDialog(
            onClose = { showCreateItemDialog = false },
            onItemCreated = {
                showCreateItemDialog = false
                loadItems()
            },
        )
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onClose = { editingItem = null },
            onSaved = { updated ->
                editingItem = null
                // update in list without full reload
                items = items.map { if (it.key == updated.key) updated else it }
            },
        )
    }
}
