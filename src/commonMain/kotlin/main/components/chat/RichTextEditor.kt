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

    fun applyRemoteOps(ops: List<DocOperation>, newSelStart: Int, newSelEnd: Int): Boolean {
        return applyOpsImpl?.invoke(ops, newSelStart, newSelEnd) == true
    }
    fun applySnapshot(newText: String, newSelStart: Int, newSelEnd: Int): Boolean {
        return applySnapshotImpl?.invoke(newText, newSelStart, newSelEnd) == true
    }
    fun setSelection(start: Int, end: Int): Boolean {
        return setSelectionImpl?.invoke(start, end) == true
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

    fun saveSelectionFromEl() {
        val el = editorEl ?: return
        // If we have a pending selection to apply, don't overwrite it with a stale value
        if (pendingSelection != null) return
        selStart = el.selectionStart ?: 0
        selEnd = el.selectionEnd ?: 0
        try { onSelectionChange(selStart, selEnd) } catch (_: Throwable) {}
    }

    // Sync selection from parent control prop, if provided
    LaunchedEffect(editorEl, selection, value) {
        val sel = selection
        val el = editorEl
        if (el != null && sel != null) {
            val desired = sel.first.coerceAtLeast(0)..sel.last.coerceAtLeast(0)
            // Always re-apply selection after value/value-length changes to prevent caret jumps (Firefox)
            pendingSelection = desired
        }
    }

    // Track retry attempts for applying pending selection to defeat browser races
    var pendingSelectionAttempts by remember { mutableStateOf(0) }
    var isComposing by remember { mutableStateOf(false) }
    // Internal target length to gate selection application until the controlled value length matches
    var internalSelectionTargetLength by remember { mutableStateOf<Int?>(null) }

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
            // If the element already has the desired selection, skip
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
            // If a target length is specified, only apply when the current value length matches it
            val targetLen = internalSelectionTargetLength ?: selectionTargetLength
            if (targetLen != null && targetLen != len) {
                // Defer until value matches the intended length
                return@LaunchedEffect
            }
            // Try immediate application (microtask), then 1 RAF retry if needed
            // Only focus if this textarea is already the active element to avoid focus churn
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
                // keep pendingSelection to try again on next state change
                pendingSelection = start..end
            }
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
        internalSelectionTargetLength = newText.length
        // Keep internal selection state in sync
        selStart = caret
        selEnd = caret
        // Keep focus on the editor and immediately collapse selection
        el.focus()
        val clamped = caret.coerceIn(0, el.value.length)
        el.setSelectionRange(clamped, clamped)
    }

    fun insertAtCursor(snippet: String) {
        val el = editorEl ?: return
        // Refresh selection to support replacing the current selection
        saveSelectionFromEl()
        val domValue = el.value
        val start = selStart.coerceIn(0, domValue.length)
        val end = selEnd.coerceIn(0, domValue.length)
        val before = domValue.substring(0, start)
        val after = domValue.substring(end)
        val newText = before + snippet + after
        setText(newText)
        val caret = start + snippet.length
        // Defer caret placement until after recomposition and gate by expected length
        pendingSelection = caret..caret
        internalSelectionTargetLength = newText.length
        // Keep internal selection state in sync
        selStart = caret
        selEnd = caret
        // Keep focus on the editor and immediately collapse selection to caret to avoid selecting inserted text
        el.focus()
        val clamped = caret.coerceIn(0, el.value.length)
        el.setSelectionRange(clamped, clamped)
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
        // Defer caret placement until after recomposition and gate by length
        pendingSelection = caret..caret
        internalSelectionTargetLength = newText.length
        // Keep internal selection state in sync
        selStart = caret
        selEnd = caret
        // Keep focus on the editor and collapse selection
        el.focus()
        val clamped = caret.coerceIn(0, el.value.length)
        el.setSelectionRange(clamped, clamped)
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
        pendingSelection = caret..caret
        internalSelectionTargetLength = newText.length
        el.setSelectionRange(caret, caret)
        el.focus()
        selStart = caret
        selEnd = caret
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
                saveSelectionFromEl()
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

                    if (aiLoading) {
                        opacity(0.5)
                        property("pointer-events", "none")
                    }
                }
                autoFocus()
                onInput {
                    if (suppressOnInput) return@onInput
                    // Capture current selection BEFORE mutating value so we can preserve caret accurately
                    saveSelectionFromEl()
                    setText(it.value)
                }
                onClick { saveSelectionFromEl() }
                if (aiLoading) {
                    disabled()
                }
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
                    // Wire controller bindings if provided
                    controller?.apply {
                        applyOpsImpl = { ops, newSelStart, newSelEnd ->
                            val textarea = editorEl
                            if (textarea == null || isComposing) {
                                false
                            } else {
                                val base = textarea.value
                                val newVal = applyOpsTo(base, ops)
                                suppressOnInput = true
                                try {
                                    textarea.value = newVal
                                    val s = newSelStart.coerceIn(0, newVal.length)
                                    val e = newSelEnd.coerceIn(0, newVal.length)
                                    val isActive = (document.activeElement === textarea)
                                    if (isActive) textarea.focus()
                                    textarea.setSelectionRange(s, e)
                                    selStart = textarea.selectionStart ?: s
                                    selEnd = textarea.selectionEnd ?: e
                                    // Defer selection stabilization until controlled value matches newVal
                                    pendingSelection = s..e
                                    internalSelectionTargetLength = newVal.length
                                    pendingSelectionAttempts = 0
                                    // Parent is responsible for syncing state to avoid unintended saves
                                    true
                                } finally {
                                    suppressOnInput = false
                                }
                            }
                        }
                        applySnapshotImpl = { newText, newSelStart, newSelEnd ->
                            val textarea = editorEl
                            if (textarea == null || isComposing) {
                                false
                            } else {
                                suppressOnInput = true
                                try {
                                    textarea.value = newText
                                    val s = newSelStart.coerceIn(0, newText.length)
                                    val e = newSelEnd.coerceIn(0, newText.length)
                                    val isActive = (document.activeElement === textarea)
                                    if (isActive) textarea.focus()
                                    textarea.setSelectionRange(s, e)
                                    selStart = textarea.selectionStart ?: s
                                    selEnd = textarea.selectionEnd ?: e
                                    // Defer selection stabilization until controlled value matches newText
                                    pendingSelection = s..e
                                    internalSelectionTargetLength = newText.length
                                    pendingSelectionAttempts = 0
                                    // Sync compose state
                                    onValueChange(newText)
                                    true
                                } finally {
                                    suppressOnInput = false
                                }
                            }
                        }
                        setSelectionImpl = { s0, e0 ->
                            val textarea = editorEl
                            if (textarea == null) {
                                false
                            } else {
                                val len = textarea.value.length
                                val s = s0.coerceIn(0, len)
                                val e = e0.coerceIn(0, len)
                                val isActive = (document.activeElement === textarea)
                                if (isActive) textarea.focus()
                                textarea.setSelectionRange(s, e)
                                selStart = textarea.selectionStart ?: s
                                selEnd = textarea.selectionEnd ?: e
                                // Defer selection stabilization until the controlled value reflects the DOM
                                pendingSelection = s..e
                                internalSelectionTargetLength = len
                                pendingSelectionAttempts = 0
                                true
                            }
                        }
                    }

                    // Autofocus on mount; if external selection is provided, do not override it here
                    el.focus()
                    val sel = selection
                    if (sel == null) {
                        val end = value.length
                        el.setSelectionRange(end, end)
                        selStart = end
                        selEnd = end
                    } else {
                        // Defer selection application to the pendingSelection effect
                        val desired = sel.first.coerceAtLeast(0)..sel.last.coerceAtLeast(0)
                        pendingSelection = desired
                    }
                    onDispose {
                        // Clear controller bindings
                        controller?.apply {
                            applyOpsImpl = null
                            applySnapshotImpl = null
                            setSelectionImpl = null
                        }
                        editorEl = null
                    }
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
                saveSelectionFromEl()
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
