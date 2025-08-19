package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.AdminBalanceApi
import main.components.common.AdminRequestRow
import main.components.common.formatAmount
import main.theme.Styles
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
            if (bearerToken.isBlank()) {
                error = "Please sign in to view admin balance requests"
                items = emptyList()
                return
            }
            error = null
            // Load pending requests (default behavior on backend)
            items = AdminBalanceApi.getRequests()

            // Fetch balances for involved users
            val uniqueUserIds = items.map { it.userId }.distinct()
            val map = mutableMapOf<String, Long>()
            for (uid in uniqueUserIds) {
                map[uid] = try { AdminBalanceApi.getUserBalance(uid).balance } catch (_: Throwable) { 0L }
            }
            balances = map

            // Fetch usernames for involved users
            val userMap = mutableMapOf<String, models.User>()
            for (uid in uniqueUserIds) {
                try { main.api.UserApi.getUserById(uid)?.let { userMap[uid] = it } } catch (_: Throwable) {}
            }
            users = userMap
        } catch (t: Throwable) {
            error = t.message
            items = emptyList()
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
                                AdminBalanceApi.approveRequest(r.key, noteVal)
                                load()
                            } catch (t: Throwable) { error = t.message }
                        }
                    },
                    onDeny = {
                        scope.launch {
                            try {
                                AdminBalanceApi.denyRequest(r.key, noteVal)
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
