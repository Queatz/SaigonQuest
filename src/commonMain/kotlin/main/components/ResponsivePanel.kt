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
    headerRight: (@Composable () -> Unit)? = null,
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
                            Text("×")
                        }
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
        SimpleSidePanel(
            title = title,
            onClose = onClose,
            headerRight = headerRight,
            content = content
        )
    }
}
