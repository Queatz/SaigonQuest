package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

/**
 * A responsive panel component that adapts between a side panel (desktop) and a dialog (mobile).
 * 
 * @param title The title to display in the panel header
 * @param isMobile Whether the current device is mobile
 * @param onClose Callback when the panel is closed
 * @param content The content to display in the panel body
 */
@Composable
fun ResponsivePanel(
    title: String,
    isMobile: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isMobile) {
        // Mobile: Dialog
        Div(
            attrs = {
                classes(Styles.overlayBase)
                onClick { onClose() }
            }
        ) {
            Div(
                attrs = {
                    classes(Styles.dialogContainer)
                    onClick { it.stopPropagation() }
                }
            ) {
                // Dialog header
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
                
                // Dialog content
                Div(
                    attrs = {
                        classes(Styles.panelContent)
                    }
                ) {
                    content()
                }
            }
        }
    } else {
        // Desktop: Side panel
        SidePanel(
            title = title,
            onClose = onClose,
            content = content
        )
    }
}
