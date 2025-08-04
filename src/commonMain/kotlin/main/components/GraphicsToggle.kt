package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import main.theme.Theme
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
fun GraphicsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    id: String? = null
) {
    // Container for the toggle and label
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                padding(8.px)
                marginBottom(16.px)
                backgroundColor(Theme.colors.surfaceVariant)
                property("border-radius", "4px")
            }
        }
    ) {
        // Label
        Span(
            attrs = {
                style {
                    padding(8.px)
                    color(Theme.colors.onSurfaceVariant)
                }
            }
        ) {
            Text(label)
        }
        
        // Toggle button
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    padding(8.px)
                }
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
