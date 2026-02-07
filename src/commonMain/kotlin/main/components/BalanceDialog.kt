package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.BalanceApi
import main.components.common.BalanceEventRow
import main.components.common.PendingRequestRow
import main.components.common.formatAmount
import main.components.media.UploadMediaDialog
import main.theme.Styles
import models.CreateBalanceChangeRequest
import models.UserBalance
import models.UserBalanceChangeRequest
import models.UserBalanceChangeStatus
import models.UserBalanceChangeType
import models.UserBalanceHistory
import models.MediaType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
fun BalanceDialog(
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
            pending = BalanceApi.getRequests(UserBalanceChangeStatus.Received)
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
            pending = BalanceApi.getRequests(UserBalanceChangeStatus.Received)
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
        title = translated("Balance"),
        showCloseButton = true,
        isFullscreen = false,
        content = {
            // Balance summary box
            Div({ classes(Styles.balanceSummaryBox) }) {
                when (val b = balance) {
                    null -> P { Translated("Loading...") }
                    else -> {
                        Div({ classes(Styles.balanceAmountText) }) { Text("${formatAmount(b.balance)} ${translated("VND")}") }
                        P({ classes(Styles.balanceSubtext) }) { Translated("Current balance") }
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
                    PendingRequestRow(
                        request = r,
                        onImageClick = { url -> imageToView = url },
                        onCancel = {
                            scope.launch {
                                try {
                                    BalanceApi.cancelRequest(r.key)
                                    pending = BalanceApi.getRequests(UserBalanceChangeStatus.Received)
                                } catch (t: Throwable) {
                                    error = t.message
                                }
                            }
                        }
                    )
                }
            }

            // Request form
            Div({ classes(Styles.formGroup, Styles.container) }) {
                error?.let { e ->
                    Div({ classes(Styles.formErrorMessage) }) { Text(e) }
                }

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
                                Img(src = url, attrs = {
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

                if (type == UserBalanceChangeType.Withdraw) {
                    Div({ classes(Styles.formGroup) }) {
                        Text("Withdrawal requests are subject to manual approval by the admin.")
                    }
                }

                // Submit
                Div({ classes(Styles.formButtonsContainer) }) {
                    DialogButton(onClick = { if (!submitting) submitNonce++ }, isPrimary = true) {
                        Text(if (submitting) translated("Submitting...") else translated("Submit"))
                    }
                }
            }

            // Balance history at the bottom
            H4({ classes(Styles.balanceSectionTitle) }) { Translated("Balance history") }
            recent.forEach { h ->
                BalanceEventRow(item = h)
            }
            onViewAll?.let {
                DialogButton(onClick = it, isPrimary = false) { Translated("View all") }
            }

            if (showUpload) {
                UploadMediaDialog(
                    onClose = { showUpload = false },
                    onSelect = { url, _ -> attachments = listOf(url); showUpload = false },
                    filter = MediaType.Image,
                )
            }

            imageToView?.let { url ->
                ImageDialog(imageUrl = url, onClose = { imageToView = null })
            }
        }
    )
}
