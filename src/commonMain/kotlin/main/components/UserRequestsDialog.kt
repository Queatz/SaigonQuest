package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.BalanceApi
import main.api.CostsApi
import main.api.UserRequestApi
import main.components.common.formatAmount
import main.theme.Styles
import models.CreateUserRequestDto
import models.UserRequest
import models.UserRequestType
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.TextInput
import web.animations.awaitAnimationFrame

@Composable
fun UserRequestsDialog(
    preselectedType: UserRequestType,
    onClose: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var type by remember { mutableStateOf(preselectedType) }
    var fee by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(false) }
    var myRequests by remember { mutableStateOf<List<UserRequest>>(emptyList()) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var contentError by remember { mutableStateOf<String?>(null) }
    var showConfirmPurchase by remember { mutableStateOf(false) }

    // Keep internal type in sync with prop in case dialog is reused with a different preselectedType
    LaunchedEffect(preselectedType) {
        type = preselectedType
    }

    LaunchedEffect(type) {
        try {
            error = null
            fee = CostsApi.userRequestFee(type)
        } catch (t: Throwable) {
            error = t.message
            fee = null
        }
    }

    LaunchedEffect(Unit) {
        // Load balance and my requests once on open
        try {
            balance = BalanceApi.getBalance().balance
        } catch (_: Throwable) {
            balance = null
        }
        try {
            myRequests = UserRequestApi.getMyRequests()
        } catch (t: Throwable) {
            error = t.message
        }
    }

    fun typeLabel(t: UserRequestType): String = when (t) {
        UserRequestType.Wearable -> str("Wearable item")
        else -> str(t.name)
    }

    fun handleSubmit() {
        loading = true
        scope.launch {
            try {
                error = null
                // set validation errors prior to sending
                val titleValid = title.length in 3..80
                val contentValid = content.length in 10..2000
                titleError = if (!titleValid) str("Title must be between 3 and 80 characters") else null
                contentError = if (!contentValid) str("Description must be between 10 and 2000 characters") else null
                if (!titleValid || !contentValid) {
                    return@launch
                }
                val created = UserRequestApi.create(
                    dto = CreateUserRequestDto(
                        type = type,
                        title = title,
                        content = content,
                    ),
                )
                // Refresh list and balance
                try { myRequests = UserRequestApi.getMyRequests() } catch (_: Throwable) {}
                try { balance = BalanceApi.getBalance().balance } catch (_: Throwable) {}
                // Clear form
                title = ""
                content = ""
                titleError = null
                contentError = null
                onClose()
            } catch (t: Throwable) {
                error = t.message
            } finally {
                loading = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = str("User requests"),
        maxWidth = "600px",
        content = {
            Div {
                Label { Translated("Type") }
                Select(attrs = {
                    classes(Styles.inputField)
                    onInput { ev ->
                        val v = ev.value ?: type.name
                        type = try { UserRequestType.valueOf(v) } catch (_: Throwable) { type }
                    }
                }) {
                    for (t in UserRequestType.entries) {
                        Option(
                            value = t.name,
                            attrs = {
                                if (t == type) selected()
                            },
                        ) { Text(typeLabel(t)) }
                    }
                }
            }

            Div {
                Label { Translated("Title") }
                TextInput(
                    value = title,
                    attrs = {
                        classes(Styles.inputField)
                        onInput {
                            title = it.value
                        }
                        autoFocus()
                        ref {
                            scope.launch {
                                delay(100)
                                awaitAnimationFrame()
                                it.focus()
                            }
                            
                            onDispose {}
                        }
                    },
                )
                val te = titleError
                if (te != null) {
                    P(attrs = { classes(Styles.formErrorMessage) }) { Text(te) }
                }
            }

            Div {
                Label { Translated("Description") }
                TextArea(
                    value = content,
                    attrs = {
                        classes(Styles.inputField)
                        onInput { content = it.value }
                    },
                )
                val ce = contentError
                if (ce != null) {
                    P(attrs = { classes(Styles.formErrorMessage) }) { Text(ce) }
                }
            }

            // Cost and Balance section with padding
            Div({
                style {
                    property("padding", "12px")
                    property("margin", "8px 0")
                    property("border", "1px solid rgba(255,255,255,0.12)")
                    property("border-radius", "8px")
                    property("display", "grid")
                    property("grid-template-columns", "1fr 1fr")
                    property("gap", "8px 16px")
                }
            }) {
                Div(
                    {
                        style {
                            display(DisplayStyle.Flex)
                            gap(4.px)
                        }
                    }
                ) {
                    Span { Translated("Cost") }
                    Span({ style { fontWeight("bold") } }) {
                        val f = fee
                        if (f == null) Text("…") else Text("${formatAmount(f)} VND")
                    }
                }
                Div(
                    {
                        style {
                            display(DisplayStyle.Flex)
                            gap(4.px)
                        }
                    }
                ) {
                    Span { Translated("Balance") }
                    Span({ style { fontWeight("bold") } }) { Text(balance?.let { formatAmount(it) } ?: "…") ; Text(" VND") }
                }
            }

            // Frontend validation with limits
            val titleValid = title.length in 3..80
            val contentValid = content.length in 10..2000
            val canSubmit = (titleValid && contentValid && (fee ?: Long.MAX_VALUE) <= (balance ?: Long.MAX_VALUE))
            Button(attrs = {
                classes(Styles.baseDialogButtonPrimary)
                if (!canSubmit || loading) disabled()
                onClick {
                    showConfirmPurchase = true
                }
            }) { Translated("Submit request") }

            if (error != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(error!!) }
            }

            // Existing requests
            org.jetbrains.compose.web.dom.H4 { Translated("My requests") }
            if (myRequests.isEmpty()) {
                P { Translated("No results") }
            } else {
                for (r in myRequests) {
                    Div({
                        style {
                            property("padding", "8px 0")
                            property("border-bottom", "1px solid rgba(255,255,255,0.06)")
                        }
                    }) {
                        // Line 1: type • status • cost
                        Div {
                            Span { Text(typeLabel(r.type)) }
                            Span { Text(" · ") }
                            val s = when (r.status) {
                                models.UserRequestStatus.InReview -> str("In review")
                                models.UserRequestStatus.Approved -> str("Approved")
                                models.UserRequestStatus.Declined -> str("Declined")
                                models.UserRequestStatus.Completed -> str("Completed")
                                models.UserRequestStatus.Refunded -> str("Refunded")
                                models.UserRequestStatus.Open -> str("Open")
                            }
                            Span { Text(s) }
                            Span { Text(" · ") }
                            Span { Text(formatAmount(r.cost)); Text(" VND") }
                        }
                        // Line 2: title
                        if (r.title.isNotBlank()) {
                            Div { Text(r.title) }
                        }
                        // Line 3: content/description
                        if (r.content.isNotBlank()) {
                            Div { Text(r.content) }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        },
    )

    // Confirm purchase dialog
    if (showConfirmPurchase) {
        val price = fee ?: 0L
        BasicConfirmDialog(
            message = "${str("You are about to purchase a request for")} ${formatAmount(price)} ${str("VND")}. ${str("Continue?")}",
            onConfirm = {
                showConfirmPurchase = false
                handleSubmit()
            },
            onCancel = { showConfirmPurchase = false },
            title = str("Confirm purchase"),
        )
    }
}
