package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import main.components.IconRenderer
import main.components.VoiceInput
import main.components.translated
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.attributes.AttrsScope
import org.w3c.dom.HTMLButtonElement

private fun AttrsScope<HTMLButtonElement>.title(value: String) {
    attr("title", value)
}

/**
 * A reusable toolbar for the RichTextEditor.
 * Displays various formatting and media insertion actions as icon buttons.
 */
@Composable
fun RichTextEditorToolbar(
    aiLoading: Boolean,
    showRawMd: Boolean,
    onAiClick: () -> Unit,
    onHeadingClick: (Int) -> Unit,
    onInlineClick: (String, String) -> Unit,
    onQuoteClick: () -> Unit,
    onCodeClick: () -> Unit,
    onInsertImageClick: () -> Unit,
    onInsertAudioClick: () -> Unit,
    onInsertLinkClick: () -> Unit,
    onInsertItemClick: () -> Unit,
    onToggleRawMdClick: () -> Unit,
    onVoiceInput: (String) -> Unit,
) {
    val sheetUrl = "raw/data/ui/editor.png"

    Div(attrs = {
        classes(Styles.richTextEditorToolbar)
    }) {
        // Voice input
        VoiceInput(
            small = true,
            onText = onVoiceInput,
            enabled = !aiLoading,
        )

        // AI button
        ToolbarIconButton(
            onClick = onAiClick,
            title = translated("AI"),
            disabled = aiLoading,
        ) {
            if (aiLoading) {
                Div(attrs = { classes(Styles.tinySpinner) }) {}
            } else {
                IconRenderer(
                    url = sheetUrl,
                    index = 0,
                    size = 24,
                )
            }
        }

        // Heading buttons
        ToolbarIconButton(
            onClick = { onHeadingClick(1) },
            title = "H1",
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 1,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = { onHeadingClick(2) },
            title = "H2",
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 2,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = { onHeadingClick(3) },
            title = "H3",
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 3,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = { onHeadingClick(4) },
            title = "H4",
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 4,
                size = 24,
            )
        }

        // Inline formatting
        ToolbarIconButton(
            onClick = { onInlineClick("**", "**") },
            title = translated("Bold"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 5,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = { onInlineClick("*", "*") },
            title = translated("Italic"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 6,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = { onInlineClick("<u>", "</u>") },
            title = translated("Underline"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 7,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = { onInlineClick("~~", "~~") },
            title = translated("Strikethrough"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 8,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = onQuoteClick,
            title = translated("Quote"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 9,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = onCodeClick,
            title = translated("Code"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 10,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = onInsertImageClick,
            title = translated("Insert image"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 11,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = onInsertAudioClick,
            title = translated("Insert audio"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 12,
                size = 24,
            )
        }
        ToolbarIconButton(
            onClick = onInsertLinkClick,
            title = translated("Insert link"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 13,
                size = 24,
            )
        }
        // Insert item button
        ToolbarIconButton(
            onClick = onInsertItemClick,
            title = translated("Insert item"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 16, // Item icon
                size = 24,
            )
        }

        // Toggle raw MD textarea visibility
        ToolbarIconButton(
            onClick = onToggleRawMdClick,
            title = translated(if (showRawMd) "Hide MD" else "Show MD"),
            disabled = aiLoading,
        ) {
            IconRenderer(
                url = sheetUrl,
                index = 14, // Toggle icons
                size = 24,
            )
        }
    }
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    title: String,
    disabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    Button(attrs = {
        classes(Styles.textButton)
        if (disabled) classes(Styles.disabledAction)
        attr("title", title)
        onMouseDown { it.preventDefault() }
        onClick {
            if (!disabled) {
                onClick()
            }
        }
    }) {
        content()
    }
}
