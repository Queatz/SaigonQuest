package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text

/**
 * Header component for the logic editor dialog.
 * 
 * @param title The title to display in the header
 * @param isFullscreen Whether the dialog is in fullscreen mode
 * @param onToggleFullscreen Callback when the fullscreen button is clicked
 * @param onClose Callback when the close button is clicked
 */
@Composable
fun DialogHeader(
    title: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    Div(
        attrs = {
            classes(NodeEditorStyles.dialogHeader)
        }
    ) {
        H3(
            attrs = {
                classes(NodeEditorStyles.dialogTitle)
            }
        ) {
            Text(title)
        }
        
        // Container for buttons to group them together
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }
        ) {
            // Fullscreen button
            Button(
                attrs = {
                    classes(NodeEditorStyles.closeButton)
                    onClick { onToggleFullscreen() }
                }
            ) {
                Text(if (isFullscreen) "⤓" else "⤢")
            }
                
            Button(
                attrs = {
                    classes(NodeEditorStyles.closeButton)
                    onClick { onClose() }
                }
            ) {
                Text("×")
            }
        }
    }
}
