package main.components.worlddocs

import models.Doc
import models.DocTree

object WorldDocsUtils {
    fun flatten(node: DocTree): List<Doc> = listOf(node.doc) + node.children.flatMap { flatten(it) }

    fun flatten(tree: List<DocTree>): List<Doc> {
        val list = mutableListOf<Doc>()
        fun walk(nodes: List<DocTree>) {
            nodes.forEach { n ->
                list.add(n.doc)
                if (n.children.isNotEmpty()) walk(n.children)
            }
        }
        walk(tree)
        return list
    }

    fun updateNode(node: DocTree, updated: Doc): DocTree {
        return if (node.doc.key == updated.key) {
            node.copy(doc = updated)
        } else {
            node.copy(children = node.children.map { updateNode(it, updated) })
        }
    }

    fun findNode(node: DocTree, id: String): DocTree? {
        if (node.doc.key == id) return node
        for (child in node.children) {
            val found = findNode(child, id)
            if (found != null) return found
        }
        return null
    }

    fun removeNode(list: List<DocTree>, id: String): List<DocTree> {
        fun removeFrom(node: DocTree): DocTree? {
            if (node.doc.key == id) return null
            val newChildren = node.children.mapNotNull { removeFrom(it) }
            return node.copy(children = newChildren)
        }
        return list.mapNotNull { removeFrom(it) }
    }

    fun filterTree(node: DocTree, query: String): DocTree? {
        if (query.isBlank()) return node
        val childMatches = node.children.mapNotNull { filterTree(it, query) }
        val selfMatches = node.doc.title.contains(query, ignoreCase = true)
        return if (selfMatches || childMatches.isNotEmpty()) node.copy(children = childMatches) else null
    }

    fun filterTreeByIds(node: DocTree, ids: Set<String>): DocTree? {
        val childMatches = node.children.mapNotNull { filterTreeByIds(it, ids) }
        val selfMatches = node.doc.key in ids
        return if (selfMatches || childMatches.isNotEmpty()) node.copy(children = childMatches) else null
    }

    fun siblingsOf(forest: List<DocTree>, parentId: String?): List<Doc> {
        val siblings: List<Doc> = if (parentId == null) {
            forest.map { it.doc }
        } else {
            val parent = forest.asSequence().mapNotNull { findNodeInForest(it, parentId) }.firstOrNull()
            parent?.children?.map { it.doc } ?: emptyList()
        }
        return siblings.sortedBy { it.orderIndex }
    }

    private fun findNodeInForest(node: DocTree, id: String): DocTree? {
        if (node.doc.key == id) return node
        for (c in node.children) {
            val found = findNodeInForest(c, id)
            if (found != null) return found
        }
        return null
    }

    fun newIndex(before: Double?, after: Double?): Double = when {
        before == null && after == null -> 0.0
        before == null -> after!! - 1.0
        after == null -> before + 1.0
        else -> (before + after) / 2.0
    }

    fun ancestorsOf(forest: List<DocTree>, id: String): List<Doc> {
        val startDoc = forest.asSequence().mapNotNull { findNodeInForest(it, id) }.firstOrNull()?.doc
            ?: return emptyList()
        val result = mutableListOf<Doc>()
        var parentId = startDoc.parentId
        while (parentId != null) {
            val parent = forest.asSequence().mapNotNull { findNodeInForest(it, parentId!!) }.firstOrNull()
            if (parent != null) {
                result += parent.doc
                parentId = parent.doc.parentId
            } else {
                break
            }
        }
        return result
    }

    fun parentIdOf(forest: List<DocTree>, id: String): String? {
        return forest.asSequence().mapNotNull { findNodeInForest(it, id) }.firstOrNull()?.doc?.parentId
    }

    fun ancestorsPath(forest: List<DocTree>, id: String): List<String> {
        val startDoc = forest.asSequence().mapNotNull { findNodeInForest(it, id) }.firstOrNull()?.doc
            ?: return emptyList()
        val result = mutableListOf<String>()
        var parentId = startDoc.parentId
        while (parentId != null) {
            result.add(parentId!!)
            val parent = forest.asSequence().mapNotNull { findNodeInForest(it, parentId!!) }.firstOrNull()
            parentId = parent?.doc?.parentId
        }
        return result
    }

    fun isDescendant(forest: List<DocTree>, ancestorId: String, descendantId: String): Boolean {
        val ancestorNode = forest.asSequence().mapNotNull { findNodeInForest(it, ancestorId) }.firstOrNull()
            ?: return false
        fun contains(node: DocTree, needle: String): Boolean {
            if (node.doc.key == needle) return true
            return node.children.any { contains(it, needle) }
        }
        return ancestorNode.children.any { contains(it, descendantId) }
    }
}
