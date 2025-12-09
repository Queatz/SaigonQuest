package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import main.components.markdownToHtml
import main.components.chat.md.applyMdRangeToDom
import main.components.chat.md.buildPositionMap
import main.components.chat.md.domPointToMd
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import web.animations.awaitAnimationFrame

@Composable
fun MdEditable(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    selection: IntRange?,
    onSelectionChange: (Int, Int) -> Unit,
    controller: RichTextEditorController?,
    selectionTargetLength: Int? = null,
    enabled: Boolean = true,
) {
    var root by remember { mutableStateOf<HTMLDivElement?>(null) }
    var isComposing by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<IntRange?>(null) }
    var internalSelectionTargetLength by remember { mutableStateOf<Int?>(null) }
    var suppressDomRenderOnce by remember { mutableStateOf(false) }

    // Render markdown to HTML using Marked
    LaunchedEffect(markdown, root) {
        val el = root ?: return@LaunchedEffect
        if (!suppressDomRenderOnce) {
            // Disable soft breaks during editing to avoid extraneous <br> in list items
            el.innerHTML = markdownToHtml(markdown, softBreaks = false)
        }
        suppressDomRenderOnce = false

        // Try to apply any pending selection once the DOM matches expected length
        val desired = pendingSelection
        if (desired != null) {
            val targetLen = internalSelectionTargetLength
            if (targetLen == null || targetLen == markdown.length) {
                awaitAnimationFrame()
                applyMarkdownRangeToDom(el, desired.first, desired.last)
                pendingSelection = null
                internalSelectionTargetLength = null
            }
        }
    }

    // Sync incoming external selection request
    LaunchedEffect(root, selection, markdown, selectionTargetLength) {
        val sel = selection
        val el = root
        if (el != null && sel != null) {
            val targetLen = selectionTargetLength
            if (targetLen == null || targetLen == markdown.length) {
                pendingSelection = sel.first.coerceAtLeast(0)..sel.last.coerceAtLeast(0)
                internalSelectionTargetLength = markdown.length
            }
        }
    }

    Div(
        attrs = {
            classes(Styles.md, Styles.inputField)
            attr("contenteditable", if (enabled) "true" else "false")
            ref { el ->
                root = el

                // Composition events
                val compStart: (Event) -> Unit = { isComposing = true }
                val compEnd: (Event) -> Unit = {
                    isComposing = false
                    // After IME completes, resync selection
                    val r = domSelectionToMarkdownRange(el)
                    try { onSelectionChange(r.first, r.last) } catch (_: Throwable) {}
                }
                el.addEventListener("compositionstart", compStart)
                el.addEventListener("compositionend", compEnd)

                // Input listener (fires on typing, paste, delete, undo/redo)
                val inputListener: (Event) -> Unit = {
                    if (!isComposing) {
                        val newMd = serializeDomToMarkdown(el)
                        val range = domSelectionToMarkdownRange(el)
                        suppressDomRenderOnce = true
                        onMarkdownChange(newMd)
                        try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                        pendingSelection = range
                        internalSelectionTargetLength = newMd.length
                    }
                }
                el.addEventListener("input", inputListener)

                // Keyboard shortcuts: Ctrl/Cmd + B/I/U for bold/italic/underline
                val keydownListener: (Event) -> Unit = keydown@ { evt ->
                    if (!enabled) return@keydown
                    if (isComposing) return@keydown
                    if (evt is KeyboardEvent) {
                        val isMod = evt.ctrlKey || evt.metaKey
                        if (isMod) {
                            val k = evt.key.lowercase()
                            val wrapperLeft: String?
                            val wrapperRight: String?
                            when (k) {
                                "b" -> { wrapperLeft = "**"; wrapperRight = "**" }
                                "i" -> { wrapperLeft = "*"; wrapperRight = "*" }
                                "u" -> { wrapperLeft = "<u>"; wrapperRight = "</u>" }
                                else -> { wrapperLeft = null; wrapperRight = null }
                            }
                            if (wrapperLeft != null) {
                                evt.preventDefault()
                                // Map current DOM selection to markdown indices using current DOM-derived markdown
                                val sel = domSelectionToMarkdownRange(el)
                                val sourceMd = serializeDomToMarkdown(el)
                                val start = sel.first.coerceIn(0, sourceMd.length)
                                val end = sel.last.coerceIn(0, sourceMd.length)
                                val before = sourceMd.substring(0, start)
                                val selected = sourceMd.substring(start, end)
                                val after = sourceMd.substring(end)
                                val newMd = buildString(before.length + selected.length + after.length + wrapperLeft.length + (wrapperRight?.length ?: wrapperLeft.length)) {
                                    append(before)
                                    append(wrapperLeft)
                                    append(selected)
                                    append(wrapperRight ?: wrapperLeft)
                                    append(after)
                                }
                                val newStart = if (start != end) start else start + wrapperLeft.length
                                val newEnd = if (start != end) end + (wrapperLeft.length + (wrapperRight?.length ?: wrapperLeft.length)) else newStart
                                onMarkdownChange(newMd)
                                try { onSelectionChange(newStart, newEnd) } catch (_: Throwable) {}
                                pendingSelection = newStart..newEnd
                                internalSelectionTargetLength = newMd.length
                            }
                        }
                    }
                }
                el.addEventListener("keydown", keydownListener)

                // Selection change helpers
                val keyupListener: (Event) -> Unit = {
                    val range = domSelectionToMarkdownRange(el)
                    try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                }
                val mouseupListener: (Event) -> Unit = {
                    val range = domSelectionToMarkdownRange(el)
                    try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                }
                val clickListener: (Event) -> Unit = {
                    val range = domSelectionToMarkdownRange(el)
                    try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                }
                el.addEventListener("keyup", keyupListener)
                el.addEventListener("mouseup", mouseupListener)
                el.addEventListener("click", clickListener)

                // Wire controller so collaboration applies to CE
                controller?.apply {
                    applyOpsImpl = { ops, newSelStart, newSelEnd ->
                        if (isComposing) {
                            false
                        } else {
                            val base = markdown
                            val newVal = applyOpsTo(base, ops)
                            val s = newSelStart.coerceIn(0, newVal.length)
                            val e = newSelEnd.coerceIn(0, newVal.length)
                            onMarkdownChange(newVal)
                            try { onSelectionChange(s, e) } catch (_: Throwable) {}
                            pendingSelection = s..e
                            internalSelectionTargetLength = newVal.length
                            if (document.activeElement === el) {
                                el.focus()
                            }
                            true
                        }
                    }
                    applySnapshotImpl = { newText, newSelStart, newSelEnd ->
                        if (isComposing) {
                            false
                        } else {
                            val s = newSelStart.coerceIn(0, newText.length)
                            val e = newSelEnd.coerceIn(0, newText.length)
                            onMarkdownChange(newText)
                            try { onSelectionChange(s, e) } catch (_: Throwable) {}
                            pendingSelection = s..e
                            internalSelectionTargetLength = newText.length
                            if (document.activeElement === el) {
                                el.focus()
                            }
                            true
                        }
                    }
                    inlineWrapImpl = { left, right ->
                        if (isComposing) {
                            false
                        } else {
                            val sel = domSelectionToMarkdownRange(el)
                            val sourceMd = serializeDomToMarkdown(el)
                            val start = sel.first.coerceIn(0, sourceMd.length)
                            val end = sel.last.coerceIn(0, sourceMd.length)
                            val before = sourceMd.substring(0, start)
                            val selected = sourceMd.substring(start, end)
                            val after = sourceMd.substring(end)
                            val newMd = buildString(before.length + selected.length + after.length + left.length + right.length) {
                                append(before)
                                append(left)
                                append(selected)
                                append(right)
                                append(after)
                            }
                            onMarkdownChange(newMd)
                            val newStart = if (start != end) start else start + left.length
                            val newEnd = if (start != end) end + left.length + right.length else newStart
                            try { onSelectionChange(newStart, newEnd) } catch (_: Throwable) {}
                            pendingSelection = newStart..newEnd
                            internalSelectionTargetLength = newMd.length
                            if (document.activeElement === el) {
                                el.focus()
                            }
                            true
                        }
                    }
                    setSelectionImpl = { s0, e0 ->
                        val len = markdown.length
                        val s = s0.coerceIn(0, len)
                        val e = e0.coerceIn(0, len)
                        pendingSelection = s..e
                        internalSelectionTargetLength = len
                        try { onSelectionChange(s, e) } catch (_: Throwable) {}
                        if (document.activeElement === el) {
                            el.focus()
                        }
                        true
                    }
                }

                onDispose {
                    controller?.apply {
                        applyOpsImpl = null
                        applySnapshotImpl = null
                        setSelectionImpl = null
                    }
                    el.removeEventListener("compositionstart", compStart)
                    el.removeEventListener("compositionend", compEnd)
                    el.removeEventListener("input", inputListener)
                    el.removeEventListener("keydown", keydownListener)
                    el.removeEventListener("keyup", keyupListener)
                    el.removeEventListener("mouseup", mouseupListener)
                    el.removeEventListener("click", clickListener)
                    root = null
                }
            }
        },
    ) {}
}

// --- Utilities ---

private fun childNodesList(n: Node): List<Node> {
    val list = mutableListOf<Node>()
    val cn = n.childNodes
    for (i in 0 until cn.length) list.add(cn.item(i)!!)
    return list
}

private fun isElement(node: Node, tag: String): Boolean {
    return (node as? Element)?.tagName?.equals(tag, ignoreCase = true) == true
}

private fun getAttribute(el: Element, name: String): String? = el.getAttribute(name)

internal fun serializeDomToMarkdown(root: Element): String = buildPositionMap(root).markdown

private fun markdownWrappersFor(@Suppress("UNUSED_PARAMETER") el: Element): Pair<Int, Int> = 0 to 0

private fun markdownBlockSpacingFor(el: Element): Int {
    // Trailing spacing appended by serializer after blocks
    return when (el.tagName.lowercase()) {
        "p", "blockquote", "h1","h2","h3","h4","h5","h6" -> 2 // \n\n
        // For lists, serializer adds a newline after each <li> and then one extra newline after list.
        // We will account for those inside list-specific branches; do not add block spacing here.
        "ul", "ol" -> 0
        else -> 0
    }
}

internal fun domSelectionToMarkdownRange(root: Element): IntRange {
    val sel = getWindowSelection() ?: return 0..0
    if (sel.rangeCount == 0) return 0..0
    val r = sel.getRangeAt(0)
    val map = buildPositionMap(root)
    val s = domPointToMd(map, r.startContainer, r.startOffset)
    val e = domPointToMd(map, r.endContainer, r.endOffset)
    return if (s <= e) s..e else e..s
}

internal fun applyMarkdownRangeToDom(root: Element, mdStart: Int, mdEnd: Int) {
    val map = buildPositionMap(root)
    applyMdRangeToDom(map, root, mdStart, mdEnd)
}

// Utilities to apply ops (duplicated minimal version to avoid dependency cycle)
private fun applyOpsTo(base: String, ops: List<models.DocOperation>): String {
    var text = base
    ops.forEach { op ->
        when (op) {
            is models.DocOperation.Insert -> {
                val i = op.position.coerceIn(0, text.length)
                text = text.substring(0, i) + op.text + text.substring(i)
            }
            is models.DocOperation.Delete -> {
                val i = op.position.coerceIn(0, text.length)
                val e = (i + op.length).coerceIn(i, text.length)
                text = text.removeRange(i, e)
            }
            is models.DocOperation.Format -> {
                // Formatting ops are no-ops for raw markdown string in this serializer path for now
            }
        }
    }
    return text
}

// Selection access helper using external typing
private external interface WindowWithSelection {
    fun getSelection(): DomSelection?
}
private external interface DomSelection {
    val rangeCount: Int
    fun getRangeAt(index: Int): Range
    fun removeAllRanges()
    fun addRange(range: Range)
}
private fun getWindowSelection(): DomSelection? = try {
    window.unsafeCast<WindowWithSelection>().getSelection()
} catch (_: Throwable) { null }
