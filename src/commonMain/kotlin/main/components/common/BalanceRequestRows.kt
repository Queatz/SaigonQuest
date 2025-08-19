package main.components.common

import androidx.compose.runtime.Composable
import main.theme.Styles
import models.UserBalanceChangeRequest
import models.UserBalanceChangeType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun PendingRequestRow(
    request: UserBalanceChangeRequest,
    onImageClick: (String) -> Unit = {}
) {
    val isDeposit = request.type == UserBalanceChangeType.Deposit
    Div({ classes(Styles.requestRow) }) {
        // Header: type badge, amount, status chip
        Div({ classes(Styles.requestHeader) }) {
            Div({ classes(Styles.requestMeta) }) {
                Div({ classes(Styles.eventTypeBadge, if (isDeposit) Styles.eventTypeDeposit else Styles.eventTypeWithdraw) }) {
                    Text(if (isDeposit) "Deposit" else "Withdraw")
                }
                Span({ classes(if (isDeposit) Styles.amountPositive else Styles.amountNegative) }) {
                    Text("${if (isDeposit) "+" else "-"}${formatAmount(request.amount)} VND")
                }
                Span({}) { Text("•") }
                run {
                    val statusStr = when (request.status.toString()) {
                        "Complete" -> "Approved"
                        else -> request.status.toString()
                    }
                    val statusClass = when (statusStr) {
                        "Approved" -> Styles.statusApproved
                        "Denied" -> Styles.statusDenied
                        else -> Styles.statusReceived
                    }
                    Span({ classes(Styles.statusChip, statusClass) }) { Text(statusStr) }
                }
            }
        }
        // Notes
        if (request.notes.isNotBlank()) {
            P({ classes(Styles.eventNotes) }) { Text(request.notes) }
        }

        // Attachments
        if (request.attachments.isNotEmpty()) {
            Div({ classes(Styles.imageThumbRow) }) {
                request.attachments.forEach { url ->
                    org.jetbrains.compose.web.dom.Img(src = url, attrs = {
                        classes(Styles.imageThumb)
                        onClick { onImageClick(url) }
                    })
                }
            }
        }
    }
}

@Composable
fun AdminRequestRow(
    request: UserBalanceChangeRequest,
    currentBalance: Long,
    noteValue: String,
    onNoteChange: (String) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    username: String? = null,
    onImageClick: (String) -> Unit = {}
) {
    val isDeposit = request.type == UserBalanceChangeType.Deposit
    val insufficient = !isDeposit && request.amount > currentBalance
    Div({ classes(Styles.requestRow) }) {
        // Header
        Div({ classes(Styles.requestHeader) }) {
            Div({ classes(Styles.requestMeta) }) {
                // Type
                Div({ classes(Styles.eventTypeBadge, if (isDeposit) Styles.eventTypeDeposit else Styles.eventTypeWithdraw) }) {
                    Text(if (isDeposit) "Deposit" else "Withdraw")
                }
                // Amount
                Span({ classes(if (isDeposit) Styles.amountPositive else Styles.amountNegative) }) {
                    Text("${if (isDeposit) "+" else "-"}${formatAmount(request.amount)} VND")
                }
                // Status chip
                run {
                    val statusClass = when (request.status.toString()) {
                        "Approved" -> Styles.statusApproved
                        "Denied" -> Styles.statusDenied
                        else -> Styles.statusReceived
                    }
                    Span({ classes(Styles.statusChip, statusClass) }) { Text(request.status.toString()) }
                }
                // Balance
                Span({ classes(Styles.requestUserId) }) { Text("Balance: ${formatAmount(currentBalance)} VND") }
            }
        }
        // User + Notes
        P({ classes(Styles.requestUserId) }) {
            Text("User: ")
            Text(if (username.isNullOrBlank()) request.userId else "$username (${request.userId})")
        }
        if (request.notes.isNotBlank()) {
            P({ classes(Styles.eventNotes) }) { Text(request.notes) }
        }

        // Attachments
        if (request.attachments.isNotEmpty()) {
            Div({ classes(Styles.imageThumbRow) }) {
                request.attachments.forEach { url ->
                    org.jetbrains.compose.web.dom.Img(src = url, attrs = {
                        classes(Styles.imageThumb)
                        onClick { onImageClick(url) }
                    })
                }
            }
        }

        // Insufficient funds warning
        if (insufficient) {
            P({ classes(Styles.formErrorMessage) }) { Text("Insufficient funds") }
        }

        // Decision note input
        Label { Text("Note") }
        TextInput(value = noteValue, attrs = { classes(Styles.inputField); onInput { onNoteChange(it.value) } })

        // Actions
        Div({ classes(Styles.requestActions) }) {
            main.components.DialogButton(onClick = onApprove, isPrimary = true) { Text("Approve") }
            main.components.DialogButtonDanger(onClick = onDeny) { Text("Deny") }
        }
    }
}
