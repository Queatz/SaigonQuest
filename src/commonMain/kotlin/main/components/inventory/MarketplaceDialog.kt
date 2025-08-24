package main.components.inventory

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.BalanceApi
import main.api.ItemApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.common.formatAmount
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.dom.*

@Composable
fun MarketplaceDialog(
    onClose: () -> Unit,
    onPurchased: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    var balance by remember { mutableStateOf<Long?>(null) }

    var buyingItem by remember { mutableStateOf<Item?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                val all = ItemApi.getAllItems()
                items = all
            } catch (t: Throwable) {
                error = t.message ?: "Failed to load items"
            } finally {
                loading = false
            }
        }
        scope.launch {
            try {
                balance = BalanceApi.getBalance().balance
            } catch (_: Throwable) {
                balance = null
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) items else items.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) || it.key.lowercase().contains(q) }
    }

    BaseDialog(
        onClose = onClose,
        title = str("Marketplace"),
        maxWidth = "900px",
        headerActions = {
            Div({
                style {
                    property("color", "white")
                    property("font-weight", "bold")
                }
            }) {
                Text(balance?.let { formatAmount(it) } ?: "...")
                Text(" ")
                Translated("VND")
            }
        },
        content = {
            // Search row
            Div({
                style {
                    property("display", "flex")
                    property("gap", "8px")
                    property("align-items", "center")
                }
            }) {
                Input(type = InputType.Text, attrs = {
                    classes(Styles.inputField)
                    value(query)
                    placeholder(str("Search items"))
                    onInput { query = it.value }
                })
            }

            if (error != null) {
                P({ classes(Styles.formErrorMessage) }) { Text(error!!) }
            }

            when {
                loading -> P { Translated("Loading...") }
                filtered.isEmpty() -> P { Translated("No results") }
                else -> {
                    Div({
                        style {
                            property("display", "grid")
                            property("grid-template-columns", "repeat(auto-fill, minmax(260px, 1fr))")
                            property("gap", "12px")
                            property("margin-top", "12px")
                            property("max-height", "55vh")
                            property("overflow-y", "auto")
                        }
                    }) {
                        filtered.forEach { item ->
                            Div({
                                style {
                                    property("padding", "12px")
                                    property("border", "1px solid rgba(255,255,255,0.1)")
                                    property("border-radius", "8px")
                                    property("display", "flex")
                                    alignItems("center")
                                    property("gap", "10px")
                                }
                            }) {
                                // Icon
                                Div { ItemRenderer(item = item, size = 48) }
                                // Details
                                Div({ style { property("flex", "1") } }) {
                                    H4 { Text(item.title) }
                                    P { Text(item.description) }
                                    P {
                                        B { Translated("Cost") }
                                        Text(": ")
                                        Text(formatAmount(item.cost))
                                        Text(" VND")
                                    }
                                }
                                // Buy
                                Div({
                                    style {
                                        property("display", "flex")
                                        property("align-items", "center")
                                    }
                                }) {
                                    Button(attrs = {
                                        classes(Styles.baseDialogButtonPrimary)
                                        onClick { buyingItem = item }
                                    }) { Translated("Buy") }
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

    val itemToBuy = buyingItem
    if (itemToBuy != null) {
        BuyItemDialog(
            item = itemToBuy,
            currentBalance = balance ?: 0L,
            onClose = { buyingItem = null },
            onPurchased = { resp ->
                buyingItem = null
                balance = resp.balance.balance
                onPurchased()
            }
        )
    }
}
