package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import main.components.BaseDialog
import main.components.BasicPromptDialog
import main.components.DialogButton
import main.components.IconRenderer
import main.components.Md
import main.components.Translated
import main.components.translated
import main.components.media.UploadMediaDialog
import main.theme.Styles
import main.utils.toggleFullscreen
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignSelf
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.alignSelf
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.maxWidth
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.flexWrap
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLTextAreaElement

/**
 * A rich text (markdown) editor dialog for composing chat messages.
 *
 * Toolbar functions insert markdown at the current cursor position in the editor.
 * A live preview below provides a WYSIWYG-like experience using the `marked` renderer.
 */
@Composable
fun RichTextEditorDialog(
    onClose: () -> Unit,
    onSend: (String) -> Unit,
    initialText: String = "",
) {
    var text by remember { mutableStateOf(initialText) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var editorEl by remember { mutableStateOf<HTMLTextAreaElement?>(null) }
    var selStart by remember { mutableStateOf(0) }
    var selEnd by remember { mutableStateOf(0) }

    fun saveSelectionFromEl() {
        val el = editorEl ?: return
        selStart = el.selectionStart ?: 0
        selEnd = el.selectionEnd ?: 0
    }

    // Keep in sync with fullscreen state to mirror LogicEditorDialog behavior
    DisposableEffect(Unit) {
        val listener: (dynamic) -> Unit = { isFullscreen = document.fullscreenElement != null }
        document.addEventListener("fullscreenchange", listener)
        onDispose { document.removeEventListener("fullscreenchange", listener) }
    }

    fun applyInline(wrapperLeft: String, wrapperRight: String = wrapperLeft) {
        val el = editorEl ?: return
        val start = selStart
        val end = selEnd
        val before = text.substring(0, start)
        val selected = text.substring(start, end)
        val after = text.substring(end)
        val newText = before + wrapperLeft + selected + wrapperRight + after
        text = newText
        // Restore selection to inside wrapped text
        val newStart = start + wrapperLeft.length
        val newEnd = newStart + selected.length
        el.setSelectionRange(newStart, newEnd)
        el.focus()
        // Update saved selection
        selStart = newStart
        selEnd = newEnd
    }

    fun insertAtCursor(snippet: String) {
        val el = editorEl ?: return
        val start = selStart
        val end = selEnd
        val before = text.substring(0, start)
        val after = text.substring(end)
        val newText = before + snippet + after
        text = newText
        val caret = (before + snippet).length
        el.setSelectionRange(caret, caret)
        el.focus()
        selStart = caret
        selEnd = caret
    }

    fun applyQuote() {
        val el = editorEl ?: return
        val start = selStart
        val end = selEnd
        val before = text.substring(0, start)
        val selected = text.substring(start, end)
        val after = text.substring(end)
        val block = if (selected.isBlank()) "> " else selected.lines().joinToString("\n") { "> $it" }
        text = before + block + after
        val caret = (before + block).length
        el.setSelectionRange(caret, caret)
        el.focus()
        selStart = caret
        selEnd = caret
    }

    fun applyCode() {
        val start = selStart
        val end = selEnd
        val selected = text.substring(start, end)
        if ("\n" in selected) {
            applyInline("```\n", "\n```")
        } else {
            applyInline("`", "`")
        }
    }

    fun applyHeading(level: Int) {
        val el = editorEl ?: return
        val currentText = text
        val startIdx = selStart.coerceIn(0, currentText.length)
        val endIdx = selEnd.coerceIn(0, currentText.length)

        // Find line boundaries
        val lineStart = currentText.lastIndexOf('\n', startIdx - 1).let { if (it == -1) 0 else it + 1 }
        val lineEndSearchStart = if (endIdx > 0) endIdx - 1 else 0
        val nextNewline = currentText.indexOf('\n', lineEndSearchStart)
        val lineEnd = if (nextNewline == -1) currentText.length else nextNewline

        val block = currentText.substring(lineStart, lineEnd)
        val lines = block.split('\n')
        val prefix = "#".repeat(level) + " "
        val headingRegex = Regex("^\\s{0,3}#{1,6}\\s+")
        val newLines = lines.map { line ->
            val stripped = line.replace(headingRegex, "")
            prefix + stripped
        }
        val newBlock = newLines.joinToString("\n")
        val newText = currentText.substring(0, lineStart) + newBlock + currentText.substring(lineEnd)
        text = newText

        // Place caret at end of transformed block
        val caret = lineStart + newBlock.length
        el.setSelectionRange(caret, caret)
        el.focus()
        selStart = caret
        selEnd = caret
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Write chat message"),
        maxWidth = "920px",
        isFullHeight = true,
        isFullscreen = isFullscreen,
        // Fullscreen toggle like LogicEditorDialog
        headerActions = {
            Button(
                attrs = {
                    classes(Styles.textButton)
                    onClick { toggleFullscreen() }
                }
            ) {
                IconRenderer(
                    url = "raw/data/ui/ui.png",
                    index = if (isFullscreen) 1 else 0,
                    size = 24
                )
            }
        },
        content = {
            // Toolbar
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    gap(8.px)
                    marginBottom(8.px)
                    flexWrap(FlexWrap.Wrap)
                }
            }) {
                // Heading buttons
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyHeading(1) }
                }) { Translated("H1") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyHeading(2) }
                }) { Translated("H2") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyHeading(3) }
                }) { Translated("H3") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyHeading(4) }
                }) { Translated("H4") }

                // Inline formatting
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyInline("**") }
                }) { Translated("Bold") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyInline("*") }
                }) { Translated("Italic") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyInline("<u>", "</u>") }
                }) { Translated("Underline") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyInline("~~") }
                }) { Translated("Strikethrough") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyQuote() }
                }) { Translated("Quote") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick { applyCode() }
                }) { Translated("Code") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick {
                        saveSelectionFromEl()
                        showUploadDialog = true
                    }
                }) { Translated("Insert image") }
                Button(attrs = {
                    classes(Styles.primaryButton)
                    onMouseDown { it.preventDefault() }
                    onClick {
                        saveSelectionFromEl()
                        showLinkDialog = true
                    }
                }) { Translated("Insert link") }
            }

            // Editor area
            Div(attrs = {
                style {
                    marginBottom(8.px)
                }
            }) {
                val placeholderText = translated("Write chat message")
                TextArea(
                    value = text,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(placeholderText)
                        style {
                            height(160.px)
                            padding(8.px)
                        }
                        autoFocus()
                        onInput {
                            text = it.value
                            saveSelectionFromEl()
                        }
                        onClick { saveSelectionFromEl() }
                        onKeyDown { event ->
                            // Keyboard shortcuts: Ctrl/Cmd + B/I/U
                            val isMod = event.ctrlKey || event.metaKey
                            if (isMod) {
                                val k = event.key.lowercase()
                                if (k == "b" || k == "i" || k == "u") {
                                    event.preventDefault()
                                    saveSelectionFromEl()
                                    when (k) {
                                        "b" -> applyInline("**")
                                        "i" -> applyInline("*")
                                        "u" -> applyInline("<u>", "</u>")
                                    }
                                }
                            }
                        }
                        onKeyUp { saveSelectionFromEl() }
                        ref { el ->
                            editorEl = el
                            // Autofocus on mount and set caret at end
                            el.focus()
                            val end = text.length
                            el.setSelectionRange(end, end)
                            selStart = end
                            selEnd = end
                            onDispose { editorEl = null }
                        }
                    },
                )
            }

            // WYSIWYG preview using Md
            Div(
                attrs = {
                    style {
                        maxWidth(800.px)
                        alignSelf(AlignSelf.Center)
                    }
                }
            ) {
                Md(text)
            }

            if (showUploadDialog) {
                UploadMediaDialog(
                    onClose = { showUploadDialog = false },
                    onSelect = { imageUrl ->
                        // Insert markdown image syntax at cursor
                        val alt = "image"
                        insertAtCursor("![$alt]($imageUrl)")
                        showUploadDialog = false
                    },
                )
            }

            if (showLinkDialog) {
                BasicPromptDialog(
                    title = translated("Insert link"),
                    message = translated("Enter URL"),
                    defaultValue = "https://",
                    onSubmit = { url ->
                        val start = selStart
                        val end = selEnd
                        val selected = text.substring(start, end)
                        val label = selected.ifBlank { url }
                        insertAtCursor("[$label]($url)")
                        showLinkDialog = false
                    },
                    onCancel = { showLinkDialog = false },
                )
            }
        },
        actions = {
            DialogButton(
                onClick = onClose,
            ) { Translated("Cancel") }
            DialogButton(
                onClick = { if (text.isNotBlank()) onSend(text) },
                isPrimary = true,
            ) { Translated("Send") }
        },
    )
}
