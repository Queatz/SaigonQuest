package main.components

import androidx.compose.runtime.*
import main.theme.Styles
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.step
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*

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
 * @param okLabel Label for the OK button
 * @param cancelLabel Label for the Cancel button
 */
@Composable
fun BasicPromptDialog(
    title: String? = translated("Confirm"),
    message: String? = "",
    defaultValue: String = "",
    isNumber: Boolean = false,
    min: Number? = null,
    max: Number? = null,
    step: Number = 1f,
    okLabel: String? = null,
    cancelLabel: String? = null,
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

                            autoFocus()

                            ref {
                                it.focus()

                                onDispose { }
                            }

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

                            autoFocus()

                            ref {
                                it.focus()

                                onDispose { }
                            }

                            onInput { event ->
                                value = event.value
                            }
                        }
                    )
                }

                if (isNumber && max != null) {
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginTop(12.px)
                            }
                        }
                    ) {
                        DialogButton(
                            onClick = {
                                value = max.toString()
                            },
                            isPrimary = false,
                        ) {
                            Translated("All")
                        }
                    }
                }
            }
        },
        actions = {
            // Cancel button (secondary)
            DialogButton(
                onClick = onCancel,
                isPrimary = false,
            ) {
                if (cancelLabel != null) {
                    Text(cancelLabel)
                } else {
                    Translated("Cancel")
                }
            }

            // Ok button (primary)
            DialogButton(
                onClick = { onSubmit(value) },
                isPrimary = true,
            ) {
                if (okLabel != null) {
                    Text(okLabel)
                } else {
                    Translated("Ok")
                }
            }
        }
    )
}
