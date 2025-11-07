package main.components.worlddocs

import Translations
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.theme.WorldDocsStyles
import models.Doc
import models.DocTree
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

@Composable
fun DocTreeView(
    docs: List<DocTree>,
    selectedDocId: String?,
    onSelect: (String) -> Unit,
    canDrag: Boolean = false,
    onMoveRequest: (docId: String, newParentId: String?, newOrderIndex: Double) -> Unit = { _, _, _ -> },
    canEditDoc: (Doc) -> Boolean = { true },
    expandPathToId: String? = null,
    showAddButtonForSelected: Boolean = false,
    onCreateChild: (parentId: String) -> Unit = {},
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // If requested, expand the path to the given id (e.g., from server-search selection)
    LaunchedEffect(expandPathToId, docs) {
        val id = expandPathToId
        if (!id.isNullOrBlank()) {
            ancestorsPath(docs, id).forEach { ancId -> expanded[ancId] = true }
        }
    }

    // DnD state
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOverId by remember { mutableStateOf<String?>(null) }
    var dragOverPos by remember { mutableStateOf<DragPos?>(null) }
    var dragOverIntoId by remember { mutableStateOf<String?>(null) }

    // Auto-expand on drag over after a short delay
    val scope = rememberCoroutineScope()
    var expandJob by remember { mutableStateOf<Job?>(null) }
    var lastHoverId by remember { mutableStateOf<String?>(null) }

    fun indentClass(level: Int) = when (level) {
        0 -> WorldDocsStyles.indent0
        1 -> WorldDocsStyles.indent1
        2 -> WorldDocsStyles.indent2
        3 -> WorldDocsStyles.indent3
        4 -> WorldDocsStyles.indent4
        5 -> WorldDocsStyles.indent5
        else -> WorldDocsStyles.indent6
    }

    val visible = buildVisibleList(docs, expanded)

    Ul(attrs = {
        classes(WorldDocsStyles.tree)
        attr("tabindex", "0")
        onKeyDown { e ->
            val key = e.key
            when (key) {
                "ArrowUp" -> {
                    val currentIndex = visible.indexOfFirst { it.node.doc.key == selectedDocId }
                    val newIndex = if (currentIndex <= 0) 0 else currentIndex - 1
                    val target = visible.getOrNull(newIndex)?.node?.doc?.key
                    if (target != null) {
                        e.preventDefault()
                        onSelect(target)
                    }
                }

                "ArrowDown" -> {
                    val currentIndex = visible.indexOfFirst { it.node.doc.key == selectedDocId }
                    val newIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(visible.lastIndex)
                    val target = visible.getOrNull(newIndex)?.node?.doc?.key
                    if (target != null) {
                        e.preventDefault()
                        onSelect(target)
                    }
                }

                "ArrowLeft" -> {
                    // Collapse if expanded, else move to parent
                    val focus = visible.firstOrNull { it.node.doc.key == selectedDocId }
                    if (focus != null) {
                        val id = focus.node.doc.key
                        if (expanded[id] == true) {
                            e.preventDefault()
                            expanded[id] = false
                        } else {
                            val parentId = parentIdOf(docs, id)
                            if (parentId != null) {
                                e.preventDefault()
                                onSelect(parentId)
                            }
                        }
                    }
                }

                "ArrowRight" -> {
                    val focus = visible.firstOrNull { it.node.doc.key == selectedDocId }
                    if (focus != null) {
                        val id = focus.node.doc.key
                        val hasChildren = focus.node.children.isNotEmpty()
                        if (hasChildren && expanded[id] != true) {
                            e.preventDefault()
                            expanded[id] = true
                        } else if (hasChildren) {
                            val childId = focus.node.children.first().doc.key
                            e.preventDefault()
                            onSelect(childId)
                        }
                    }
                }
            }
        }
    }) {
        visible.forEach { item ->
            val node = item.node
            val level = item.level
            val hasChildren = node.children.isNotEmpty()
            val isExpanded = expanded[node.doc.key] ?: (level == 0)
            val isSelected = node.doc.key == selectedDocId

            key(node.doc.key) {
                Li(attrs = {
                    classes(
                        if (isSelected) WorldDocsStyles.treeItemSelected else WorldDocsStyles.treeItem,
                        indentClass(level)
                    )
                    onClick { onSelect(node.doc.key) }
                    if (canDrag) attr("draggable", "true")
                    if (canDrag) {
                        onDragStart {
                            draggingId = node.doc.key
                            it.dataTransfer?.effectAllowed = "move"
                            it.dataTransfer?.setData("text/plain", node.doc.key)
                        }
                        onDragEnd {
                            draggingId = null
                            dragOverId = null
                            dragOverPos = null
                            dragOverIntoId = null
                        }
                        // Drop INTO (as last child) when dropping on the row itself
                        onDragOver {
                            it.preventDefault()
                            // Mark this row as INTO hover when valid, but only if no edge (BEFORE/AFTER) hover is active
                            if (draggingId != null && draggingId != node.doc.key && dragOverId == null && dragOverPos == null) {
                                val invalidInto = isDescendant(docs, draggingId!!, node.doc.key)
                                if (!invalidInto) {
                                    dragOverIntoId = node.doc.key
                                }
                            }
                            // Schedule auto-expand if hovering over a collapsed parent
                            if (hasChildren && (expanded[node.doc.key] != true)) {
                                if (lastHoverId != node.doc.key) {
                                    expandJob?.cancel()
                                    lastHoverId = node.doc.key
                                    expandJob = scope.launch {
                                        delay(500)
                                        expanded[node.doc.key] = true
                                    }
                                }
                            }
                        }
                        onDragLeave {
                            if (dragOverIntoId == node.doc.key) {
                                dragOverIntoId = null
                            }
                        }
                        onDrop {
                            it.preventDefault()
                            if (draggingId != null && draggingId != node.doc.key) {
                                // Prevent moving a node into its own descendant
                                if (!isDescendant(docs, draggingId!!, node.doc.key)) {
                                    val lastChild = node.children.maxByOrNull { c -> c.doc.orderIndex }?.doc
                                    val newIndex = if (lastChild != null) lastChild.orderIndex + 1.0 else 0.0
                                    onMoveRequest(draggingId!!, node.doc.key, newIndex)
                                }
                            }
                            draggingId = null
                            dragOverId = null
                            dragOverPos = null
                            dragOverIntoId = null
                        }
                    }
                }) {
                    // INTO drop overlay (visual only). Edge (BEFORE/AFTER) hover takes precedence, so only show when no edge hover is active.
                    if (canDrag && draggingId != null && dragOverIntoId == node.doc.key && dragOverId == null && dragOverPos == null) {
                        Div(attrs = { classes(WorldDocsStyles.dropIntoOverlay) }) {}
                    }
                    // Top drop zone (before this node)
                    if (canDrag && draggingId != null) {
                        Div(attrs = {
                            classes(
                                if (dragOverId == node.doc.key && dragOverPos == DragPos.BEFORE) WorldDocsStyles.dropZoneActive else WorldDocsStyles.dropZone,
                                WorldDocsStyles.dropZoneTop
                            )
                            onDragOver {
                                it.preventDefault()
                                dragOverId = node.doc.key
                                dragOverPos = DragPos.BEFORE
                                // Edge band hovered; hide INTO overlay
                                dragOverIntoId = null
                            }
                            onDragLeave {
                                dragOverId = null
                                dragOverPos = null
                            }
                            onDrop {
                                it.preventDefault()
                                if (draggingId != null && draggingId != node.doc.key) {
                                    val targetParentId = node.doc.parentId
                                    // Prevent creating a cycle by moving a node to be sibling under its own subtree
                                    val invalid = when {
                                        targetParentId == null -> false
                                        targetParentId == draggingId -> true
                                        else -> isDescendant(docs, draggingId!!, targetParentId)
                                    }
                                    if (!invalid) {
                                        val siblings = siblingsOf(docs, node.doc.parentId)
                                        val idx = siblings.indexOfFirst { s -> s.key == node.doc.key }
                                        val before = if (idx - 1 >= 0) siblings[idx - 1].orderIndex else null
                                        val after = node.doc.orderIndex
                                        val newIdx = newIndex(before, after)
                                        onMoveRequest(draggingId!!, node.doc.parentId, newIdx)
                                    }
                                }
                                draggingId = null
                                dragOverId = null
                                dragOverPos = null
                                dragOverIntoId = null
                            }
                        }) {}
                    }

                    // Row contents
                    // Arrow
                    if (hasChildren) {
                        Span(attrs = {
                            onClick {
                                it.stopPropagation()
                                expanded[node.doc.key] = !isExpanded
                            }

                            style {
                                padding(8.px)
                                opacity(.5f)
                            }
                        }) { Text(if (isExpanded) "▾" else "▸") }
                    }

                    // Title
                    Span {
                        Text(node.doc.title)
                    }

                    // Row actions (e.g., add child) shown only for selected row when enabled
                    if (isSelected && showAddButtonForSelected) {
                        Div(attrs = {
                            classes(WorldDocsStyles.rowActions)
                        }) {
                            Span(attrs = {
                                classes(WorldDocsStyles.rowActionButton)
                                attr("title", Translations.str("Create doc"))
                                onClick {
                                    it.stopPropagation()
                                    onCreateChild(node.doc.key)
                                }
                            }) { Text("➕") }
                        }
                    }

                    // Bottom drop zone (after this node)
                    if (canDrag && draggingId != null) {
                        Div(attrs = {
                            classes(
                                if (dragOverId == node.doc.key && dragOverPos == DragPos.AFTER) WorldDocsStyles.dropZoneActive else WorldDocsStyles.dropZone,
                                WorldDocsStyles.dropZoneBottom
                            )
                            onDragOver {
                                it.preventDefault()
                                dragOverId = node.doc.key
                                dragOverPos = DragPos.AFTER
                                // Edge band hovered; hide INTO overlay
                                dragOverIntoId = null
                            }
                            onDragLeave {
                                dragOverId = null
                                dragOverPos = null
                            }
                            onDrop {
                                it.preventDefault()
                                if (draggingId != null && draggingId != node.doc.key) {
                                    val targetParentId = node.doc.parentId
                                    val invalid = when {
                                        targetParentId == null -> false
                                        targetParentId == draggingId -> true
                                        else -> isDescendant(docs, draggingId!!, targetParentId)
                                    }
                                    if (!invalid) {
                                        val siblings = siblingsOf(docs, node.doc.parentId)
                                        val idx = siblings.indexOfFirst { s -> s.key == node.doc.key }
                                        val before = node.doc.orderIndex
                                        val after =
                                            if (idx + 1 <= siblings.lastIndex) siblings[idx + 1].orderIndex else null
                                        val newIdx = newIndex(before, after)
                                        onMoveRequest(draggingId!!, node.doc.parentId, newIdx)
                                    }
                                }
                                draggingId = null
                                dragOverId = null
                                dragOverPos = null
                            }
                        }) {}
                    }
                }
            }
        }
    }
}

private enum class DragPos { BEFORE, AFTER }

private data class NodeLevel(val node: DocTree, val level: Int)

private fun buildVisibleList(nodes: List<DocTree>, expanded: Map<String, Boolean>): List<NodeLevel> {
    val result = mutableListOf<NodeLevel>()
    fun walk(n: DocTree, level: Int) {
        result += NodeLevel(n, level)
        val isExpanded = expanded[n.doc.key] ?: (level == 0)
        if (n.children.isNotEmpty() && isExpanded) {
            n.children.forEach { child -> walk(child, level + 1) }
        }
    }
    nodes.forEach { root -> walk(root, 0) }
    return result
}

private fun siblingsOf(forest: List<DocTree>, parentId: String?): List<Doc> {
    fun findById(node: DocTree, id: String): DocTree? {
        if (node.doc.key == id) return node
        for (c in node.children) {
            val found = findById(c, id)
            if (found != null) return found
        }
        return null
    }
    return if (parentId == null) {
        forest.map { it.doc }.sortedBy { it.orderIndex }
    } else {
        val parent = forest.asSequence().mapNotNull { findById(it, parentId) }.firstOrNull()
        parent?.children?.map { it.doc }?.sortedBy { it.orderIndex } ?: emptyList()
    }
}

private fun newIndex(before: Double?, after: Double?): Double = when {
    before == null && after == null -> 0.0
    before == null -> after!! - 1.0
    after == null -> before + 1.0
    else -> (before + after) / 2.0
}


private fun parentIdOf(forest: List<DocTree>, id: String): String? {
    fun findById(node: DocTree, needle: String): DocTree? {
        if (node.doc.key == needle) return node
        for (c in node.children) {
            val found = findById(c, needle)
            if (found != null) return found
        }
        return null
    }

    val target = forest.asSequence().mapNotNull { findById(it, id) }.firstOrNull()
    return target?.doc?.parentId
}

// Returns the list of ancestor ids from root to the parent of the given id
private fun ancestorsPath(forest: List<DocTree>, id: String): List<String> {
    fun findById(node: DocTree, needle: String): DocTree? {
        if (node.doc.key == needle) return node
        for (c in node.children) {
            val found = findById(c, needle)
            if (found != null) return found
        }
        return null
    }

    val target = forest.asSequence().mapNotNull { findById(it, id) }.firstOrNull() ?: return emptyList()
    val result = mutableListOf<String>()
    var parentId = target.doc.parentId
    while (parentId != null) {
        val parent = forest.asSequence().mapNotNull { findById(it, parentId!!) }.firstOrNull()
        if (parent != null) {
            result.add(parent.doc.key)
            parentId = parent.doc.parentId
        } else break
    }
    return result.asReversed()
}

// Returns true if descendantId is within the subtree of ancestorId
private fun isDescendant(forest: List<DocTree>, ancestorId: String, descendantId: String): Boolean {
    fun findById(node: DocTree, needle: String): DocTree? {
        if (node.doc.key == needle) return node
        for (c in node.children) {
            val found = findById(c, needle)
            if (found != null) return found
        }
        return null
    }

    val ancestor = forest.asSequence().mapNotNull { findById(it, ancestorId) }.firstOrNull() ?: return false
    fun contains(node: DocTree, needle: String): Boolean {
        if (node.doc.key == needle) return true
        return node.children.any { contains(it, needle) }
    }
    return contains(ancestor, descendantId)
}
