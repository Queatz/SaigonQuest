package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.max
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.attributes.step
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.TextInput

/**
 * A basic prompt dialog that allows for text or number input with a default value.
 *
 * @param title Optional title for the dialog (should be translated by caller when possible)
 * @param message Message to display above the input (should be translated by caller)
 * @param defaultValue The initial value prefilled in the input
 * @param isNumber If true, uses a number input; otherwise, a text input
 * @param step Step value for number input, defaults to 0.1
 * @param onSubmit Called with the entered value when the user confirms
 * @param onCancel Called when the user cancels or closes the dialog
 */
@Composable
fun BasicPromptDialog(
    title: String? = translated("Confirm"),
    message: String? = "",
    defaultValue: String = "",
    isNumber: Boolean = false,
    min: Number? = null,
    max: Number? = null,
    step: Number = 0.001,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(defaultValue) { mutableStateOf(defaultValue) }

    BaseDialog(
        onClose = onCancel,
        title = title,
        content = {
            // Message label
            message?.takeIf {
                it.isNotBlank()
            }?.let {
                Label {
                    Text(message)
                }
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
                if (isNumber) {
                    NumberInput(
                        value = value.toDoubleOrNull(),
                        min = min,
                        max = max,
                        attrs = {
                            classes(Styles.inputField)

                            step(step)

                            onInput { event ->
                                value = event.value.toString()
                            }
                        }
                    )
                } else {
                    TextInput(
                        value = value,
                        attrs = {
                            classes(Styles.inputField)

                            onInput { event ->
                                value = event.value
                            }
                        }
                    )
                }
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
