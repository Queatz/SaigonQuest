package main.components.`object`

import androidx.compose.runtime.Composable
import main.theme.Theme
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * A reusable tab navigation component that accepts a content block for tabs
 * 
 * @param content The content block that will render the tabs
 */
@Composable
fun TabNavigation(
    content: @Composable () -> Unit
) {
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                width(100.percent)
                property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
            }
        }
    ) {
        content()
    }
}

/**
 * A single tab item in the tab navigation
 * 
 * @param label The text to display in the tab
 * @param isSelected Whether this tab is currently selected
 * @param onClick Callback when the tab is clicked
 */
@Composable
fun TabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Div(
        attrs = {
            style {
                padding(12.px, 16.px)
                cursor("pointer")
                if (isSelected) {
                    property("border-bottom", "2px solid ${Theme.colors.primary}")
                    color(Theme.colors.primary)
                } else {
                    color(Color.white)
                }
            }
            onClick { onClick() }
        }
    ) {
        Text(label)
    }
}
