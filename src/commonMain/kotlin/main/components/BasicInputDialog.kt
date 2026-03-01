package main.components

import androidx.compose.runtime.*
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Text

@Composable
fun BasicInputDialog(
    title: String,
    message: String? = null,
    placeholder: String = "",
    initialValue: String = "",
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
            Input(
                type = InputType.Text,
                attrs = {
                    classes(Styles.inputField)
                    placeholder(placeholder)
                    value(value)
                    onInput { value = it.value }
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
                isPrimary = false,
            ) {
                Text(cancelLabel)
            }
            DialogButton(
                onClick = { onConfirm(value) },
                isPrimary = true,
            ) {
                Text(confirmLabel)
            }
        }
    )
}
