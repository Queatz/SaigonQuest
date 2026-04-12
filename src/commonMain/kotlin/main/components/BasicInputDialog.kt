package main.components

import androidx.compose.runtime.*
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun BasicInputDialog(
    title: String,
    message: String? = null,
    placeholder: String = "",
    initialValue: String = "",
    multiline: Boolean = false,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit = onCancel,
    confirmLabel: String = translated("Confirm"),
    cancelLabel: String = translated("Cancel"),
) {
    var value by remember { mutableStateOf(initialValue) }

    BaseDialog(
        onClose = onDismiss,
        title = title,
        content = {
            if (message != null) {
                Div({
                    style {
                        marginBottom(12.px)
                    }
                }) {
                    Text(message)
                }
            }
            DialogInput(
                value = value,
                onInput = { value = it },
                placeholder = placeholder,
                multiline = multiline,
                attrs = {
                    onKeyDown {
                        if (it.key == "Enter") {
                            onConfirm(value)
                        }
                    }
                }
            )
        },
        actions = {
            DialogButton(
                onClick = onCancel,
                style = DialogButtonStyle.Secondary,
            ) {
                Text(cancelLabel)
            }
            DialogButton(
                onClick = { onConfirm(value) },
                style = DialogButtonStyle.Primary,
            ) {
                Text(confirmLabel)
            }
        }
    )
}
