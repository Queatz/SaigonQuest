package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import main.httpClient
import main.theme.Styles
import models.AdminDecisionRequest
import models.UserBalanceChangeRequest
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun AdminBalanceRequestsDialog(
    baseUrl: String,
    bearerToken: String,
    onClose: () -> Unit
) {
    var items by remember { mutableStateOf<List<UserBalanceChangeRequest>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var balances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            items = httpClient.get("$baseUrl/admin/balance-requests") { header(HttpHeaders.Authorization, "Bearer $bearerToken") }.body()
            // Fetch balances for involved users
            val uniqueUserIds = items.map { it.userId }.distinct()
            val map = mutableMapOf<String, Long>()
            for (uid in uniqueUserIds) {
                try {
                    val ub: models.UserBalance = httpClient.get("$baseUrl/admin/balance-requests/balance/$uid") {
                        header(HttpHeaders.Authorization, "Bearer $bearerToken")
                    }.body()
                    map[uid] = ub.balance
                } catch (_: Throwable) { map[uid] = 0L }
            }
            balances = map
        } catch (t: Throwable) {
            error = t.message
        }
    }

    LaunchedEffect(Unit) { load() }

    BaseDialog(
        onClose = onClose,
        title = "Pending Requests",
        maxWidth = "720px",
        showCloseButton = true,
        content = {
            error?.let { e -> P { Text("Error: $e") } }
            items.forEach { r ->
                Div {
                    val bal = balances[r.userId] ?: 0L
                    P { Text("${r.userId} | ${r.type} | ${r.amount} VND | ${r.notes}") }
                    P { Text("Balance: $bal VND") }
                    val insufficient = r.type == models.UserBalanceChangeType.Withdraw && r.amount > bal
                    if (insufficient) {
                        P { Text(translated("Insufficient funds")) }
                    }
                    Label { Text("Note") }
                    TextInput(value = note, attrs = { classes(Styles.inputField); onInput { note = it.value } })
                    Div({ classes(Styles.formButtonsContainer) }) {
                        DialogButton(onClick = {
                            scope.launch {
                                try {
                                    httpClient.post("$baseUrl/admin/balance-requests/${r.key}/approve") {
                                        header(HttpHeaders.Authorization, "Bearer $bearerToken")
                                        contentType(ContentType.Application.Json)
                                        setBody(AdminDecisionRequest(note))
                                    }
                                    load()
                                } catch (t: Throwable) { error = t.message }
                            }
                        }, isPrimary = true) { Text("Approve") }

                        DialogButton(onClick = {
                            scope.launch {
                                try {
                                    httpClient.post("$baseUrl/admin/balance-requests/${r.key}/deny") {
                                        header(HttpHeaders.Authorization, "Bearer $bearerToken")
                                        contentType(ContentType.Application.Json)
                                        setBody(AdminDecisionRequest(note))
                                    }
                                    load()
                                } catch (t: Throwable) { error = t.message }
                            }
                        }, isPrimary = false) { Text("Deny") }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Text("Close") }
        }
    )
}
