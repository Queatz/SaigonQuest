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
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import main.components.media.UploadMediaDialog
import main.httpClient
import main.theme.Styles
import models.CreateBalanceChangeRequest
import models.UserBalance
import models.UserBalanceChangeRequest
import models.UserBalanceChangeType
import models.UserBalanceHistory
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexWrap
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
fun BalanceDialog(
    baseUrl: String,
    bearerToken: String,
    onClose: () -> Unit,
    onViewAll: (() -> Unit)? = null,
) {
    var balance by remember { mutableStateOf<UserBalance?>(null) }
    var recent by remember { mutableStateOf<List<UserBalanceHistory>>(emptyList()) }
    var amount by remember { mutableStateOf(0L) }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(UserBalanceChangeType.Deposit) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<List<UserBalanceChangeRequest>>(emptyList()) }
    var attachments by remember { mutableStateOf<List<String>>(emptyList()) }
    var showUpload by remember { mutableStateOf(false) }
    var submitNonce by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            balance =
                httpClient.get("$baseUrl/balance") { header(HttpHeaders.Authorization, "Bearer $bearerToken") }.body()
            recent = httpClient.get("$baseUrl/balance/history") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer $bearerToken"
                ); parameter("limit", 5)
            }.body()
            pending = httpClient.get("$baseUrl/balance/requests") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("status", "Received")
            }.body()
        } catch (t: Throwable) {
            error = t.message
        }
    }

    LaunchedEffect(submitNonce) {
        if (submitNonce == 0) return@LaunchedEffect
        if (amount <= 0) { error = "Amount must be positive"; return@LaunchedEffect }
        if (attachments.isEmpty()) { error = "Attachment image is required"; return@LaunchedEffect }
        submitting = true
        error = null
        try {
            val body = CreateBalanceChangeRequest(
                type = type,
                amount = amount,
                notes = notes,
                attachments = attachments
            )
            httpClient.post("$baseUrl/balance/requests") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            // Reset form and reload lists
            amount = 0
            notes = ""
            attachments = emptyList()
            type = UserBalanceChangeType.Deposit
            pending = httpClient.get("$baseUrl/balance/requests") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("status", "Received")
            }.body()
            balance = httpClient.get("$baseUrl/balance") { header(HttpHeaders.Authorization, "Bearer $bearerToken") }.body()
            recent = httpClient.get("$baseUrl/balance/history") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("limit", 5)
            }.body()
        } catch (t: Throwable) {
            error = t.message
        } finally {
            submitting = false
        }
    }

    BaseDialog(
        onClose = onClose,
        title = "Balance",
        showCloseButton = true,
        maxWidth = "520px",
        isFullscreen = false,
        content = {
            when (val b = balance) {
                null -> P { Text("Loading...") }
                else -> P { Text("Current balance: ${b.balance} VND") }
            }

            H4 { Text("Last 5 events") }
            recent.forEach { h ->
                P { Text("${h.eventType}: ${h.changeAmount} → ${h.endBalance} | ${h.notes}") }
            }
            onViewAll?.let {
                DialogButton(onClick = it, isPrimary = false) { Text("View all") }
            }

            if (pending.isNotEmpty()) {
                H4 { Text("Pending requests") }
                pending.forEach { r ->
                    P { Text("${r.type} | ${r.amount} VND | ${r.status} | ${r.notes}") }
                }
            }

            error?.let { e ->
                Div({ classes(Styles.formErrorMessage) }) { Text(e) }
            }

            // Simple request form (attachments handled elsewhere)
            Div({ classes(Styles.formFieldContainer) }) {
                Label(attrs = { classes(Styles.formLabel) }) { Translated("Type") }
                Div(attrs = { classes(Styles.languageSwitcherContainer) }) {
                    Div(attrs = {
                        classes(
                            Styles.languageSwitcherOption,
                            if (type == UserBalanceChangeType.Deposit) Styles.languageSwitcherOptionActive else Styles.languageSwitcherOptionInactive
                        )
                        onClick { type = UserBalanceChangeType.Deposit }
                    }) { Translated("Deposit") }
                    Div(attrs = {
                        classes(
                            Styles.languageSwitcherOption,
                            if (type == UserBalanceChangeType.Withdraw) Styles.languageSwitcherOptionActive else Styles.languageSwitcherOptionInactive
                        )
                        onClick { type = UserBalanceChangeType.Withdraw }
                    }) { Translated("Withdraw") }
                }

                Label(attrs = { classes(Styles.formLabel) }) { Translated("Amount (VND)") }
                Input(type = InputType.Number, attrs = {
                    classes(Styles.inputField)
                    onInput { amount = it.value?.toLong() ?: 0L }
                })

                Label(attrs = { classes(Styles.formLabel) }) { Translated("Notes") }
                TextArea(value = notes, attrs = { classes(Styles.inputField); onInput { notes = it.value } })

                // Attachments
                Div({ classes(Styles.formFieldContainer) }) {
                    Label(attrs = { classes(Styles.formLabel) }) {
                        if (type == UserBalanceChangeType.Withdraw) {
                            Translated("Upload your VNQR")
                        } else {
                            Translated("Upload proof of payment")
                        }

                    }
                    if (attachments.isNotEmpty()) {
                        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); flexWrap(FlexWrap.Wrap) } }) {
                            attachments.forEach { url ->
                                A(href = url, attrs = { attr("target", "_blank"); attr("rel", "noopener noreferrer") }) {
                                    org.jetbrains.compose.web.dom.Img(src = url, attrs = { classes(Styles.imageThumb) })
                                }
                            }
                        }
                    }
                    DialogButton(onClick = { showUpload = true }, isPrimary = false) { Translated("Add image") }
                    if (attachments.isNotEmpty()) {
                        DialogButton(
                            onClick = { attachments = emptyList() },
                            isPrimary = false
                        ) { Translated("Remove image") }
                    }
                }

                // Submit
                Div({ classes(Styles.formButtonsContainer) }) {
                    DialogButton(onClick = onClose, isPrimary = false) { Translated("Close") }

                    DialogButton(onClick = { if (!submitting) submitNonce++ }, isPrimary = true) { Text(if (submitting) translated("Submitting...") else translated("Submit")) }
                }
            }

            if (showUpload) {
                UploadMediaDialog(
                    onClose = { showUpload = false },
                    onSelect = { url: String -> attachments = listOf(url); showUpload = false }
                )
            }
        }
    )
}
