package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text

/**
 * A reusable side panel component for desktop view.
 * 
 * @param title The title to display in the panel header
 * @param onClose Callback when the panel is closed
 * @param content The content to display in the panel body
 */
@Composable
fun SidePanel(
    title: String,
    onClose: () -> Unit,
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
            
            Button(
                attrs = {
                    classes(Styles.closeButton)
                    onClick { onClose() }
                }
            ) {
                Text("×")
            }
        }
        
        // Panel content
        content()
    }
}
