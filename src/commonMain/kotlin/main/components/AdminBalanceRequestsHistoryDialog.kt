package main.components

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.AdminBalanceApi
import main.api.UserApi
import main.components.*
import main.components.common.formatAmount
import main.components.common.formatTimestampLikeChat
import main.theme.Styles
import models.User
import models.UserBalanceChangeRequest
import models.UserBalanceChangeStatus
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun AdminBalanceRequestsHistoryDialog(
    baseUrl: String,
    bearerToken: String,
    onClose: () -> Unit
) {
    var items by remember { mutableStateOf<List<UserBalanceChangeRequest>>(emptyList()) }
    var users by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageToView by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            error = null
            if (bearerToken.isBlank()) {
                error = "Please sign in to view admin balance request history"
                items = emptyList()
                return@LaunchedEffect
            }
            val pending: List<UserBalanceChangeRequest> = AdminBalanceApi.getRequests(resolved = false, limit = 500)
            val resolved: List<UserBalanceChangeRequest> = AdminBalanceApi.getRequests(resolved = true, limit = 500)
            val fetched = (pending + resolved)
            items = fetched.sortedByDescending { it.createdAt.epochSeconds }

            // Load user details
            val uniqueIds = items.map { it.userId }.distinct()
            val pairList = uniqueIds.map { uid ->
                scope.launch { }
                uid to (UserApi.getUserById(uid))
            }
            users = pairList.mapNotNull { (k, u) -> if (u != null) k to u else null }.toMap()
        } catch (t: Throwable) {
            error = t.message
            items = emptyList()
        }
    }

    BaseDialog(
        onClose = onClose,
        title = str("All Requests History"),
        maxWidth = "820px",
        showCloseButton = true,
        isFullHeight = true,
        content = {
            error?.let { e -> P { Text("Error: $e") } }

            if (items.isEmpty() && error == null) {
                P { Translated("No requests found") }
            } else {
                items.forEach { r ->
                    // Row container
                    Paper({ classes(Styles.requestRow) }) {
                        val isDeposit = r.type.toString() == "Deposit"
                        // Header line
                        Div({ classes(Styles.requestHeader) }) {
                            Div({ classes(Styles.requestMeta) }) {
                                Div({ classes(Styles.eventTypeBadge, if (isDeposit) Styles.eventTypeDeposit else Styles.eventTypeWithdraw) }) {
                                    Translated(if (isDeposit) "Deposit" else "Withdraw")
                                }
                                Span({ classes(if (isDeposit) Styles.amountPositive else Styles.amountNegative) }) {
                                    Text("${if (isDeposit) "+" else "-"}${formatAmount(r.amount)} ${translated("VND")}")
                                }
                                // Status chip
                                val statusStr = when (r.status) {
                                    UserBalanceChangeStatus.Complete -> translated("Approved")
                                    UserBalanceChangeStatus.Received -> translated("Received")
                                    UserBalanceChangeStatus.Denied -> translated("Denied")
                                    UserBalanceChangeStatus.CancelledByUser -> translated("Cancelled by user")
                                }
                                val statusClass = when (r.status) {
                                    UserBalanceChangeStatus.Complete -> Styles.statusApproved
                                    UserBalanceChangeStatus.Denied -> Styles.statusDenied
                                    else -> Styles.statusReceived
                                }
                                Span({ classes(Styles.statusChip, statusClass) }) { Text(statusStr) }
                            }
                            // Timestamp
                            Span({ classes(Styles.chatMessageTimestamp) }) {
                                Text(formatTimestampLikeChat(r.createdAt))
                            }
                        }
                        // User line
                        val u = users[r.userId]
                        P({ classes(Styles.requestUserId) }) {
                            Text("User: ")
                            Text(if (u != null) "${u.username} (${r.userId})" else r.userId)
                        }
                        // Notes
                        if (r.notes.isNotBlank()) {
                            P({ classes(Styles.eventNotes) }) { Text(r.notes) }
                        }
                        // Attachments
                        if (r.attachments.isNotEmpty()) {
                            Div({ classes(Styles.imageThumbRow) }) {
                                r.attachments.forEach { url ->
                                    org.jetbrains.compose.web.dom.Img(src = url, attrs = {
                                        classes(Styles.imageThumb)
                                        onClick { imageToView = url }
                                    })
                                }
                            }
                        }
                    }
                }
            }

            imageToView?.let { url ->
                ImageDialog(imageUrl = url, onClose = { imageToView = null })
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}
