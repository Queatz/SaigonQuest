
package main.components

import androidx.compose.runtime.Composable
import main.utils.ellipsize
import models.AiPromptHistory
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import main.theme.Styles
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px

/**
 * Dialog listing AI prompt history items. Clicking an item selects its prompt.
 */
@Composable
fun AiPromptHistoryDialog(
    items: List<AiPromptHistory>,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    BaseDialog(
        onClose = onClose,
        title = translated("AI prompt history"),
        maxWidth = "900px",
        content = {
            if (items.isEmpty()) {
                P(attrs = { classes(Styles.chatEmptyText) }) { Translated("No items") }
            } else {
                // Scrollable list container similar to SelectDocDialog
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("flex-direction", "column")
                        gap(8.px)
                        property("margin-top", "12px")
                        property("max-height", "50vh")
                        property("overflow-y", "auto")
                    }
                }) {
                    items.forEach { h ->
                        val titleText = h.prompt
                            .trim()
                            .replace(Regex("\\s+"), " ")
                            .ellipsize(96)
                            .ifBlank { translated("Prompt") }
                        val previewText = h.responsePreview
                            .trim()
                            .replace(Regex("\\s+"), " ")
                            .ellipsize(160)
                        Paper(attrs = {
                            classes(Styles.clickable)
                            onClick { onSelect(h.prompt) }
                        }) {
                            // Title row
                            Div(attrs = {
                                style { property("font-weight", "500") }
                            }) { Text(titleText) }

                            // Preview (optional)
                            if (previewText.isNotBlank()) {
                                Div(attrs = {
                                    style {
                                        property("margin-top", "4px")
                                        property("font-size", "12px")
                                        property("opacity", "0.7")
                                    }
                                }) { Text(previewText) }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose, style = DialogButtonStyle.Primary) { Translated("Close") }
        }
    )
}
