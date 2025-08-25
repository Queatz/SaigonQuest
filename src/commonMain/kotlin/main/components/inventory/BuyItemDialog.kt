package main.components.inventory

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.ItemApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.common.formatAmount
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.dom.*

@Composable
fun BuyItemDialog(
    item: Item,
    currentBalance: Long,
    onClose: () -> Unit,
    onPurchased: (ItemApi.BuyItemResponse) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var quantity by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmOpen by remember { mutableStateOf(false) }

    val perItemCost = item.cost
    val totalCost = perItemCost * quantity.toLong()
    val insufficient = totalCost > currentBalance

    fun doBuy() {
        if (insufficient || quantity <= 0) return
        loading = true
        error = null
        scope.launch {
            try {
                val resp = ItemApi.buyItem(itemId = item.key, quantity = quantity)
                onPurchased(resp)
            } catch (t: Throwable) {
                error = t.message ?: str("Insufficient funds")
            } finally {
                loading = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = str("Buy item"),
        maxWidth = "520px",
        content = {
            // Centered icon
            Div({
                style {
                    property("display", "flex")
                    property("flex-direction", "column")
                    property("align-items", "center")
                    property("gap", "8px")
                }
            }) {
                Div { ItemRenderer(item = item, size = 96) }
                H3 { Text(item.title) }
                P { Text(item.description) }
            }

            // Quantity & costs
            Div({
                style {
                    property("display", "grid")
                    property("grid-template-columns", "120px 1fr")
                    property("gap", "8px 12px")
                    property("margin-top", "12px")
                }
            }) {
                Label { Translated("Quantity") }
                Input(type = InputType.Number, attrs = {
                    classes(Styles.inputField)
                    min("1")
                    value(quantity.toString())
                    onInput { evt ->
                        val v = evt.value?.toInt() ?: 1
                        quantity = if (v < 1) 1 else v
                    }
                })

                Label { Translated("Per item") }
                P { Text(formatAmount(perItemCost)) ; Text(" ") ; Translated("VND") }

                Label { Translated("Total") }
                P { Text(formatAmount(totalCost)); Text(" ") ; Translated("VND") }
            }

            if (error != null) {
                P({ classes(Styles.formErrorMessage) }) { Text(error!!) }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
            DialogButton(
                onClick = { confirmOpen = true },
                isPrimary = true,
                isDisabled = insufficient || loading
            ) { if (loading) Translated("Loading...") else Translated("Buy") }
        }
    )

    if (confirmOpen) {
        BaseDialog(
            onClose = { confirmOpen = false },
            title = str("Confirm"),
            content = {
                P { Text(item.title); Text(" x"); Text(quantity.toString()) }
            },
            actions = {
                DialogButton(onClick = { confirmOpen = false }) { Translated("Cancel") }
                DialogButton(
                    onClick = {
                        confirmOpen = false
                        doBuy()
                    },
                    isPrimary = true,
                ) {
                    Translated("Buy")
                    Text(" ")
                    Translated("for")
                    Text(" ")
                    Text(formatAmount(totalCost))
                    Text(" ")
                    Translated("VND")
                }
            }
        )
    }
}
