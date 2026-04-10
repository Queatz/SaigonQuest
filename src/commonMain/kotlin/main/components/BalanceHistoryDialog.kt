package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import main.components.common.BalanceEventRow
import main.httpClient
import models.UserBalanceHistory
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
        title = translated("Balance history"),
        maxWidth = "560px",
        showCloseButton = true,
        content = {
            error?.let { e -> P { Text("Error: $e") } }
            if (items.isEmpty() && error == null) {
                P { Translated("No history yet") }
            } else {
                items.forEach { h ->
                    BalanceEventRow(item = h)
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}
