package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div

/**
 * A container component that wraps the UI elements for the 3D world scene.
 * This container is positioned fixed and covers the entire viewport.
 * It has pointer-events set to none to allow interaction with the 3D scene underneath.
 */
@Composable
fun WorldUIContainer(content: @Composable () -> Unit) {
    Div(
        attrs = {
            classes(Styles.worldUIContainer)
        }
    ) {
        content()
    }
}
