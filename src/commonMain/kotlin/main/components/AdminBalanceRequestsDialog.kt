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
import main.components.common.AdminRequestRow
import main.components.common.formatAmount
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
    var notesByKey by remember { mutableStateOf(mapOf<String, String>()) }
    var balances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<Map<String, models.User>>(emptyMap()) }
    var showHistory by remember { mutableStateOf(false) }
    var imageToView by remember { mutableStateOf<String?>(null) }

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

            // Fetch usernames for involved users
            val userMap = mutableMapOf<String, models.User>()
            for (uid in uniqueUserIds) {
                try {
                    main.api.UserApi.getUserById(uid)?.let { userMap[uid] = it }
                } catch (_: Throwable) {}
            }
            users = userMap
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
        headerActions = {
            DialogButton(onClick = { showHistory = true }, isPrimary = false) { Text("View history") }
        },
        content = {
            error?.let { e -> P { Text("Error: $e") } }
            items.forEach { r ->
                val bal = balances[r.userId] ?: 0L
                val noteVal = notesByKey[r.key] ?: ""
                val user = users[r.userId]
                AdminRequestRow(
                    request = r,
                    currentBalance = bal,
                    noteValue = noteVal,
                    onNoteChange = { nv -> notesByKey = notesByKey.toMutableMap().also { it[r.key] = nv } },
                    onApprove = {
                        scope.launch {
                            try {
                                httpClient.post("$baseUrl/admin/balance-requests/${r.key}/approve") {
                                    header(HttpHeaders.Authorization, "Bearer $bearerToken")
                                    contentType(ContentType.Application.Json)
                                    setBody(AdminDecisionRequest(noteVal))
                                }
                                load()
                            } catch (t: Throwable) { error = t.message }
                        }
                    },
                    onDeny = {
                        scope.launch {
                            try {
                                httpClient.post("$baseUrl/admin/balance-requests/${r.key}/deny") {
                                    header(HttpHeaders.Authorization, "Bearer $bearerToken")
                                    contentType(ContentType.Application.Json)
                                    setBody(AdminDecisionRequest(noteVal))
                                }
                                load()
                            } catch (t: Throwable) { error = t.message }
                        }
                    },
                    username = user?.username,
                    onImageClick = { url -> imageToView = url }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Text("Close") }
        }
    )

    if (showHistory) {
        AdminBalanceRequestsHistoryDialog(
            baseUrl = baseUrl,
            bearerToken = bearerToken,
            onClose = { showHistory = false }
        )
    }

    imageToView?.let { url ->
        ImageDialog(imageUrl = url, onClose = { imageToView = null })
    }
}
