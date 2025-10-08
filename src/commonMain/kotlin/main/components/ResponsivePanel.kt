package main.components

import androidx.compose.runtime.Composable
import main.theme.OverlayStyles
import main.theme.PanelStyles
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
    onTitleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (isMobile) {
        // Mobile: Dialog
        Div(
            attrs = {
                classes(OverlayStyles.overlayBase)
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
                            Text("✖️")
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
            onTitleClick = onTitleClick,
            content = content
        )
    }
}
