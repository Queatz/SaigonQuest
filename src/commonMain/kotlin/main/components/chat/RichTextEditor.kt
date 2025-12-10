package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import main.api.AiApi
import main.components.AiPromptDialog
import main.components.AiPromptHistoryDialog
import main.components.BasicPromptDialog
import main.components.Md
import main.components.Translated
import main.components.media.UploadMediaDialog
import main.components.translated
import main.components.BasicAlertDialog
import main.components.VoiceInput
import main.components.chat.MdEditable
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
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLTextAreaElement
import web.animations.awaitAnimationFrame
import kotlinx.browser.document
import models.DocOperation
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.opacity

/**
 * A reusable rich text (markdown) editor with toolbar and live preview.
 *
 * Controlled input: pass [value] and handle changes via [onValueChange].
 */
class RichTextEditorController {
    internal var applyOpsImpl: ((List<DocOperation>, Int, Int) -> Boolean)? = null
    internal var applySnapshotImpl: ((String, Int, Int) -> Boolean)? = null
    internal var setSelectionImpl: ((Int, Int) -> Boolean)? = null
    internal var inlineWrapImpl: ((String, String) -> Boolean)? = null

    fun applyRemoteOps(ops: List<DocOperation>, newSelStart: Int, newSelEnd: Int): Boolean {
        return applyOpsImpl?.invoke(ops, newSelStart, newSelEnd) == true
    }
    fun applySnapshot(newText: String, newSelStart: Int, newSelEnd: Int): Boolean {
        return applySnapshotImpl?.invoke(newText, newSelStart, newSelEnd) == true
    }
    fun setSelection(start: Int, end: Int): Boolean {
        return setSelectionImpl?.invoke(start, end) == true
    }

    fun inlineWrap(left: String, right: String = left): Boolean {
        return inlineWrapImpl?.invoke(left, right) == true
    }
}

@Composable
fun RichTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    selection: IntRange? = null,
    onSelectionChange: (Int, Int) -> Unit = { _, _ -> },
    selectionTargetLength: Int? = null,
    controller: RichTextEditorController? = null,
) {
    var showUploadDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showRawMd by remember { mutableStateOf(false) }
    // AI integration state
    var showAiDialog by remember { mutableStateOf(false) }
    var showAiHistoryDialog by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiHistory by remember { mutableStateOf<List<models.AiPromptHistory>>(emptyList()) }
    data class AiRequest(val prompt: String, val doc: String, val s: Int, val e: Int)
    var aiRequest by remember { mutableStateOf<AiRequest?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var editorEl by remember { mutableStateOf<HTMLTextAreaElement?>(null) }
    var selStart by remember { mutableStateOf(0) }
    var selEnd by remember { mutableStateOf(0) }
    // Defer caret/selection application until after recomposition to avoid browser resetting it
    var pendingSelection by remember { mutableStateOf<IntRange?>(null) }
    // Guard to suppress recursive onInput when we imperatively mutate the textarea
    var suppressOnInput by remember { mutableStateOf(false) }

    fun setText(newText: String) {
        onValueChange(newText)
    }

    fun saveSelectionFromEl(force: Boolean = false) {
        val el = editorEl ?: return
        // If we have a pending selection to apply, don't overwrite it with a stale value
        if (!force && pendingSelection != null) return
        selStart = el.selectionStart ?: 0
        selEnd = el.selectionEnd ?: 0
        // Only notify parent on natural (non-forced) sync; forced reads are internal-only.
        if (!force) {
            try { onSelectionChange(selStart, selEnd) } catch (_: Throwable) {}
        }
    }

    // Sync selection from parent control prop, if provided
    // (moved below internalSelectionTargetLength declaration to avoid unresolved reference)

    // Track retry attempts for applying pending selection to defeat browser races
    var pendingSelectionAttempts by remember { mutableStateOf(0) }
    var isComposing by remember { mutableStateOf(false) }
    // Internal target length to gate selection application until the controlled value length matches
    var internalSelectionTargetLength by remember { mutableStateOf<Int?>(null) }

    // Sync selection from parent control prop
    LaunchedEffect(editorEl, selection, value, selectionTargetLength) {
        val sel = selection
        val el = editorEl
        if (el != null && sel != null) {
            if (pendingSelection == null && internalSelectionTargetLength == null) {
                // If parent provides a target length, only accept when it matches current value length
                val targetLen = selectionTargetLength
                if (targetLen == null || targetLen == value.length) {
                    val desired = sel.first.coerceAtLeast(0)..sel.last.coerceAtLeast(0)
                    // Avoid re-applying the same range which can cause flicker
                    if (!(selStart == desired.first && selEnd == desired.last)) {
                        pendingSelection = desired
                    }
                }
            }
        }
    }

    fun applyOpsTo(base: String, ops: List<DocOperation>): String {
        var content = base
        ops.forEach { op ->
            when (op) {
                is DocOperation.Insert -> {
                    val pos = op.position.coerceIn(0, content.length)
                    content = content.substring(0, pos) + op.text + content.substring(pos)
                }
                is DocOperation.Delete -> {
                    if (op.length > 0) {
                        val start = op.position.coerceIn(0, content.length)
                        val end = (start + op.length).coerceAtMost(content.length)
                        if (start < end) content = content.removeRange(start, end)
                    }
                }
                is DocOperation.Format -> { /* ignore for now */ }
            }
        }
        return content
    }

    // After each recomposition, if there is a pending selection to apply, set it on the element.
    LaunchedEffect(editorEl, value, pendingSelection, pendingSelectionAttempts, selectionTargetLength, internalSelectionTargetLength, isComposing) {
        val el = editorEl
        val ps = pendingSelection
        if (el != null && ps != null) {
            val len = value.length
            // If composing (IME), defer applying external selection to avoid fighting the IME
            if (isComposing) return@LaunchedEffect
            val start = ps.first.coerceIn(0, len)
            val end = ps.last.coerceIn(0, len)
            // If a target length is specified, only apply when the current value length matches it
            val targetLen = internalSelectionTargetLength ?: selectionTargetLength
            if (targetLen != null && targetLen != len) {
                // Defer until value matches the intended length
                return@LaunchedEffect
            }
            // Wait until DOM value length is ready to avoid clamped selection
            val domLen = el.value.length
            if ((targetLen != null && domLen != len) || (targetLen == null && domLen < end)) {
                return@LaunchedEffect
            }
            // If the element already has the desired selection (after length gating), skip
            val curStart = el.selectionStart ?: 0
            val curEnd = el.selectionEnd ?: 0
            if (curStart == start && curEnd == end) {
                selStart = start
                selEnd = end
                pendingSelection = null
                pendingSelectionAttempts = 0
                internalSelectionTargetLength = null
                return@LaunchedEffect
            }
            // Try immediate application (microtask), then 1 RAF retry if needed
            val isActive = (document.activeElement === el)
            if (isActive) {
                el.focus()
            }
            el.setSelectionRange(start, end)
            var appliedStart = el.selectionStart ?: 0
            var appliedEnd = el.selectionEnd ?: 0
            if ((appliedStart != start || appliedEnd != end) && pendingSelectionAttempts < 1) {
                awaitAnimationFrame()
                if (isActive) el.focus()
                el.setSelectionRange(start, end)
                appliedStart = el.selectionStart ?: 0
                appliedEnd = el.selectionEnd ?: 0
                pendingSelectionAttempts += 1
            }
            selStart = appliedStart
            selEnd = appliedEnd
            if (appliedStart == start && appliedEnd == end) {
                pendingSelection = null
                pendingSelectionAttempts = 0
                internalSelectionTargetLength = null
            } else {
                pendingSelection = start..end
            }
        }
    }

    fun applyInline(wrapperLeft: String, wrapperRight: String = wrapperLeft) {
        // Always fetch latest selection from active surface if textarea is focused
        val rawFocused = (document.activeElement === editorEl)
        if (rawFocused) saveSelectionFromEl(force = true)

        // If contenteditable surface is focused, delegate wrapping to it for exact mapping
        if (!rawFocused) {
            val handled = controller?.inlineWrap(wrapperLeft, wrapperRight) == true
            if (handled) return
        }

        val start = selStart
        val end = selEnd
        val source = if (rawFocused && editorEl != null) editorEl!!.value else value
        val before = source.substring(0, start)
        val selected = source.substring(start, end)
        val after = source.substring(end)
        val newText = before + wrapperLeft + selected + wrapperRight + after
        setText(newText)

        // If selection existed: select wrappers+content; else caret between wrappers
        val newStart = if (start != end) start else start + wrapperLeft.length
        val newEnd = if (start != end) end + wrapperLeft.length + wrapperRight.length else newStart

        // Update internal state and apply selection to the surface that is focused
        selStart = newStart
        selEnd = newEnd
        if (rawFocused && editorEl != null) {
            pendingSelection = newStart..newEnd
            internalSelectionTargetLength = newText.length
            pendingSelectionAttempts = 0
            val el = editorEl!!
            el.focus()
            val domLen = el.value.length
            if (newEnd <= domLen) {
                val cs = newStart.coerceIn(0, domLen)
                val ce = newEnd.coerceIn(0, domLen)
                el.setSelectionRange(cs, ce)
            }
        } else {
            controller?.setSelection(newStart, newEnd)
        }
    }

    fun insertAtCursor(snippet: String) {
        val rawFocused = (document.activeElement === editorEl)
        if (rawFocused) saveSelectionFromEl(force = true)
        val src = if (rawFocused && editorEl != null) editorEl!!.value else value
        val start = selStart.coerceIn(0, src.length)
        val end = selEnd.coerceIn(0, src.length)
        val before = src.substring(0, start)
        val after = src.substring(end)
        val newText = before + snippet + after
        setText(newText)
        val caret = start + snippet.length
        selStart = caret
        selEnd = caret
        if (rawFocused && editorEl != null) {
            // Defer caret placement until after recomposition and gate by expected length
            pendingSelection = caret..caret
            internalSelectionTargetLength = newText.length
            val el = editorEl!!
            el.focus()
            val clamped = caret.coerceIn(0, el.value.length)
            el.setSelectionRange(clamped, clamped)
        } else {
            controller?.setSelection(caret, caret)
        }
    }

    fun applyQuote() {
        val rawFocused = (document.activeElement === editorEl)
        if (rawFocused) saveSelectionFromEl(force = true)
        val src = if (rawFocused && editorEl != null) editorEl!!.value else value
        val start = selStart
        val end = selEnd
        val before = src.substring(0, start)
        val selected = src.substring(start, end)
        val after = src.substring(end)
        val block = if (selected.isBlank()) "> " else selected.lines().joinToString("\n") { "> $it" }
        val newText = before + block + after
        setText(newText)

        val newRange: IntRange = if (selected.isBlank()) {
            val caret = start + 2
            caret..caret
        } else {
            val linesCount = selected.lines().size
            val newStart = start + 2
            val newEnd = end + (2 * linesCount)
            newStart..newEnd
        }

        selStart = newRange.first
        selEnd = newRange.last
        if (rawFocused && editorEl != null) {
            pendingSelection = newRange
            internalSelectionTargetLength = newText.length
            pendingSelectionAttempts = 0
            val el = editorEl!!
            el.focus()
            val domLen = el.value.length
            if (newRange.last <= domLen) {
                val cs = newRange.first.coerceIn(0, domLen)
                val ce = newRange.last.coerceIn(0, domLen)
                el.setSelectionRange(cs, ce)
            }
        } else {
            controller?.setSelection(newRange.first, newRange.last)
        }
    }

    fun applyCode() {
        // Decide inline vs block code using current selection
        val rawFocused = (document.activeElement === editorEl)
        if (rawFocused) saveSelectionFromEl(force = true)
        val src = if (rawFocused && editorEl != null) editorEl!!.value else value
        val start = selStart
        val end = selEnd
        val selected = src.substring(start.coerceIn(0, src.length), end.coerceIn(0, src.length))
        if ("\n" in selected) {
            applyInline("```\n", "\n```")
        } else {
            applyInline("`", "`")
        }
    }

    fun applyHeading(level: Int) {
        val rawFocused = (document.activeElement === editorEl)
        if (rawFocused) saveSelectionFromEl(force = true)
        val currentText = if (rawFocused && editorEl != null) editorEl!!.value else value
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
        selStart = caret
        selEnd = caret
        if (rawFocused && editorEl != null) {
            pendingSelection = caret..caret
            internalSelectionTargetLength = newText.length
            editorEl!!.setSelectionRange(caret, caret)
            editorEl!!.focus()
        } else {
            controller?.setSelection(caret, caret)
        }
    }

    // Toolbar
    Div(attrs = {
        classes(Styles.richTextEditorToolbar)
    }) {
        // Voice input
        VoiceInput(
            small = true,
            onText = { text ->
                insertAtCursor(text)
            },
        )
        // AI button at start
        Button(attrs = {
            classes(Styles.primaryButton)
            if (aiLoading) classes(Styles.disabledAction)
            onMouseDown { it.preventDefault() }
            onClick {
                if (aiLoading) return@onClick
                saveSelectionFromEl(force = true)
                aiPrompt = ""
                showAiDialog = true
            }
        }) {
            if (aiLoading) {
                Div(attrs = { classes(Styles.tinySpinner) }) {}
            } else {
                Text("✨ ${translated("AI")}")
            }
        }
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
                saveSelectionFromEl(force = true)
                showUploadDialog = true
            }
        }) { Translated("Insert image") }
        Button(attrs = {
            classes(Styles.primaryButton)
            onMouseDown { it.preventDefault() }
            onClick {
                saveSelectionFromEl(force = true)
                showLinkDialog = true
            }
        }) { Translated("Insert link") }

        // Toggle raw MD textarea visibility
        Button(attrs = {
            classes(Styles.primaryButton)
            onMouseDown { it.preventDefault() }
            onClick { showRawMd = !showRawMd }
        }) {
            Translated(if (showRawMd) "Hide MD" else "Show MD")
        }
    }

    // Editor area
    if (showRawMd) {
        Div(attrs = {
            style { marginBottom(8.px) }
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

                        if (aiLoading) {
                            opacity(0.5)
                            property("pointer-events", "none")
                        }
                    }
                    autoFocus()
                    onInput {
                        if (suppressOnInput) return@onInput
                        saveSelectionFromEl()
                        setText(it.value)
                    }
                    onClick { saveSelectionFromEl(force = true) }
                    if (aiLoading) {
                        disabled()
                    }
                    onKeyDown { event ->
                        val isMod = event.ctrlKey || event.metaKey
                        if (isMod) {
                            val k = event.key.lowercase()
                            if (k == "b" || k == "i" || k == "u") {
                                event.preventDefault()
                                saveSelectionFromEl(force = true)
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
                        // Autofocus on mount; if external selection is provided, do not override it here
                        el.focus()
                        val sel = selection
                        if (sel == null) {
                            val end = value.length
                            el.setSelectionRange(end, end)
                            selStart = end
                            selEnd = end
                        } else {
                            val desired = sel.first.coerceAtLeast(0)..sel.last.coerceAtLeast(0)
                            pendingSelection = desired
                        }
                        onDispose {
                            editorEl = null
                        }
                    }
                },
            )
        }
    }

    // WYSIWYG contenteditable editor
    Div(
        attrs = {
            style {
                maxWidth(800.px)
                width(100.percent)
                alignSelf(AlignSelf.Center)
            }
        }
    ) {
        MdEditable(
            markdown = value,
            onMarkdownChange = { newMd -> setText(newMd) },
            selection = selection,
            onSelectionChange = { s, e ->
                selStart = s
                selEnd = e
                try { onSelectionChange(s, e) } catch (_: Throwable) {}
            },
            controller = controller,
            selectionTargetLength = selectionTargetLength,
            // When raw MD is visible, keep CE passive (mirror-only) to avoid value mutations and flicker
            enabled = !aiLoading && !showRawMd,
            mutatesValue = !showRawMd,
        )
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

    if (showAiDialog) {
        AiPromptDialog(
            prompt = aiPrompt,
            onPromptChange = { aiPrompt = it },
            onOpenHistory = {
                scope.launch {
                    aiLoading = true
                    try {
                        aiHistory = runCatching { AiApi.getHistory(50) }.getOrElse { emptyList() }
                        showAiHistoryDialog = true
                    } finally {
                        aiLoading = false
                    }
                }
            },
            onSubmit = {
                // Snapshot current doc and selection before starting async call
                saveSelectionFromEl(force = true)
                val s = selStart
                val e = selEnd
                val docSnapshot = value
                val userPrompt = aiPrompt
                // Close the prompt dialog immediately per requirement, show toolbar spinner during request
                showAiDialog = false
                aiLoading = true
                scope.launch {
                    try {
                        val text = AiApi.sendPrompt(
                            prompt = userPrompt,
                            docContents = docSnapshot,
                            caretStart = s,
                            caretEnd = e,
                            docId = null,
                        )
                        val before = docSnapshot.substring(0, s)
                        val after = docSnapshot.substring(e)
                        val newText = before + text + after
                        setText(newText)
                        val caret = (before + text).length
                        pendingSelection = caret..caret
                        internalSelectionTargetLength = newText.length
                        selStart = caret
                        selEnd = caret
                        // Collapse DOM selection immediately to avoid replacement on subsequent inserts
                        editorEl?.let { el ->
                            el.focus()
                            val clamped = caret.coerceIn(0, el.value.length)
                            el.setSelectionRange(clamped, clamped)
                        }
                    } catch (t: Throwable) {
                        aiError = "AI request failed or timed out"
                    } finally {
                        aiLoading = false
                    }
                }
            },
            onCancel = { showAiDialog = false },
        )
    }

    if (showAiHistoryDialog) {
        AiPromptHistoryDialog(
            items = aiHistory,
            onSelect = { selected ->
                aiPrompt = selected
                showAiHistoryDialog = false
            },
            onClose = { showAiHistoryDialog = false },
        )
    }

    if (aiError != null) {
        BasicAlertDialog(
            message = translated("AI request failed or timed out"),
            onClose = { aiError = null },
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
