@file:OptIn(kotlin.time.ExperimentalTime::class)
package main.components

import androidx.compose.runtime.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import main.api.UserApi
import main.components.common.formatAmount
import main.components.common.formatTimestampLikeChat
import main.httpClient
import main.theme.Styles
import models.User
import models.UserBalanceChangeRequest
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
            // Fetch both pending and resolved to show full history with status handling
            val pendingResp = httpClient.get("$baseUrl/admin/balance-requests") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("resolved", false)
                parameter("limit", 500)
            }
            val resolvedResp = httpClient.get("$baseUrl/admin/balance-requests") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("resolved", true)
                parameter("limit", 500)
            }

            if (pendingResp.status.value !in 200..299 || resolvedResp.status.value !in 200..299) {
                val err = buildString {
                    append("Failed to load requests: ")
                    if (pendingResp.status.value !in 200..299) append("pending ${pendingResp.status.value} ")
                    if (resolvedResp.status.value !in 200..299) append("resolved ${resolvedResp.status.value}")
                }
                error = try { pendingResp.body<models.ErrorResponse>().message } catch (_: Throwable) { err }
                items = emptyList()
                return@LaunchedEffect
            }

            val pending: List<UserBalanceChangeRequest> = pendingResp.body()
            val resolved: List<UserBalanceChangeRequest> = resolvedResp.body()
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
        }
    }

    BaseDialog(
        onClose = onClose,
        title = "All Requests History",
        maxWidth = "820px",
        showCloseButton = true,
        isFullHeight = true,
        content = {
            error?.let { e -> P { Text("Error: $e") } }

            if (items.isEmpty() && error == null) {
                P { Text("No requests found") }
            } else {
                items.forEach { r ->
                    // Row container
                    Div({ classes(Styles.requestRow) }) {
                        val isDeposit = r.type.toString() == "Deposit"
                        // Header line
                        Div({ classes(Styles.requestHeader) }) {
                            Div({ classes(Styles.requestMeta) }) {
                                Div({ classes(Styles.eventTypeBadge, if (isDeposit) Styles.eventTypeDeposit else Styles.eventTypeWithdraw) }) {
                                    Text(if (isDeposit) "Deposit" else "Withdraw")
                                }
                                Span({ classes(if (isDeposit) Styles.amountPositive else Styles.amountNegative) }) {
                                    Text("${if (isDeposit) "+" else "-"}${formatAmount(r.amount)} VND")
                                }
                                // Status chip
                                val statusStr = when (r.status.toString()) {
                                    "Complete" -> "Approved"
                                    else -> r.status.toString()
                                }
                                val statusClass = when (statusStr) {
                                    "Approved" -> Styles.statusApproved
                                    "Denied" -> Styles.statusDenied
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
            DialogButton(onClick = onClose) { Text("Close") }
        }
    )
}
