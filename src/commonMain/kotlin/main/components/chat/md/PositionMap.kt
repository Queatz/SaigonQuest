package main.components.chat.md

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*

// Position map built while serializing DOM -> Markdown so both use identical rules
internal data class TextSeg(
    val node: Text,
    val mdStart: Int,
    val length: Int,
)

internal data class BoundaryAnchor(
    val element: Element,
    val childIndex: Int, // position between children (0..children.size)
)

internal data class VirtualSpan(
    val mdStart: Int,
    val mdEnd: Int,
    val anchor: BoundaryAnchor,
)

internal class PositionMap(
    val markdown: String,
    val textSegs: List<TextSeg>,
    val boundaries: Map<Element, IntArray>,
    val virtuals: List<VirtualSpan>,
)

private fun childNodesList(n: Node): List<Node> {
    val list = mutableListOf<Node>()
    val cn = n.childNodes
    for (i in 0 until cn.length) list.add(cn.item(i)!!)
    return list
}

private fun firstTextIn(node: Node): Text? {
    if (node.nodeType == Node.TEXT_NODE) return node as Text
    val children = childNodesList(node)
    for (c in children) {
        val t = firstTextIn(c)
        if (t != null) return t
    }
    return null
}

private fun lastTextIn(node: Node): Text? {
    if (node.nodeType == Node.TEXT_NODE) return node as Text
    val children = childNodesList(node)
    for (i in children.size - 1 downTo 0) {
        val t = lastTextIn(children[i])
        if (t != null) return t
    }
    return null
}

private fun endsWithBr(node: Node): Boolean {
    return when (node.nodeType) {
        Node.ELEMENT_NODE -> {
            val el = node as Element
            val tag = el.tagName.lowercase()
            if (tag == "br") return true
            val children = childNodesList(el)
            for (i in children.size - 1 downTo 0) {
                if (endsWithBr(children[i])) return true
                // If this child had any content that is not a trailing <br>, stop
                val c = children[i]
                if (c.nodeType == Node.TEXT_NODE) {
                    val txt = (c as Text).data
                    if (txt.isNotEmpty()) return false
                } else {
                    // non-empty element handled by recursion above; if returned false, stop
                    return false
                }
            }
            false
        }
        else -> false
    }
}

private fun nextTextAfterBoundary(element: Element, childIndex: Int): Text? {
    val siblings = childNodesList(element)
    if (childIndex < siblings.size) {
        val t = firstTextIn(siblings[childIndex])
        if (t != null) return t
    }
    // search forward after the element in DOM order
    var p: Node? = element
    var idxInParent = childIndex
    while (p != null) {
        val parent = p.parentNode ?: return null
        val sibs = childNodesList(parent)
        val start = if (p === element) childIndex else sibs.indexOf(p) + 1
        for (i in start until sibs.size) {
            val t = firstTextIn(sibs[i])
            if (t != null) return t
        }
        p = parent
        idxInParent = childNodesList(parent).indexOf(p)
    }
    return null
}

private fun ensureEmptyTextAtBoundary(element: Element, childIndex: Int): Text {
    val siblings = childNodesList(element)
    return if (childIndex < siblings.size) {
        firstTextIn(siblings[childIndex]) ?: document.createTextNode("").also {
            element.insertBefore(it, siblings[childIndex])
        }
    } else {
        lastTextIn(element) ?: document.createTextNode("").also { element.appendChild(it) }
    }
}

internal fun buildPositionMap(root: Element): PositionMap {
    val sb = StringBuilder()
    val textSegs = mutableListOf<TextSeg>()
    val boundaries = HashMap<Element, IntArray>()
    val virtuals = mutableListOf<VirtualSpan>()

    fun addText(node: Text, text: String) {
        val mdStart = sb.length
        sb.append(text)
        textSegs.add(TextSeg(node, mdStart, text.length))
    }

    fun addVirtual(len: Int, anchor: BoundaryAnchor) {
        val start = sb.length
        sb.append(" ".repeat(len)) // advance md by len; actual chars irrelevant for mapping
        virtuals.add(VirtualSpan(start, start + len, anchor))
    }

    fun addLiteral(lit: String, anchor: BoundaryAnchor? = null) {
        val start = sb.length
        sb.append(lit)
        if (anchor != null) {
            virtuals.add(VirtualSpan(start, start + lit.length, anchor))
        }
    }

    // We'll declare a function variable so helpers can reference it regardless of order
    lateinit var emit: (Node) -> Unit

    fun writeInlineWrapper(el: Element, prefix: String, suffix: String) {
        // prefix anchored at start boundary
        val children = childNodesList(el)
        val anchorStart = BoundaryAnchor(el, 0)
        addLiteral(prefix, anchorStart)
        // boundaries for this element
        val arr = IntArray(children.size + 1)
        arr[0] = sb.length
        children.forEachIndexed { i, child ->
            emit(child)
            arr[i + 1] = sb.length
        }
        // suffix anchored at end boundary
        val anchorEnd = BoundaryAnchor(el, children.size)
        addLiteral(suffix, anchorEnd)
        boundaries[el] = arr
    }

    fun writeBlockWithTrailing(el: Element, prefix: String, trailing: String) {
        val children = childNodesList(el)
        val arr = IntArray(children.size + 1)
        // prefix at start
        val anchorStart = BoundaryAnchor(el, 0)
        if (prefix.isNotEmpty()) addLiteral(prefix, anchorStart)
        arr[0] = sb.length
        children.forEachIndexed { i, child ->
            emit(child)
            arr[i + 1] = sb.length
        }
        // trailing spacing anchored at end boundary
        if (trailing.isNotEmpty()) {
            val anchorEnd = BoundaryAnchor(el, children.size)
            addLiteral(trailing, anchorEnd)
        }
        boundaries[el] = arr
    }

    fun write(node: Node) {
        when (node.nodeType) {
            Node.TEXT_NODE -> addText(node as Text, node.data)
            Node.ELEMENT_NODE -> {
                val el = node as Element
                val tag = el.tagName.lowercase()
                when (tag) {
                    "br" -> {
                        // Treat <br> as a soft break
                        val parent = el.parentElement ?: return
                        val idx = childNodesList(parent).indexOf(el).coerceAtLeast(0)
                        addLiteral("  \n", BoundaryAnchor(parent, idx + 1))
                        // parent boundaries will be set by parent walk
                    }
                    "img" -> {
                        val alt = el.getAttribute("alt") ?: ""
                        val src = el.getAttribute("src") ?: ""
                        val parent = el.parentElement ?: return
                        val idx = childNodesList(parent).indexOf(el).coerceAtLeast(0)
                        val start = sb.length
                        val lit = StringBuilder().apply {
                            append("![")
                            append(alt)
                            append("](")
                            append(src)
                            append(")")
                        }.toString()
                        sb.append(lit)
                        virtuals.add(VirtualSpan(start, start + lit.length, BoundaryAnchor(parent, idx + 1)))
                    }
                    "audio" -> {
                        val src = el.getAttribute("src") ?: ""
                        val parent = el.parentElement ?: return
                        val idx = childNodesList(parent).indexOf(el).coerceAtLeast(0)
                        val start = sb.length
                        val lit = "![audio]($src)"
                        sb.append(lit)
                        virtuals.add(VirtualSpan(start, start + lit.length, BoundaryAnchor(parent, idx + 1)))
                    }
                    "strong", "b" -> writeInlineWrapper(el, "**", "**")
                    "em", "i" -> writeInlineWrapper(el, "*", "*")
                    "u" -> writeInlineWrapper(el, "<u>", "</u>")
                    "del", "s" -> writeInlineWrapper(el, "~~", "~~")
                    "code" -> {
                        val parentTag = el.parentElement?.tagName?.lowercase()
                        if (parentTag == "pre") {
                            // handled by <pre>
                            val children = childNodesList(el)
                            val arr = IntArray(children.size + 1)
                            arr[0] = sb.length
                            children.forEachIndexed { i, child ->
                                emit(child)
                                arr[i + 1] = sb.length
                            }
                            boundaries[el] = arr
                        } else {
                            writeInlineWrapper(el, "`", "`")
                        }
                    }
                    "a" -> {
                        // Link: [text](href)
                        val href = el.getAttribute("href") ?: ""
                        val children = childNodesList(el)
                        // prefix "[" anchored at start boundary
                        addLiteral("[", BoundaryAnchor(el, 0))
                        val arr = IntArray(children.size + 1)
                        arr[0] = sb.length
                        children.forEachIndexed { i, child ->
                            emit(child)
                            arr[i + 1] = sb.length
                        }
                        // suffix "](href)" anchored at end boundary
                        addLiteral("]($href)", BoundaryAnchor(el, children.size))
                        boundaries[el] = arr
                    }
                    "h1","h2","h3","h4","h5","h6" -> {
                        val level = tag.removePrefix("h").toIntOrNull() ?: 1
                        writeBlockWithTrailing(el, "#".repeat(level) + " ", "\n\n")
                    }
                    "p" -> {
                        // Inside a list item, paragraphs should not emit trailing blank lines
                        val parentTag = el.parentElement?.tagName?.lowercase()
                        if (parentTag == "li") {
                            val children = childNodesList(el)
                            val arr = IntArray(children.size + 1)
                            arr[0] = sb.length
                            children.forEachIndexed { i, child ->
                                emit(child)
                                arr[i + 1] = sb.length
                            }
                            boundaries[el] = arr
                        } else {
                            writeBlockWithTrailing(el, "", "\n\n")
                        }
                    }
                    "blockquote" -> writeBlockWithTrailing(el, "> ", "\n\n")
                    "ul" -> {
                        val children = childNodesList(el)
                        val arr = IntArray(children.size + 1)
                        arr[0] = sb.length
                        children.forEachIndexed { i, li ->
                            if ((li as? Element)?.tagName?.equals("LI", true) == true) {
                                // marker for li anchored at its start boundary
                                addLiteral("- ", BoundaryAnchor(li, 0))
                                // boundaries for LI
                                val liChildren = childNodesList(li)
                                val liArr = IntArray(liChildren.size + 1)
                                liArr[0] = sb.length
                                liChildren.forEachIndexed { j, c ->
                                    emit(c)
                                    liArr[j + 1] = sb.length
                                }
                                boundaries[li] = liArr
                                // if LI does not already end with a <br> (soft break), add newline
                                if (!endsWithBr(li)) {
                                    addLiteral("\n", BoundaryAnchor(li, liChildren.size))
                                }
                            } else {
                                emit(li)
                            }
                            arr[i + 1] = sb.length
                        }
                        // extra newline after list anchored at UL end
                        addLiteral("\n", BoundaryAnchor(el, children.size))
                        boundaries[el] = arr
                    }
                    "ol" -> {
                        val children = childNodesList(el)
                        val arr = IntArray(children.size + 1)
                        arr[0] = sb.length
                        var iNum = el.getAttribute("start")?.toIntOrNull() ?: 1
                        children.forEachIndexed { i, li ->
                            if ((li as? Element)?.tagName?.equals("LI", true) == true) {
                                val marker = "$iNum. "
                                addLiteral(marker, BoundaryAnchor(li, 0))
                                val liChildren = childNodesList(li)
                                val liArr = IntArray(liChildren.size + 1)
                                liArr[0] = sb.length
                                liChildren.forEachIndexed { j, c ->
                                    emit(c)
                                    liArr[j + 1] = sb.length
                                }
                                boundaries[li] = liArr
                                // if LI does not already end with a <br> (soft break), add newline
                                if (!endsWithBr(li)) {
                                    addLiteral("\n", BoundaryAnchor(li, liChildren.size))
                                }
                                iNum += 1
                            } else {
                                emit(li)
                            }
                            arr[i + 1] = sb.length
                        }
                        addLiteral("\n", BoundaryAnchor(el, children.size))
                        boundaries[el] = arr
                    }
                    "pre" -> {
                        val code = el.getElementsByTagName("code").item(0)
                        val lang = code?.getAttribute("class")?.substringAfter("language-", "") ?: ""
                        val open = "```" + lang + "\n"
                        addLiteral(open, BoundaryAnchor(el, 0))
                        val children = if (code != null) childNodesList(code) else childNodesList(el)
                        val arr = IntArray(children.size + 1)
                        arr[0] = sb.length
                        children.forEachIndexed { i, c ->
                            emit(c)
                            arr[i + 1] = sb.length
                        }
                        val close = "\n```\n\n"
                        addLiteral(close, BoundaryAnchor(el, children.size))
                        boundaries[el] = arr
                    }
                    else -> {
                        // Generic: just children, no wrappers
                        val children = childNodesList(el)
                        val arr = IntArray(children.size + 1)
                        arr[0] = sb.length
                        children.forEachIndexed { i, c ->
                            emit(c)
                            arr[i + 1] = sb.length
                        }
                        boundaries[el] = arr
                    }
                }
            }
        }
    }

    // assign emit to the writer
    emit = { n -> write(n) }

    // Walk children of container root
    childNodesList(root).forEach { write(it) }

    return PositionMap(
        markdown = sb.toString(),
        textSegs = textSegs,
        boundaries = boundaries,
        virtuals = virtuals,
    )
}

internal fun domPointToMd(map: PositionMap, container: Node, offset: Int): Int {
    return when (container.nodeType) {
        Node.TEXT_NODE -> {
            val t = container as Text
            val seg = map.textSegs.find { it.node == t }
            if (seg != null) (seg.mdStart + offset.coerceIn(0, seg.length)) else map.markdown.length
        }
        Node.ELEMENT_NODE -> {
            val el = container as Element
            val arr = map.boundaries[el]
            if (arr != null && offset in 0..arr.lastIndex) arr[offset] else map.markdown.length
        }
        else -> map.markdown.length
    }
}

private fun ensureEmptyTextInParentAt(parent: Element, boundaryChildIndex: Int): Text {
    val siblings = childNodesList(parent)
    return if (boundaryChildIndex < siblings.size) {
        val ref = siblings[boundaryChildIndex]
        // If next sibling already starts with a text node, use it; otherwise insert empty text
        firstTextIn(ref) ?: document.createTextNode("").also { parent.insertBefore(it, ref) }
    } else {
        // After the last child
        lastTextIn(parent) ?: document.createTextNode("").also { parent.appendChild(it) }
    }
}

internal fun mdToDomPoint(map: PositionMap, root: Element, mdIndex: Int, preferEnd: Boolean = false): Pair<Node, Int> {
    val idx = mdIndex.coerceIn(0, map.markdown.length)
    // Try text segments
    for (seg in map.textSegs) {
        if (idx in seg.mdStart..(seg.mdStart + seg.length)) {
            val off = (idx - seg.mdStart).coerceIn(0, seg.length)
            return seg.node to off
        }
    }
    // Try virtuals: find closest spanning or nearest by start
    val v = map.virtuals.find { idx >= it.mdStart && idx <= it.mdEnd }
        ?: map.virtuals.minByOrNull { kotlin.math.abs(idx - it.mdStart) }
    if (v != null) {
        val el = v.anchor.element
        val childIdx = v.anchor.childIndex
        val childCount = childNodesList(el).size
        // If we prefer placing caret after the element (for end indices), and we're on end boundary,
        // create/use a text node positioned just after this element in its parent so the range can
        // include the wrapper's suffix logically.
        if (preferEnd && childIdx >= childCount) {
            val parent = el.parentElement
            if (parent != null) {
                val idxInParent = childNodesList(parent).indexOf(el)
                val after = ensureEmptyTextInParentAt(parent, idxInParent + 1)
                return after to 0
            }
        }
        val t: Text = when {
            // At start boundary: prefer start of element (inside)
            childIdx <= 0 -> ensureEmptyTextAtBoundary(el, 0)
            // At end boundary: prefer end of element (stay within element)
            childIdx >= childCount -> ensureEmptyTextAtBoundary(el, childCount)
            // Middle boundary: prefer beginning of the next child content
            else -> nextTextAfterBoundary(el, childIdx) ?: ensureEmptyTextAtBoundary(el, childIdx)
        }
        val offset = when {
            childIdx <= 0 -> 0
            childIdx >= childCount -> t.data.length
            else -> 0
        }
        return t to offset
    }
    // Fallback: place at end of root
    val t = lastTextIn(root) ?: document.createTextNode("").also { root.appendChild(it) }
    return t to t.data.length
}

internal fun applyMdRangeToDom(map: PositionMap, root: Element, mdStart: Int, mdEnd: Int) {
    val r = document.createRange()
    val (sNode, sOff) = mdToDomPoint(map, root, mdStart, preferEnd = false)
    // Use end-biased mapping for the end index so selections include wrapper suffixes precisely
    val (eNode, eOff) = mdToDomPoint(map, root, mdEnd, preferEnd = true)
    try {
        r.setStart(sNode, sOff)
        r.setEnd(eNode, eOff)
        val sel = getWindowSelection()
        sel?.removeAllRanges()
        sel?.addRange(r)
    } catch (_: Throwable) {}
}

// Selection access helper using external typing (to avoid platform mismatches)
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
