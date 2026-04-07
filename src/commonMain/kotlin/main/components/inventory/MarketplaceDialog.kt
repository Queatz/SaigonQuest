package main.components.inventory

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.BalanceApi
import main.api.ItemApi
import main.components.BaseDialog
import main.components.BasicConfirmDialog
import main.components.DialogButton
import main.components.DialogInput
import main.components.DialogButtonStyle
import main.components.FramedBorder
import main.components.Translated
import main.components.common.formatAmount
import main.theme.InventoryStyles
import main.theme.Styles
import models.Item
import models.Sex
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
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
            DialogButton(
                onClick = {
                    requestType = UserRequestType.Item
                    showRequestTypeDialog = true
                },
                style = DialogButtonStyle.Secondary
            ) { Text("💡 "); Translated("Request new item type") }
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
                DialogInput(
                    value = query,
                    onInput = { query = it },
                    placeholder = str("Search items")
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
                        classes(InventoryStyles.inventoryGrid)
                        style {
                            property("margin-top", "12px")
                            property("max-height", "55vh")
                            property("overflow-y", "auto")
                        }
                    }) {
                        filtered.forEach { item ->
                            FramedBorder(
                                borderImage = "/raw/data/ui/borders/input.png",
                                backgroundImage = "/raw/data/ui/backgrounds/whitestone.png",
                                darkBackgroundImage = "/raw/data/ui/backgrounds/chalkboard.png",
                                attrs = {
                                    classes(Styles.dialogInputWrapper)
                                    classes(InventoryStyles.inventoryCard)
                                }
                            ) {
                                // Icon
                                InventoryBox(
                                    onClick = { /* Buying handled by button */ },
                                    item = item,
                                    isLoading = false,
                                    useBorder = false,
                                )

                                // Title
                                Div(attrs = { classes(InventoryStyles.inventoryCardTitle) }) {
                                    Text(item.title)
                                }

                                // Chips: wearable + sex
                                Div(attrs = { classes(InventoryStyles.inventoryCardChips) }) {
                                    if (item.wearable) {
                                        Div(attrs = { classes(InventoryStyles.wearableChip) }) {
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
                                            }
                                        ) {
                                            Translated(sex.name)
                                        }
                                    }
                                }

                                // Description
                                Div(
                                    attrs = {
                                        classes(InventoryStyles.descriptionText)
                                        style {
                                            property("text-align", "center")
                                            property("margin-bottom", "8px")
                                        }
                                    }
                                ) {
                                    Text(item.description)
                                }

                                // Cost
                                P(
                                    attrs = {
                                        style {
                                            margin(0.px)
                                            fontSize(14.px)
                                            fontWeight("bold")
                                        }
                                    }
                                ) {
                                    Text(formatAmount(item.cost))
                                    Text(" VND")
                                }

                                // Buy button at bottom
                                Div(attrs = { classes(InventoryStyles.inventoryCardActionBar) }) {
                                    DialogButton(
                                        onClick = { buyingItem = item },
                                        style = DialogButtonStyle.Primary,
                                        attrs = {
                                            classes(InventoryStyles.inventoryCardActionButton)
                                        }
                                    ) { Translated("Buy") }
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
