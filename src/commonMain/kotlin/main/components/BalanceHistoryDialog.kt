package main.components

import androidx.compose.runtime.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import main.components.BaseDialog
import main.components.DialogButton
import main.httpClient
import models.UserBalanceHistory
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun BalanceHistoryDialog(
    baseUrl: String,
    bearerToken: String,
    onClose: () -> Unit
) {
    var items by remember { mutableStateOf<List<UserBalanceHistory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            items = httpClient.get("$baseUrl/balance/history") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("limit", 500)
            }.body()
        } catch (t: Throwable) {
            error = t.message
        }
    }

    BaseDialog(
        onClose = onClose,
        title = "Balance history",
        maxWidth = "560px",
        showCloseButton = true,
        content = {
            error?.let { e -> P { Text("Error: $e") } }
            items.forEach { h ->
                P { Text("${h.eventType}: ${h.changeAmount} → ${h.endBalance} | ${h.notes}") }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Text("Close") }
        }
    )
}
