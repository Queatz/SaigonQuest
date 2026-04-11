package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.applyImageLoadBehavior
import kotlinx.browser.document
import kotlinx.browser.window
import main.components.markdownToHtml
import main.components.chat.md.applyMdRangeToDom
import main.components.chat.md.buildPositionMap
import main.components.chat.md.domPointToMd
import main.theme.Styles
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.px
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
    // When false, this surface mirrors content but must NOT modify the value (no normalization, no input edits)
    mutatesValue: Boolean = true,
) {
    var root by remember { mutableStateOf<HTMLDivElement?>(null) }
    var isComposing by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<IntRange?>(null) }
    var internalSelectionTargetLength by remember { mutableStateOf<Int?>(null) }
    var suppressDomRenderOnce by remember { mutableStateOf(false) }
    // Cache the latest markdown-mapped selection so toolbar actions don't read a transient DOM selection
    var lastMdSelection by remember { mutableStateOf(0..0) }
    // Normalize the incoming markdown once per value to avoid large first-edit rewrites
    var normalizedOnceForThisValue by remember(markdown) { mutableStateOf(false) }

    // Render markdown to HTML using Marked
    LaunchedEffect(markdown, root) {
        val el = root ?: return@LaunchedEffect
        if (!suppressDomRenderOnce) {
            // Disable soft breaks during editing to avoid extraneous <br> and simplify selection mapping
            el.innerHTML = markdownToHtml(markdown, softBreaks = false)
            // Ensure images adopt the same load/fade behavior as in Md
            applyImageLoadBehavior(el)
        }
        suppressDomRenderOnce = false

        // One-time normalization ONLY when this surface is allowed to mutate and is focused.
        // This avoids raw editor surprises and prevents first-action churn while passive.
        val isActive = (document.activeElement === el)
        if (mutatesValue && isActive && !normalizedOnceForThisValue) {
            val canonicalMd = serializeDomToMarkdown(el)
            normalizedOnceForThisValue = true
            if (canonicalMd != markdown) {
                suppressDomRenderOnce = true
                onMarkdownChange(canonicalMd)
                // Preserve current selection mapped to markdown
                val r = domSelectionToMarkdownRange(el)
                pendingSelection = r
                internalSelectionTargetLength = canonicalMd.length
                lastMdSelection = r
            }
        }

        // Try to apply any pending selection once the DOM matches expected length
        val desired = pendingSelection
        if (desired != null) {
            val targetLen = internalSelectionTargetLength
            // Only attempt to apply if:
            // - length matches (or not constrained)
            // - element is focused
            // - we're not in the middle of IME composition
            if ((targetLen == null || targetLen == markdown.length) &&
                (document.activeElement === el) &&
                !isComposing
            ) {
                // Skip if current DOM selection already equals desired range
                val current = domSelectionToMarkdownRange(el)
                if (current.first == desired.first && current.last == desired.last) {
                    pendingSelection = null
                    internalSelectionTargetLength = null
                } else {
                    awaitAnimationFrame()
                    applyMarkdownRangeToDom(el, desired.first, desired.last)
                    pendingSelection = null
                    internalSelectionTargetLength = null
                }
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
            classes(Styles.md)

            if (enabled) {
                if (markdown.isBlank()) {
                    classes(Styles.inputField)
                }

                style {
                    borderRadius(12.px)
                    property("outline", "none")
                }
            }

            attr("contenteditable", if (enabled) "true" else "false")
            ref { el ->
                root = el

                // Composition events
                val compStart: (Event) -> Unit = { isComposing = true }
                val compEnd: (Event) -> Unit = {
                    isComposing = false
                    // After IME completes, resync selection
                    val r = domSelectionToMarkdownRange(el)
                    lastMdSelection = r
                    try { onSelectionChange(r.first, r.last) } catch (_: Throwable) {}
                }
                el.addEventListener("compositionstart", compStart)
                el.addEventListener("compositionend", compEnd)

                // Input listener (fires on typing, paste, delete, undo/redo)
                val inputListener: (Event) -> Unit = {
                    if (!isComposing && mutatesValue) {
                        val newMd = serializeDomToMarkdown(el)
                        val range = domSelectionToMarkdownRange(el)
                        lastMdSelection = range
                        // We intentionally suppress DOM re-injection and avoid forcing selection
                        // application because the native caret is already correct.
                        suppressDomRenderOnce = true
                        onMarkdownChange(newMd)
                        try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                        // Do NOT set pendingSelection here to avoid fighting the native caret
                        // while the user is typing.
                    }
                }
                el.addEventListener("input", inputListener)

                // Keyboard shortcuts: Ctrl/Cmd + B/I/U for bold/italic/underline
                val keydownListener: (Event) -> Unit = keydown@ { evt ->
                    if (!enabled || !mutatesValue) return@keydown
                    if (isComposing) return@keydown
                    if (evt is KeyboardEvent) {
                        val isMod = evt.ctrlKey || evt.metaKey
                        if (isMod) {
                            val k = evt.key.lowercase()
                            val wrapperLeft: String?
                            val wrapperRight: String?
                            var domTag: String? = null
                            when (k) {
                                "b" -> { wrapperLeft = "**"; wrapperRight = "**"; domTag = "strong" }
                                "i" -> { wrapperLeft = "*"; wrapperRight = "*"; domTag = "em" }
                                "u" -> { wrapperLeft = "<u>"; wrapperRight = "</u>"; domTag = "u" }
                                else -> { wrapperLeft = null; wrapperRight = null }
                            }
                            if (wrapperLeft != null) {
                                evt.preventDefault()
                                // Try DOM-native wrapping to avoid re-injection and selection flicker
                                val sel = getWindowSelection()
                                val canDomWrap = sel != null && sel.rangeCount > 0 && domTag != null
                                if (canDomWrap) {
                                    try {
                                        val range = sel.getRangeAt(0)
                                        if (!range.collapsed) {
                                            val wrapper = el.ownerDocument?.createElement(domTag) ?: document.createElement(
                                                domTag
                                            )
                                            range.surroundContents(wrapper)
                                            // Select contents of the new wrapper (exclude wrapper element itself)
                                            val newRange = document.createRange()
                                            newRange.selectNodeContents(wrapper)
                                            sel.removeAllRanges()
                                            sel.addRange(newRange)
                                            // Serialize DOM → MD without re-injection to avoid flash
                                            val newMd = serializeDomToMarkdown(el)
                                            val r = domSelectionToMarkdownRange(el)
                                            lastMdSelection = r
                                            // We've mutated DOM directly; suppress re-injection and
                                            // avoid re-applying selection since it's already correct.
                                            suppressDomRenderOnce = true
                                            onMarkdownChange(newMd)
                                            try { onSelectionChange(r.first, r.last) } catch (_: Throwable) {}
                                            // No pendingSelection here — prevents cursor jumps
                                            return@keydown
                                        }
                                    } catch (_: Throwable) {
                                        // Fallback to markdown-range wrapping below
                                    }
                                }
                                // Fallback path: Compute live DOM→MD selection and combine with cached + visible length for robustness
                                val liveSel = domSelectionToMarkdownRange(el)
                                val sourceMd = serializeDomToMarkdown(el)
                                var start = liveSel.first.coerceIn(0, sourceMd.length)
                                val baseLen = (liveSel.last - liveSel.first).coerceAtLeast(0)
                                val cachedLen = (lastMdSelection.last - lastMdSelection.first).coerceAtLeast(0)
                                val visibleText = getCurrentSelectionText()
                                val singleLineVisible = visibleText != null && !visibleText.contains('\n') && visibleText.isNotEmpty()
                                val visibleLen = if (singleLineVisible) visibleText.length else 0
                                // Avoid wrapping list markers if start accidentally points at "- "
                                if (singleLineVisible && start + 2 <= sourceMd.length && sourceMd.startsWith("- ", start) && !visibleText.startsWith("-")) {
                                    start = (start + 2).coerceAtMost(sourceMd.length)
                                }
                                val chosenLen = if (visibleLen > 0) visibleLen else maxOf(baseLen, cachedLen)
                                var end = (start + chosenLen).coerceIn(start, sourceMd.length)
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
                                // After wrapping, keep selection on the content (exclude wrapper tokens)
                                val newStart = if (start != end) start + wrapperLeft.length else start + wrapperLeft.length
                                val rightLen = (wrapperRight?.length ?: wrapperLeft.length)
                                val newEnd = if (start != end) end + wrapperLeft.length else newStart
                                suppressDomRenderOnce = false // we didn't touch DOM; allow re-injection
                                onMarkdownChange(newMd)
                                try { onSelectionChange(newStart, newEnd) } catch (_: Throwable) {}
                                pendingSelection = newStart..newEnd
                                internalSelectionTargetLength = newMd.length
                                lastMdSelection = newStart..newEnd
                            }
                        }
                    }
                }
                el.addEventListener("keydown", keydownListener)

                // Selection change helpers
                val keyupListener: (Event) -> Unit = {
                    val range = domSelectionToMarkdownRange(el)
                    lastMdSelection = range
                    try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                }
                val mouseupListener: (Event) -> Unit = {
                    val range = domSelectionToMarkdownRange(el)
                    lastMdSelection = range
                    try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                }
                val clickListener: (Event) -> Unit = {
                    val range = domSelectionToMarkdownRange(el)
                    lastMdSelection = range
                    try { onSelectionChange(range.first, range.last) } catch (_: Throwable) {}
                }
                el.addEventListener("keyup", keyupListener)
                el.addEventListener("mouseup", mouseupListener)
                el.addEventListener("click", clickListener)

                // Wire controller so collaboration applies to CE
                controller?.apply {
                    applyOpsImpl = applyOps@ { ops, newSelStart, newSelEnd ->
                        if (isComposing) {
                            false
                        } else {
                            if (!mutatesValue) return@applyOps false
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
                    applySnapshotImpl = snapshot@ { newText, newSelStart, newSelEnd ->
                        if (isComposing) {
                            false
                        } else {
                            if (!mutatesValue) return@snapshot false
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
                    inlineWrapImpl = wrap@ { left, right ->
                        if (isComposing) {
                            false
                        } else {
                            if (!mutatesValue) return@wrap false
                            // Prefer DOM-native wrapping to avoid selection flash
                            val tag = when {
                                left == "**" && right == "**" -> "strong"
                                left == "*" && right == "*" -> "em"
                                left == "<u>" && right == "</u>" -> "u"
                                else -> null
                            }
                            val sel = getWindowSelection()
                            if (tag != null && sel != null && sel.rangeCount > 0) {
                                try {
                                    val range = sel.getRangeAt(0)
                                    if (!range.collapsed) {
                                        val wrapper = el.ownerDocument?.createElement(tag) ?: document.createElement(tag)
                                        range.surroundContents(wrapper)
                                        val newRange = document.createRange()
                                        newRange.selectNodeContents(wrapper)
                                        sel.removeAllRanges()
                                        sel.addRange(newRange)
                                        val newMd = serializeDomToMarkdown(el)
                                        val r = domSelectionToMarkdownRange(el)
                                        lastMdSelection = r
                                        // DOM was edited directly; keep caret as-is and suppress re-injection
                                        suppressDomRenderOnce = true
                                        onMarkdownChange(newMd)
                                        try { onSelectionChange(r.first, r.last) } catch (_: Throwable) {}
                                        // No pendingSelection here — prevents cursor jumps
                                        if (document.activeElement === el) {
                                            el.focus()
                                        }
                                        return@wrap true
                                    }
                                } catch (_: Throwable) {
                                    // fall back to string-based wrapping
                                }
                            }
                            // Compute live DOM→MD selection and combine with cached + visible length for robustness
                            val liveSel = domSelectionToMarkdownRange(el)
                            val sourceMd = serializeDomToMarkdown(el)
                            var start = liveSel.first.coerceIn(0, sourceMd.length)
                            val baseLen = (liveSel.last - liveSel.first).coerceAtLeast(0)
                            val cachedLen = (lastMdSelection.last - lastMdSelection.first).coerceAtLeast(0)
                            val visibleText = getCurrentSelectionText()
                            val singleLineVisible = visibleText != null && !visibleText.contains('\n') && visibleText.isNotEmpty()
                            val visibleLen = if (singleLineVisible) visibleText.length else 0
                            if (singleLineVisible && start + 2 <= sourceMd.length && sourceMd.startsWith("- ", start) && !visibleText.startsWith("-")) {
                                start = (start + 2).coerceAtMost(sourceMd.length)
                            }
                            val chosenLen = if (visibleLen > 0) visibleLen else maxOf(baseLen, cachedLen)
                            val end = (start + chosenLen).coerceIn(start, sourceMd.length)
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
                            // Keep selection on the wrapped content (exclude wrapper tokens)
                            val newStart = if (start != end) start + left.length else start + left.length
                            val newEnd = if (start != end) end + left.length else newStart
                            try { onSelectionChange(newStart, newEnd) } catch (_: Throwable) {}
                            pendingSelection = newStart..newEnd
                            internalSelectionTargetLength = newMd.length
                            lastMdSelection = newStart..newEnd
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

internal fun serializeDomToMarkdown(root: Element): String = buildPositionMap(root).markdown

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

// Access selected visible text without using dynamic/js
private external interface DomSelectionWithToString {
    override fun toString(): String
}

private fun getCurrentSelectionText(): String? = try {
    val sel = getWindowSelection() ?: return null
    sel.unsafeCast<DomSelectionWithToString>().toString()
} catch (_: Throwable) { null }
