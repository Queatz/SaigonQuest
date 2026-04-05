package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.*
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
    FramedBorder(
        borderImage = "/raw/data/ui/borders/button_secondary.png",
        backgroundImage = "/raw/data/ui/backgrounds/button_secondary.png",
        attrs = {
            classes(Styles.graphicsToggleContainer)
            classes(Styles.baseDialogButtonSecondary)

            // Ensure the framed border stays visible and behaves like a button
            style {
                padding(6.px, 18.px)
                property("border-width", "6px")
                property("border-image-width", "6px")
                property("border-style", "solid")
                property("border-radius", "0")
                property("box-shadow", "none")
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                property("cursor", "pointer")
                property("user-select", "none")
            }

            onClick {
                onCheckedChange(!checked)
            }
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
