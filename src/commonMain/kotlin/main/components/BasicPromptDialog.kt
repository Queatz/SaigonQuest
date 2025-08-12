package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px

/**
 * A basic prompt dialog that allows for text or number input with a default value.
 *
 * @param title Optional title for the dialog (should be translated by caller when possible)
 * @param message Message to display above the input (should be translated by caller)
 * @param defaultValue The initial value prefilled in the input
 * @param isNumber If true, uses a number input; otherwise, a text input
 * @param onSubmit Called with the entered value when the user confirms
 * @param onCancel Called when the user cancels or closes the dialog
 */
@Composable
fun BasicPromptDialog(
    title: String? = translated("Confirm"),
    message: String,
    defaultValue: String = "",
    isNumber: Boolean = false,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(defaultValue) { mutableStateOf(defaultValue) }

    BaseDialog(
        onClose = onCancel,
        title = title,
        content = {
            // Message label
            Label {
                Text(message)
            }
            // Input field
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Block)
                        marginBottom(12.px)
                    }
                }
            ) {
                Input(
                    type = if (isNumber) InputType.Number else InputType.Text,
                    attrs = {
                        value(value)
                        onInput { ev ->
                            value = ev.value.toString()
                        }
                    }
                )
            }
        },
        actions = {
            // Cancel button (secondary)
            DialogButton(
                onClick = onCancel,
                isPrimary = false,
            ) {
                Translated("Cancel")
            }

            // Ok button (primary)
            DialogButton(
                onClick = { onSubmit(value) },
                isPrimary = true,
            ) {
                Translated("Ok")
            }
        }
    )
}
