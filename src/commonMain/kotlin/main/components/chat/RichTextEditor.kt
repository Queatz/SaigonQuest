package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.BasicPromptDialog
import main.components.Md
import main.components.Translated
import main.components.media.UploadMediaDialog
import main.components.translated
import main.theme.Styles
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignSelf
import org.jetbrains.compose.web.css.alignSelf
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.maxWidth
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLTextAreaElement
import web.animations.awaitAnimationFrame
import kotlinx.coroutines.delay

/**
 * A reusable rich text (markdown) editor with toolbar and live preview.
 *
 * Controlled input: pass [value] and handle changes via [onValueChange].
 */
@Composable
fun RichTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
) {
    var showUploadDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var editorEl by remember { mutableStateOf<HTMLTextAreaElement?>(null) }
    var selStart by remember { mutableStateOf(0) }
    var selEnd by remember { mutableStateOf(0) }
    // Defer caret/selection application until after recomposition to avoid browser resetting it
    var pendingSelection by remember { mutableStateOf<IntRange?>(null) }

    fun setText(newText: String) {
        onValueChange(newText)
    }

    fun saveSelectionFromEl() {
        val el = editorEl ?: return
        // If we have a pending selection to apply, don't overwrite it with a stale value
        if (pendingSelection != null) return
        selStart = el.selectionStart ?: 0
        selEnd = el.selectionEnd ?: 0
    }

    // After each recomposition, if there is a pending selection to apply, set it on the element.
    LaunchedEffect(editorEl, value, pendingSelection) {
        val el = editorEl
        val ps = pendingSelection
        if (el != null && ps != null) {
            val len = value.length
            val start = ps.first.coerceIn(0, len)
            val end = ps.last.coerceIn(0, len)
            // Wait for DOM commit: multiple frames + microtask
            awaitAnimationFrame()
            awaitAnimationFrame()
            awaitAnimationFrame()
            delay(0)
            el.focus()
            el.setSelectionRange(start, end)
            selStart = start
            selEnd = end
            pendingSelection = null
        }
    }

    fun applyInline(wrapperLeft: String, wrapperRight: String = wrapperLeft) {
        val el = editorEl ?: return
        // Always refresh selection from the live element to avoid stale values
        saveSelectionFromEl()
        val start = selStart
        val end = selEnd
        val before = value.substring(0, start)
        val selected = value.substring(start, end)
        val after = value.substring(end)
        val newText = before + wrapperLeft + selected + wrapperRight + after
        setText(newText)
        // Defer caret placement until after recomposition to prevent browser reset
        val caret = start + wrapperLeft.length + selected.length + wrapperRight.length
        pendingSelection = caret..caret
        // Keep internal selection state in sync
        selStart = caret
        selEnd = caret
        // Keep focus on the editor
        el.focus()
    }

    fun insertAtCursor(snippet: String) {
        val el = editorEl ?: return
        // Refresh selection to support replacing the current selection
        saveSelectionFromEl()
        val start = selStart
        val end = selEnd
        val before = value.substring(0, start)
        val after = value.substring(end)
        val newText = before + snippet + after
        setText(newText)
        val caret = start + snippet.length
        // Defer caret placement until after recomposition
        pendingSelection = caret..caret
        // Keep internal selection state in sync
        selStart = caret
        selEnd = caret
        // Keep focus on the editor
        el.focus()
    }

    fun applyQuote() {
        val el = editorEl ?: return
        // Ensure we operate on the latest selection
        saveSelectionFromEl()
        val start = selStart
        val end = selEnd
        val before = value.substring(0, start)
        val selected = value.substring(start, end)
        val after = value.substring(end)
        val block = if (selected.isBlank()) "> " else selected.lines().joinToString("\n") { "> $it" }
        val newText = before + block + after
        setText(newText)
        val caret = (before + block).length
        // Defer caret placement until after recomposition
        pendingSelection = caret..caret
        // Keep internal selection state in sync
        selStart = caret
        selEnd = caret
        // Keep focus on the editor
        el.focus()
    }

    fun applyCode() {
        // Ensure we use the current selection to decide inline vs block code
        saveSelectionFromEl()
        val start = selStart
        val end = selEnd
        val selected = value.substring(start, end)
        if ("\n" in selected) {
            applyInline("```\n", "\n```")
        } else {
            applyInline("`", "`")
        }
    }

    fun applyHeading(level: Int) {
        val el = editorEl ?: return
        // Refresh selection from element first
        saveSelectionFromEl()
        val currentText = value
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
        setText(newText)

        // Place caret at end of transformed block
        val caret = lineStart + newBlock.length
        el.setSelectionRange(caret, caret)
        el.focus()
        selStart = caret
        selEnd = caret
    }

    // Toolbar
    Div(attrs = {
        classes(Styles.richTextEditorToolbar)
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
        val placeholderText = placeholder ?: translated("Write chat message")
        TextArea(
            value = value,
            attrs = {
                classes(Styles.inputField)
                placeholder(placeholderText)
                style {
                    height(160.px)
                    padding(8.px)
                }
                autoFocus()
                onInput {
                    setText(it.value)
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
                    val end = value.length
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
                width(100.percent)
                alignSelf(AlignSelf.Center)
            }
        }
    ) {
        Md(value)
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
                val selected = value.substring(start, end)
                val label = selected.ifBlank { url }
                insertAtCursor("[$label]($url)")
                showLinkDialog = false
            },
            onCancel = { showLinkDialog = false },
        )
    }
}
