package main.components.inventory

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.ItemApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.translated
import models.Item
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
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
        Button(attrs = {
            style {
                backgroundColor(rgba(0, 120, 255, 0.7))

                property("border", "0")
                property("border-radius", "4px")
                padding(4.px, 8.px)
                marginRight(8.px)
                cursor("pointer")
                fontSize(14.px)
            }
            onClick { showCreateItemDialog = true }
        }) { Text("+ " + translated("New")) }

        Button(attrs = {
            style {
                backgroundColor(rgba(90, 90, 90, 0.7))

                property("border", "0")
                property("border-radius", "4px")
                padding(4.px, 8.px)
                cursor("pointer")
                fontSize(14.px)
            }
            onClick { loadItems() }
        }) { Translated("Refresh") }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Items"),
        headerActions = headerActions,
        maxWidth = "720px",
        content = {
            Div(attrs = {
                style {
                    padding(16.px)
                    height(100.percent)
                    property("overflow-y", "auto")
                }
            }) {
                if (errorMessage != null) {
                    P(attrs = { style { color(rgba(255, 100, 100, 0.9)) } }) { Text(errorMessage!!) }
                }
                if (isLoading) {
                    P { Translated("Loading...") }
                } else if (items.isEmpty()) {
                    P { Translated("No items found") }
                } else {
                    // Header row
                    Div(attrs = {
                        style {
                            display(DisplayStyle.Grid)
                            property("grid-template-columns", "48px 2fr 1fr 1fr 1fr auto")
                            property("gap", "8px")
                            fontSize(12.px)
                            marginBottom(8.px)
                        }
                    }) {
                        Span { /* image column header left blank intentionally */ }
                        Span { Translated("Title") }
                        Span { Translated("Cost") }
                        Span { Translated("Asset index") }
                        Span { Translated("Wearable") }
                        Span {} // actions
                    }

                    // List rows
                    for (item in items) {
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Grid)
                                property("grid-template-columns", "48px 2fr 1fr 1fr 1fr auto")
                                property("gap", "8px")
                                alignItems(AlignItems.Center)
                                padding(8.px)
                                property("border", "1px solid rgba(255,255,255,0.08)")
                                property("border-radius", "6px")
                                marginBottom(6.px)
                            }
                        }) {
                            // image
                            Div {
                                ItemRenderer(item = item, size = 32)
                            }
                            Span { Text(item.title.ifBlank { "(untitled)" }) }
                            Span { Text(item.cost.toString()) }
                            Span { Text(item.assetIndex.toString()) }
                            Span { Text(if (item.wearable) translated("Yes") else translated("No")) }
                            Div(attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    property("gap", "8px")
                                    justifyContent(JustifyContent.FlexEnd)
                                }
                            }) {
                                Button(attrs = {
                                    style {
                                        backgroundColor(rgba(0, 120, 255, 0.7))

                                        property("border", "0")
                                        property("border-radius", "4px")
                                        padding(4.px, 8.px)
                                        cursor("pointer")
                                        fontSize(12.px)
                                    }
                                    onClick { editingItem = item }
                                }) { Translated("Edit") }
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

    if (showCreateItemDialog) {
        CreateItemDialog(
            onClose = { showCreateItemDialog = false },
            onItemCreated = {
                showCreateItemDialog = false
                loadItems()
            }
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
            }
        )
    }
}
