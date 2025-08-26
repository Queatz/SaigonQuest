package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.BalanceApi
import main.components.common.formatAmount
import main.theme.Styles
import models.UserBalance
import org.jetbrains.compose.web.dom.*

@Composable
fun SendVndDialog(
    friendUserId: String,
    friendUsername: String,
    onClose: () -> Unit,
    onSent: (() -> Unit)? = null,
) {
    var balance by remember { mutableStateOf<UserBalance?>(null) }
    var amountInput by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            balance = BalanceApi.getBalance()
        } catch (t: Throwable) {
            error = t.message
        }
    }

    val currentBalance = balance?.balance ?: 0L
    val canSend = amountInput > 0 && amountInput <= currentBalance && !sending

    BaseDialog(
        onClose = onClose,
        title = translated("Send VND to") + " " + friendUsername,
        maxWidth = "480px",
        showCloseButton = true,
        content = {
            // Current balance
            P { Text("${translated("Your balance")}: ${formatAmount(currentBalance)} ${translated("VND")}") }

            // Amount input
            Label(attrs = { classes(Styles.formLabel) }) { Translated("Amount (VND)") }
            NumberInput(
                value = amountInput,
                min = 1,
                attrs = {
                    classes(Styles.inputField)
                    onInput { amountInput = it.value?.toLong() ?: 0L }
                }
            )

            // Optional note
            Label(attrs = { classes(Styles.formLabel) }) { Translated("Note (optional)") }
            TextArea(value = note, attrs = { classes(Styles.inputField); onInput { note = it.value } })

            // Error message
            error?.let { e -> Div({ classes(Styles.formErrorMessage) }) { Text(e) } }

            // Warning if exceeds balance
            if (amountInput > currentBalance) {
                P { Translated("Amount exceeds your balance") }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
            DialogButton(
                onClick = {
                    if (!canSend) return@DialogButton
                    sending = true
                    error = null
                    scope.launch {
                        try {
                            BalanceApi.sendToFriend(friendUserId, amountInput, note.ifBlank { null })
                            // Show success dialog instead of closing immediately
                            showSuccess = true
                        } catch (t: Throwable) {
                            error = t.message
                        } finally {
                            sending = false
                        }
                    }
                },
                isPrimary = true,
                isDisabled = !canSend,
            ) {
                if (sending) Translated("Sending...") else Translated("Send VND")
            }
        }
    )

    if (showSuccess) {
        BasicAlertDialog(
            message = translated("Successfully sent VND"),
            onConfirm = {
                showSuccess = false
                onSent?.invoke()
                onClose()
            }
        )
    }
}
