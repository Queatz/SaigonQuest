package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.UserRequestApi
import main.components.common.formatAmount
import main.theme.Styles
import models.UserRequest
import models.UserRequestStatus
import models.UserRequestType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px

@Composable
fun AdminUserRequestsDialog(
    onClose: () -> Unit,
) {
    var requests by remember { mutableStateOf<List<UserRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf<UserRequestStatus?>(UserRequestStatus.Open) }
    var actionBusyId by remember { mutableStateOf<String?>(null) }
    var noteInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val scope = rememberCoroutineScope()

    fun load() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                // Load filtered by selected status (default Open)
                requests = UserRequestApi.getAll(selectedStatus)
            } catch (t: Throwable) {
                errorMessage = t.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedStatus) { load() }

    fun typeLabel(t: UserRequestType): String =
        if (t == UserRequestType.Wearable) str("Wearable item") else t.name

    fun statusLabel(s: UserRequestStatus): String = when (s) {
        UserRequestStatus.Open -> str("Open")
        UserRequestStatus.InReview -> str("In review")
        UserRequestStatus.Approved -> str("Approved")
        UserRequestStatus.Declined -> str("Declined")
        UserRequestStatus.Completed -> str("Completed")
        UserRequestStatus.Refunded -> str("Refunded")
    }

    val headerActions: @Composable () -> Unit = {
        // Status filter
        Div {
            Select(attrs = {
                classes(Styles.inputField)
                onInput { ev ->
                    val v = ev.value ?: ""
                    selectedStatus = if (v.isBlank()) null else try { UserRequestStatus.valueOf(v) } catch (_: Throwable) { selectedStatus }
                }
            }) {
                Option(value = "") { Translated("All") }
                for (s in UserRequestStatus.entries) {
                    Option(
                        value = s.name,
                        attrs = {
                            if (s == selectedStatus) selected()
                        }
                    ) { Text(statusLabel(s)) }
                }
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("User requests (admin)"),
        headerActions = headerActions,
        maxWidth = "860px",
        content = {
            if (errorMessage != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
            }

            when {
                isLoading -> P { Translated("Loading...") }
                requests.isEmpty() -> P { Translated("No results") }
                else -> {
                    // Rows
                    for (r in requests) {
                        val note = noteInputs[r.key] ?: ""
                        Div(attrs = { classes(Styles.assetsGridRow) }) {
                            // Type
                            Span { Text(typeLabel(r.type)) }
                            // Title
                            Span { Text(r.title.ifBlank { "(untitled)" }) }
                            // Description/content
                            Span { Text(r.content.ifBlank { "-" }) }
                            // Status
                            Span { Text(statusLabel(r.status)) }
                            // Cost
                            Span {
                                Text(formatAmount(r.cost)); Text(" "); Translated("VND")
                            }
                            // Actions
                            Span {
                                // Inline note input used for decline/add note
                                TextInput(
                                    value = note,
                                    attrs = {
                                        classes(Styles.inputField)
                                        onInput { ev ->
                                            noteInputs = noteInputs.toMutableMap().also { m -> m[r.key] = ev.value }
                                        }
                                        style {
                                            marginBottom(8.px)
                                        }
                                    },
                                )
                                // Buttons row
                                Div(
                                    attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            gap(4.px)
                                        }
                                    }
                                ) {
                                    val busy = actionBusyId == r.key
                                    if (r.status != UserRequestStatus.InReview) {
                                        Button(attrs = {
                                            classes(Styles.baseDialogButtonSecondary)
                                            if (busy) disabled()
                                            onClick {
                                                actionBusyId = r.key
                                                scope.launch {
                                                    try { UserRequestApi.updateStatus(r.key, UserRequestStatus.InReview) } finally {
                                                        actionBusyId = null; load()
                                                    }
                                                }
                                            }
                                        }) { Translated("Mark in review") }
                                    }

                                    if (r.status != UserRequestStatus.Approved) {
                                        Button(attrs = {
                                            classes(Styles.baseDialogButtonSecondary)
                                            if (busy) disabled()
                                            onClick {
                                                actionBusyId = r.key
                                                scope.launch {
                                                    try { UserRequestApi.updateStatus(r.key, UserRequestStatus.Approved) } finally {
                                                        actionBusyId = null; load()
                                                    }
                                                }
                                            }
                                        }) { Translated("Approve") }
                                    }

                                    if (r.status != UserRequestStatus.Declined) {
                                        Button(attrs = {
                                            classes(Styles.baseDialogButtonSecondary)
                                            if (busy) disabled()
                                            onClick {
                                                actionBusyId = r.key
                                                scope.launch {
                                                    try { UserRequestApi.updateStatus(r.key, UserRequestStatus.Declined, note = note) } finally {
                                                        actionBusyId = null; load()
                                                    }
                                                }
                                            }
                                        }) { Translated("Decline") }
                                    }

                                    if (r.status != UserRequestStatus.Completed) {
                                        Button(attrs = {
                                            classes(Styles.baseDialogButtonSecondary)
                                            if (busy) disabled()
                                            onClick {
                                                actionBusyId = r.key
                                                scope.launch {
                                                    try { UserRequestApi.updateStatus(r.key, UserRequestStatus.Completed) } finally {
                                                        actionBusyId = null; load()
                                                    }
                                                }
                                            }
                                        }) { Translated("Mark completed") }
                                    }

                                    Button(attrs = {
                                        classes(Styles.baseDialogButtonSecondary)
                                        if (busy) disabled()
                                        onClick {
                                            actionBusyId = r.key
                                            scope.launch {
                                                try { if (note.isNotBlank()) UserRequestApi.addNote(r.key, note) } finally {
                                                    actionBusyId = null; load()
                                                }
                                            }
                                        }
                                    }) { Translated("Add note") }
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}
