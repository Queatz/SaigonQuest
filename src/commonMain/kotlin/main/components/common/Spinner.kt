package main.components.common

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * A reusable loading spinner component with optional message.
 *
 * @param message Optional message to display below the spinner
 */
@Composable
fun Spinner(message: String? = null) {
    Div(
        attrs = {
            classes(Styles.chatLoadingContainer)
        }
    ) {
        // Spinner element
        Div(
            attrs = {
                classes(Styles.chatLoadingSpinner)
            }
        )
        
        // Optional loading text
        if (message != null) {
            P(
                attrs = {
                    classes(Styles.chatLoadingText)
                }
            ) {
                Text(message)
            }
        }
    }
}
