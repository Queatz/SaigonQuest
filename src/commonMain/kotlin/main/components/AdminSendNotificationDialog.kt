package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.NotificationApi
import main.theme.Styles
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div

/**
 * Dialog to send a system notification (optionally targeted by username).
 */
@Composable
fun AdminSendNotificationDialog(
    onClose: () -> Unit,
) {
    var targetUsername by remember { mutableStateOf("") }
    var notificationTitle by remember { mutableStateOf("") }
    var notificationMessage by remember { mutableStateOf("") }
    var showBroadcastConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    BaseDialog(
        onClose = onClose,
        title = translated("Send system notification"),
        showCloseButton = true,
        content = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        width(100.percent)
                        property("gap", "12px")
                    }
                }
            ) {
                // Target username input (leave blank to broadcast)
                DialogInput(
                    value = targetUsername,
                    onInput = { targetUsername = it },
                    label = "Target username (leave blank to broadcast)",
                    containerAttrs = { style { marginBottom(8.px) } }
                )
                // Notification title input
                DialogInput(
                    value = notificationTitle,
                    onInput = { notificationTitle = it },
                    label = "Title",
                    containerAttrs = { style { marginBottom(8.px) } }
                )
                // Notification message input
                DialogInput(
                    value = notificationMessage,
                    onInput = { notificationMessage = it },
                    label = "Message",
                    containerAttrs = { style { marginBottom(8.px) } }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose, style = DialogButtonStyle.Secondary) { Translated("Close") }
            DialogButton(
                onClick = {
                    if (notificationTitle.isNotBlank() && notificationMessage.isNotBlank()) {
                        if (targetUsername.trim().isBlank()) {
                            showBroadcastConfirm = true
                        } else {
                            scope.launch {
                                val ok = NotificationApi.sendSystemNotification(
                                    username = targetUsername,
                                    title = notificationTitle,
                                    description = notificationMessage,
                                )
                                if (ok) {
                                    notificationTitle = ""
                                    notificationMessage = ""
                                    onClose()
                                }
                            }
                        }
                    }
                },
                style = DialogButtonStyle.Primary,
            ) { Translated("Send notification") }
        }
    )

    if (showBroadcastConfirm) {
        BasicConfirmDialog(
            message = translated("Send this notification to all users?"),
            onConfirm = {
                showBroadcastConfirm = false
                scope.launch {
                    val ok = NotificationApi.sendSystemNotification(
                        username = "",
                        title = notificationTitle,
                        description = notificationMessage,
                    )
                    if (ok) {
                        notificationTitle = ""
                        notificationMessage = ""
                        onClose()
                    }
                }
            },
            onCancel = { showBroadcastConfirm = false },
        )
    }
}
