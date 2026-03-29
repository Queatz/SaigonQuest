package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import main.theme.OverlayStyles
import main.theme.Styles
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.random.Random

// Centralized back/stack controller for all dialogs
private object DialogBackController {
    private const val SENTINEL_STATE = "DIALOG_SENTINEL"

    private data class Entry(
        val onBack: () -> Unit,
        val escEnabled: Boolean,
    )

    private val stack = mutableListOf<Entry>()
    private var listening = false
    private var sentinelActive = false
    private var suppressNextPop = false

    private val popHandler: (Event) -> Unit = {
        // If the sentinel was on top, a back popped it
        if (sentinelActive) {
            sentinelActive = false
        }
        if (suppressNextPop) {
            suppressNextPop = false
        } else if (stack.isNotEmpty()) {
            // User pressed back: keep the sentinel on top and close only the last dialog
            pushSentinel()
            val top = stack.last()
            top.onBack()
        }
    }

    private val keydownHandler: (Event) -> Unit = keydownHandler@ { e ->
        val ke = e as? KeyboardEvent ?: return@keydownHandler
        val key = ke.key
        if (key == "Escape" || key == "Esc") {
            if (stack.isNotEmpty()) {
                val top = stack.last()
                if (top.escEnabled) {
                    ke.preventDefault()
                    top.onBack()
                }
            }
        }
    }

    private fun ensureListening() {
        if (!listening) {
            window.addEventListener("popstate", popHandler)
            window.addEventListener("keydown", keydownHandler)
            listening = true
        }
    }

    private fun pushSentinel() {
        if (!sentinelActive) {
            window.history.pushState(data = SENTINEL_STATE, title = "", url = null)
            sentinelActive = true
        }
    }

    private fun removeSentinelIfNeeded() {
        if (sentinelActive) {
            // Only pop if the current top history entry is actually our sentinel.
            val currentState = window.history.state
            if (currentState is String && currentState == SENTINEL_STATE) {
                suppressNextPop = true
                window.history.back()
            }
            // Whether we popped or not, this controller no longer keeps a sentinel active.
            sentinelActive = false
        }
    }

    fun register(onBack: () -> Unit, escEnabled: Boolean): () -> Unit {
        val entry = Entry(onBack = onBack, escEnabled = escEnabled)
        stack.add(entry)
        ensureListening()
        pushSentinel()
        return { unregister(entry) }
    }

    private fun unregister(entry: Entry) {
        stack.remove(entry)
        if (stack.isEmpty()) {
            removeSentinelIfNeeded()
        }
    }
}

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
    maxWidth: String = "calc(-2rem + 100vw)",
    isFullscreen: Boolean = false,
    clickOutside: Boolean = true,
    isFullHeight: Boolean = false,
    isFullWidth: Boolean = false,
    anchorToBottom: Boolean = false,
    banner: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    // Unique key per instance to keep portal reconciliation isolated
    val token = remember { "dlg-${Random.nextInt()}" }

    // Register this dialog with the centralized back controller
    DisposableEffect(onClose, clickOutside) {
        val unregister = DialogBackController.register(onBack = onClose, escEnabled = clickOutside)
        onDispose { unregister() }
    }

    Portal(
        key = "dialog-${token}-${title}-${maxWidth}-${isFullscreen}-${showCloseButton}",
        target = document.body!!
    ) {
        // Dialog overlay
        Div(
            attrs = {
                classes(OverlayStyles.overlayBase)
                style {
                    if (anchorToBottom && !isFullscreen) {
                        alignItems(AlignItems.FlexEnd)
                    }
                }

                if (clickOutside) {
                    onClick {
                        it.stopPropagation()
                        onClose()
                    }
                }
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
                            property("border-radius", "0")
                        } else {
                            // Apply regular styling
                            if (isFullHeight) {
                                property("height", "calc(100vh - 64px)")
                            }
                            if (isFullWidth) {
                                property("width", "calc(100vw - 64px)")
                            }
                            if (anchorToBottom) {
                                // Bottom sheet style tweaks
                                maxHeight("calc(100vh - 64px)")
                                property("width", "100%")
                                property("max-width", "100vw")
                                property("border-radius", "16px 16px 0 0")
                                property("margin-bottom", "env(safe-area-inset-bottom)")
                            } else {
                                property("max-width", maxWidth)
                                maxHeight("calc(100vh - 2rem)")
                            }
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
                                    marginLeft(16.px)
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
                                    Text("✖️")
                                }
                            }
                        }
                    }
                }
                
                // Dialog banner (if provided)
                if (banner != null) {
                    banner()
                }
                
                // Dialog content
                Div(
                    attrs = {
                        classes(Styles.baseDialogContent)

                        style {
                            if (isFullHeight) {
                                height(0.px)
                            }
                        }
                    }
                ) {
                    content()
                }
                
                // Dialog footer with actions (if provided)
                if (actions != null) {
                    Div(
                        attrs = {
                            classes(Styles.baseDialogFooter)
                            style {
                                if (anchorToBottom) {
                                    // Ensure comfortable tap area above device safe-area on mobile
                                    property("padding-bottom", "calc(16px + env(safe-area-inset-bottom))")
                                }
                            }
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
 * @param isDisabled Whether this button is disabled (default: false)
 * @param color The color of the button (default: null)
 * @param content The content of the button
 */
@Composable
fun DialogButton(
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    isDisabled: Boolean = false,
    color: String? = null,
    content: @Composable () -> Unit
) {
    Button(
        attrs = {
            if (isPrimary) {
                classes(Styles.baseDialogButtonPrimary)
            } else {
                classes(Styles.baseDialogButtonSecondary)
            }

            if (color != null) {
                style {
                    backgroundColor(Color(color))
                }
            }

            if (isDisabled) {
                disabled()
            }

            onClick { onClick() }
        }
    ) {
        content()
    }
}

@Composable
fun DialogButtonDanger(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Button(
        attrs = {
            classes(Styles.baseDialogButtonDangerPrimary)
            onClick { onClick() }
        }
    ) { content() }
}
