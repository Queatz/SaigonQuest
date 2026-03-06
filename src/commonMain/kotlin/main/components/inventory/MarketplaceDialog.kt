package main.components.inventory

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.BalanceApi
import main.api.ItemApi
import main.components.BaseDialog
import main.components.BasicConfirmDialog
import main.components.DialogButton
import main.components.Translated
import main.components.common.formatAmount
import main.theme.InventoryStyles
import main.theme.Styles
import models.Item
import models.Sex
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*
import models.UserRequestType
import main.components.UserRequestsDialog
import main.components.translated
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.marginLeft

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
    var showRequestsDialog by remember { mutableStateOf(false) }
    var showRequestTypeDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(UserRequestType.Item) }

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
        val q = query.trim()
        if (q.isBlank()) items else items.filter {
            it.title.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true) ||
                    it.key.contains(q, ignoreCase = true)
        }
    }

    BaseDialog(
        onClose = onClose,
        title = str("Market"),
        maxWidth = "900px",
        headerActions = {
            Div({
                style {
                    fontSize(14.px)
                }
            }) {
                Text(balance?.let { formatAmount(it) } ?: "...")
                Text(" ")
                Translated("VND")
            }

            // Request new item and wearable type buttons
            Button(attrs = {
                classes(Styles.baseDialogButtonSecondary)
                onClick {
                    requestType = UserRequestType.Item
                    showRequestTypeDialog = true
                }
            }) { Text("💡 "); Translated("Request new item type") }
        },
        content = {
            // Search row
            Div({
                style {
                    display(DisplayStyle.Flex)
                    gap(8.px)
                    alignItems(AlignItems.Center)
                }
            }) {
                TextInput(
                    value = query,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Search items"))
                        onInput { query = it.value }
                    },
                )
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
                                    Div(
                                        attrs = {
                                            style {
                                                display(DisplayStyle.Flex)
                                                alignItems(AlignItems.Center)
                                            }
                                        }
                                    ) {
                                        H4(
                                            attrs = {
                                                style {
                                                    margin(0.px)
                                                }
                                            }
                                        ) { Text(item.title) }

                                        if (item.wearable) {
                                            Div(
                                                attrs = {
                                                    classes(InventoryStyles.wearableChip)
                                                    style { marginLeft(8.px) }
                                                }
                                            ) {
                                                Translated("Wearable")
                                            }

                                            val sex = item.wearableSex ?: Sex.Boy
                                            Div(
                                                attrs = {
                                                    classes(InventoryStyles.wearableChip)
                                                    when (sex) {
                                                        Sex.Boy -> classes(InventoryStyles.wearableChipBoy)
                                                        Sex.Girl -> classes(InventoryStyles.wearableChipGirl)
                                                    }
                                                    style { marginLeft(4.px) }
                                                }
                                            ) {
                                                Translated(sex.name)
                                            }
                                        }
                                    }
                                    Div {
                                        Text(item.description)
                                    }
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

    if (showRequestTypeDialog) {
        BasicConfirmDialog(
            translated("Is the item wearable?"),
            onConfirm = {
                showRequestTypeDialog = false
                requestType = UserRequestType.Wearable
                showRequestsDialog = true
            },
            onCancel = {
                showRequestTypeDialog = false
                requestType = UserRequestType.Item
                showRequestsDialog = true
            },
            onDismiss = {
                showRequestTypeDialog = false
            }
        )
    }

    if (showRequestsDialog) {
        UserRequestsDialog(
            preselectedType = requestType,
            onClose = { showRequestsDialog = false },
        )
    }
}
