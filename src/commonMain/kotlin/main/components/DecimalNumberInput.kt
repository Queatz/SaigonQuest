package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Input

/**
 * Reusable decimal number input that preserves transient user input states
 * like trailing decimal points (e.g., "10.") or just a minus sign ("-").
 *
 * This is implemented using a text input and client-side validation so that
 * we don't lose the trailing dot while typing, which is a common issue when
 * binding directly to numeric types.
 */
@Composable
fun DecimalNumberInput(
    value: String,
    enabled: Boolean = true,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    Input(
        type = InputType.Text,
        attrs = {
            classes(Styles.inputField)
            placeholder?.let { placeholder(it) }
            if (!enabled) {
                disabled()
            }
            // Hint mobile keyboards to show a numeric keypad with decimal
            attr("inputmode", "decimal")
            // Controlled value as string to preserve transient states
            value(value)
            // Accept only valid partial decimal numbers
            onInput { event ->
                val next = event.value
                if (isValidPartialDecimal(next)) {
                    onChange(next)
                }
            }
        }
    )
}

private fun isValidPartialDecimal(text: String): Boolean {
    if (text.isEmpty()) return true

    var start = 0
    if (text[0] == '-') {
        // Leading minus allowed once at the start
        start = 1
        if (text.length == 1) return true
    }

    var dotCount = 0
    for (i in start until text.length) {
        val c = text[i]
        if (c == '.') {
            dotCount++
            if (dotCount > 1) return false
        } else if (!c.isDigit()) {
            return false
        }
    }
    return true
}
