package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * Reusable labeled checkbox component.
 *
 * - Renders a checkbox with an adjacent clickable label.
 * - Respects enabled/disabled state.
 * - Emits onCheckedChange when user toggles via checkbox or label click.
 */
@Composable
fun LabeledCheckbox(
    checked: Boolean,
    enabled: Boolean = true,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Div(
        attrs = {
            // Match existing inline styling used throughout the project for consistency
            style {
                display(DisplayStyle.Flex)
                property("gap", "8px")
                property("align-items", "center")
            }
        }
    ) {
        CheckboxInput(
            checked = checked,
            attrs = {
                if (!enabled) {
                    disabled()
                }
                onChange { event ->
                    if (enabled) {
                        onCheckedChange(event.value)
                    }
                }
            }
        )

        // Clickable label/hint
        Div(
            attrs = {
                style {
                    fontSize(12.px)
                }
                onClick {
                    if (enabled) {
                        onCheckedChange(!checked)
                    }
                }
            }
        ) {
            Text(label)
        }
    }
}
