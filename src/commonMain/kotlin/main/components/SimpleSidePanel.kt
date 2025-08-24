package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

/**
 * A simple side panel component that displays a panel with a title, close button, and content.
 * This is a simplified version of SidePanel used for basic panel display.
 * 
 * @param title The title to display in the panel header
 * @param onClose Callback when the panel is closed
 * @param content The content to display in the panel body
 */
@Composable
fun SimpleSidePanel(
    title: String,
    onClose: () -> Unit,
    headerRight: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Div(
        attrs = {
            classes(Styles.sidePanel)
        }
    ) {
        // Panel header
        Div(
            attrs = {
                classes(Styles.panelHeader)
            }
        ) {
            H3(
                attrs = {
                    classes(Styles.panelTitle)
                }
            ) {
                Text(title)
            }
            // Right side: hint + close
            Div(attrs = { classes(Styles.panelHeaderRight) }) {
                if (headerRight != null) {
                    Div(attrs = { classes(Styles.panelHeaderHint) }) { headerRight() }
                }
                Button(
                    attrs = {
                        classes(Styles.closeButton)
                        onClick { onClose() }
                    }
                ) {
                    Text("✖️")
                }
            }
        }
        
        // Panel content
        Div(
            attrs = {
                classes(Styles.panelContent)
            }
        ) {
            content()
        }
    }
}
