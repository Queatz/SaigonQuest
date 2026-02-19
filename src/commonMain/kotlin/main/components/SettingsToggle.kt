package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.*

/**
 * A reusable graphics toggle component that can be used for various graphics settings.
 * This component displays a label and a toggle switch for a graphics setting.
 * 
 * @param label The label text to display for the toggle
 * @param checked Whether the toggle is checked (on) or not (off)
 * @param onCheckedChange Callback function that is called when the toggle is toggled
 * @param id Optional ID for the toggle switch (useful for accessibility)
 */
@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    id: String? = null
) {
    // Container for the toggle and label
    Div(
        attrs = {
            classes(Styles.graphicsToggleContainer)
        }
    ) {
        // Label
        Span(
            attrs = {
                classes(Styles.graphicsToggleLabel)
            }
        ) {
            Text(label)
        }

        // Toggle button container
        Div(
            attrs = {
                classes(Styles.graphicsToggleControl)
            }
        ) {
            ToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                id = id
            )
        }
    }
}
