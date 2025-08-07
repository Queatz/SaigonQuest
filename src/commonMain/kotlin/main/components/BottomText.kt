package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import main.components.Translated

/**
 * A component that displays the copyright text at the bottom left of the screen.
 * This text is positioned absolutely at the bottom left corner with a small font size.
 */
@Composable
fun BottomText() {
    Div(
        attrs = {
            classes(Styles.bottomText)
        }
    ) {
        Translated("© 2025 Saigon.Quest v0.1")
    }
}
