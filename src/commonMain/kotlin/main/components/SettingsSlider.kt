package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.attributes.disabled

/**
 * A reusable slider component for settings.
 * Displays a label, the current value as a percentage, and a range input.
 *
 * @param label The label text to display
 * @param value The current value (0.0 to 1.0)
 * @param onValueChange Callback when the value changes
 * @param min Minimum value (default 0.0)
 * @param max Maximum value (default 1.0)
 * @param step Step size (default 0.01)
 * @param id Optional ID for the input
 */
@Composable
fun SettingsSlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double = 0.0,
    max: Double = 1.0,
    step: Double = 0.01,
    disabled: Boolean = false,
    id: String? = null
) {
    Div(
        attrs = {
            classes(Styles.graphicsToggleContainer)
            style {
                flexDirection(FlexDirection.Column)
                alignItems(AlignItems.Stretch)
                if (disabled) {
                    opacity(0.6)
                    cursor("not-allowed")
                }
            }
        }
    ) {
        // Label and Percentage Row
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
                }
            }
        ) {
            Span(
                attrs = {
                    classes(Styles.graphicsToggleLabel)
                }
            ) {
                Text(label)
            }

            Span(
                attrs = {
                    classes(Styles.graphicsToggleLabel)
                    style {
                        fontSize(12.px)
                        opacity(0.8)
                    }
                }
            ) {
                Text("${(value * 100).toInt()}%")
            }
        }

        // Slider Row
        Div(
            attrs = {
                style {
                    padding(0.px, 12.px, 8.px, 12.px)
                }
            }
        ) {
            RangeInput(
                value = value,
                min = min,
                max = max,
                step = step,
                attrs = {
                    style {
                        width(100.percent)
                        if (disabled) {
                            cursor("not-allowed")
                        } else {
                            cursor("pointer")
                        }
                    }
                    id?.let { id(it) }
                    if (disabled) {
                        disabled()
                    }
                    onInput { event ->
                        onValueChange(event.value?.toDouble() ?: 0.0)
                    }
                }
            )
        }
    }
}
