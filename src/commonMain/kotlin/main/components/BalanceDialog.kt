package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import main.api.BalanceApi
import kotlinx.coroutines.launch
import main.components.media.UploadMediaDialog
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
import main.components.common.formatAmount
import org.jetbrains.compose.web.attributes.InputMode
import org.jetbrains.compose.web.dom.NumberInput
import kotlin.math.min

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
    var imageToView by remember { mutableStateOf<String?>(null) }
    var showSentToast by remember { mutableStateOf(false) }
    var lastSubmittedType by remember { mutableStateOf<UserBalanceChangeType?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            balance = BalanceApi.getBalance()
            recent = BalanceApi.getRecent(5)
            pending = BalanceApi.getRequests("Received")
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
            BalanceApi.submitRequest(body)
            // Reset form and reload lists
            val submittedType = type
            amount = 0
            notes = ""
            attachments = emptyList()
            type = UserBalanceChangeType.Deposit
            lastSubmittedType = submittedType
            showSentToast = true
            pending = BalanceApi.getRequests("Received")
            balance = BalanceApi.getBalance()
            recent = BalanceApi.getRecent(5)
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
            // Balance summary box
            Div({ classes(Styles.balanceSummaryBox) }) {
                when (val b = balance) {
                    null -> P { Text("Loading...") }
                    else -> {
                        Div({ classes(Styles.balanceAmountText) }) { Text("${formatAmount(b.balance)} VND") }
                        P({ classes(Styles.balanceSubtext) }) { Text("Current balance") }
                    }
                }
            }

            // Success toast
            if (showSentToast) {
                Div({ classes(Styles.formSuccessMessage) }) {
                    val lt = lastSubmittedType
                    Text(
                        if (lt == UserBalanceChangeType.Withdraw) translated("Withdraw request sent")
                        else translated("Deposit request sent")
                    )
                }
                LaunchedEffect(showSentToast) {
                    kotlinx.coroutines.delay(3000)
                    showSentToast = false
                }
            }

            // Pending requests section (if any)
            if (pending.isNotEmpty()) {
                H4({ classes(Styles.balanceSectionTitle) }) { Translated("Pending requests") }
                pending.forEach { r ->
                    main.components.common.PendingRequestRow(
                        request = r,
                        onImageClick = { url -> imageToView = url },
                        onCancel = {
                            scope.launch {
                                try {
                                    BalanceApi.cancelRequest(r.key)
                                    pending = BalanceApi.getRequests("Received")
                                } catch (t: Throwable) {
                                    error = t.message
                                }
                            }
                        }
                    )
                }
            }

            error?.let { e ->
                Div({ classes(Styles.formErrorMessage) }) { Text(e) }
            }

            // Request form
            Div({ classes(Styles.formGroup) }) {
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
                NumberInput(
                    value = amount,
                    min = 1,
                    attrs = {
                        classes(Styles.inputField)
                        onInput { amount = it.value?.toLong() ?: 0L }
                    }
                )

                Label(attrs = { classes(Styles.formLabel) }) { Translated("Notes") }
                TextArea(value = notes, attrs = { classes(Styles.inputField); onInput { notes = it.value } })

                // Attachments
                Div({ classes(Styles.formGroup) }) {
                    Label(attrs = { classes(Styles.formLabel) }) {
                        if (type == UserBalanceChangeType.Withdraw) {
                            Translated("Upload your VNQR")
                        } else {
                            Translated("Upload proof of payment")
                        }

                    }
                    if (attachments.isNotEmpty()) {
                        Div(attrs = { classes(Styles.imageThumbRow) }) {
                            attachments.forEach { url ->
                                org.jetbrains.compose.web.dom.Img(src = url, attrs = {
                                    classes(Styles.imageThumb)
                                    onClick { imageToView = url }
                                })
                            }
                        }
                    }
                    DialogButton(onClick = { showUpload = true }, isPrimary = true) { Translated("Add image") }
                    if (attachments.isNotEmpty()) {
                        DialogButtonDanger(
                            onClick = { attachments = emptyList() }
                        ) { Translated("Remove image") }
                    }
                }

                // Submit
                Div({ classes(Styles.formButtonsContainer) }) {
                    DialogButton(onClick = onClose, isPrimary = false) { Translated("Close") }

                    DialogButton(onClick = { if (!submitting) submitNonce++ }, isPrimary = true) { Text(if (submitting) translated("Submitting...") else translated("Submit")) }
                }
            }

            // Recent events at the bottom
            H4({ classes(Styles.balanceSectionTitle) }) { Text("Recent events") }
            recent.forEach { h ->
                main.components.common.BalanceEventRow(item = h)
            }
            onViewAll?.let {
                DialogButton(onClick = it, isPrimary = false) { Text("View all") }
            }

            if (showUpload) {
                UploadMediaDialog(
                    onClose = { showUpload = false },
                    onSelect = { url: String -> attachments = listOf(url); showUpload = false }
                )
            }

            imageToView?.let { url ->
                ImageDialog(imageUrl = url, onClose = { imageToView = null })
            }
        }
    )
}
