package main.components

import androidx.compose.runtime.Composable
import main.components.IconRenderer
import main.theme.PanelStyles
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
    headerActions: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    FramedBorder(
        attrs = {
            classes(PanelStyles.sidePanel)
        }
    ) {
        // Panel header
        Div(
            attrs = {
                classes(PanelStyles.panelHeader)
            }
        ) {
            H3(
                attrs = {
                    classes(PanelStyles.panelTitle)
                }
            ) {
                Text(title)
            }
            // Right side: hint + close
            Div(attrs = { classes(PanelStyles.panelHeaderRight) }) {
                if (headerActions != null) {
                    headerActions()
                }
                if (headerRight != null) {
                    Div(attrs = {
                        classes(PanelStyles.panelHeaderHint)
                        if (onTitleClick != null) {
                            onClick { onTitleClick() }
                        }
                    }) { headerRight() }
                }
                Button(
                    attrs = {
                        classes(PanelStyles.closeButton)
                        onClick { onClose() }
                    }
                ) {
                    IconRenderer(
                        url = "raw/data/ui/ui.png",
                        index = 20,
                        size = 24,
                    )
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
