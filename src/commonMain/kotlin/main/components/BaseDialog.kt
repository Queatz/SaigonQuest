package main.components

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import main.theme.Styles
import main.theme.Theme
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.dom.*

/**
 * A reusable base dialog component that provides a consistent structure and styling for dialogs.
 *
 * @param onClose Callback when the dialog is closed
 * @param title The title of the dialog (optional)
 * @param showCloseButton Whether to show the close button in the header (default: true)
 * @param headerActions Additional actions to display in the header next to the close button (optional)
 * @param content The content of the dialog
 * @param actions The actions/buttons to display in the dialog footer (optional)
 * @param maxWidth The maximum width of the dialog content (default: 400px)
 * @param isFullscreen Whether the dialog should be displayed in fullscreen mode (default: false)
 */
@Composable
fun BaseDialog(
    onClose: () -> Unit,
    title: String? = null,
    showCloseButton: Boolean = true,
    headerActions: (@Composable () -> Unit)? = null,
    maxWidth: String = "400px",
    isFullscreen: Boolean = false,

    content: @Composable () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    // Use Portal to render the dialog at the root of the DOM
    // This ensures it's not affected by parent containers
    // Create a unique key based on multiple properties to prevent duplicate dialogs during recomposition
    // Using a combination of title, maxWidth, isFullscreen, and showCloseButton to create a more unique key
    Portal(
        key = "dialog-${title}-${maxWidth}-${isFullscreen}-${showCloseButton}",
        target = document.body!!
    ) {
        // Dialog overlay
        Div(
            attrs = {
                classes(Styles.overlayBase)
                onClick { onClose() }
            }
        ) {
            // Dialog content
            Div(
                attrs = {
                    classes(Styles.baseDialogContainer)
                    style {
                        if (isFullscreen) {
                            // Apply fullscreen styling
                            property("width", "100%")
                            property("height", "100vh")
                            property("max-width", "100vw")
                            property("border-radius", "0")
                        } else {
                            // Apply regular styling
                            property("max-width", maxWidth)
                            maxHeight("calc(100vh - 64px)")
                        }
                    }
                    // Stop propagation to prevent closing when clicking inside the dialog
                    onClick { it.stopPropagation() }
                }
            ) {
                // Dialog header (if title is provided)
                if (title != null || showCloseButton) {
                    Div(
                        attrs = {
                            classes(Styles.baseDialogHeader)
                        }
                    ) {
                        // Dialog title
                        if (title != null) {
                            H3(
                                attrs = {
                                    classes(Styles.baseDialogTitle)
                                }
                            ) {
                                Text(title)
                            }
                        } else {
                            // Empty div to maintain flex layout when no title
                            Div {}
                        }
                        
                        // Header actions container (for additional buttons and close button)
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    alignItems(AlignItems.Center)
                                    property("gap", "8px")
                                }
                            }
                        ) {
                            // Additional header actions (if provided)
                            if (headerActions != null) {
                                headerActions()
                            }
                            
                            // Close button (if enabled)
                            if (showCloseButton) {
                                Button(
                                    attrs = {
                                        classes(Styles.baseDialogCloseButton)
                                        onClick { onClose() }
                                    }
                                ) {
                                    Text("×")
                                }
                            }
                        }
                    }
                }
                
                // Dialog content
                Div(
                    attrs = {
                        classes(Styles.baseDialogContent)
                    }
                ) {
                    content()
                }
                
                // Dialog footer with actions (if provided)
                if (actions != null) {
                    Div(
                        attrs = {
                            classes(Styles.baseDialogFooter)
                        }
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

/**
 * A reusable dialog button component with consistent styling.
 *
 * @param onClick Callback when the button is clicked
 * @param isPrimary Whether this is a primary button (default: false)
 * @param content The content of the button
 */
@Composable
fun DialogButton(
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    content: @Composable () -> Unit
) {
    Button(
        attrs = {
            if (isPrimary) {
                classes(Styles.baseDialogButtonPrimary)
            } else {
                classes(Styles.baseDialogButtonSecondary)
            }
            onClick { onClick() }
        }
    ) {
        content()
    }
}
