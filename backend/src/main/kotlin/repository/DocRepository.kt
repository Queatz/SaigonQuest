package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.entity.DocumentUpdateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Doc
import models.DocTree
import kotlin.time.Clock

class DocRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.DOCS_COLLECTION)

    fun hasChildren(docId: String): Boolean {
        val query = """
            FOR d IN @@collection
            FILTER d.parentId == @docId
            LIMIT 1
            RETURN d._key
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.DOCS_COLLECTION,
            "docId" to docId,
        )
        val cursor = ArangoConfig.db.query(query, String::class.java, bind)
        return cursor.hasNext()
    }

    fun createDoc(
        worldId: String,
        title: String,
        parentId: String? = null,
        createdBy: String = "",
    ): Doc {
        val now = Clock.System.now()
        val nextIndex = nextOrderIndex(worldId, parentId)
        val doc = Doc(
            worldId = worldId,
            title = title.ifBlank { "Untitled" },
            content = "",
            parentId = parentId,
            orderIndex = nextIndex,
            createdBy = createdBy,
            version = 0,
            updatedAt = now,
        )
        val result: DocumentCreateEntity<Doc> = collection.insertDocument(
            doc,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    /**
     * Saves new content for the doc if the provided baseVersion matches the current version.
     * Increments version and updates updatedAt.
     * Returns the updated Doc on success, null if not found.
     * Throws IllegalStateException on version conflict.
     */
    fun saveContentAndVersion(
        docId: String,
        newContent: String,
        baseVersion: Int,
    ): Doc? {
        val existing = getById(docId) ?: return null
        if (existing.version != baseVersion) {
            throw IllegalStateException("Version conflict: expected ${'$'}baseVersion, found ${'$'}{existing.version}")
        }
        val updated = existing.copy(
            content = newContent,
            version = existing.version + 1,
            updatedAt = Clock.System.now(),
        ).also { it.key = existing.key }
        val result: DocumentUpdateEntity<Doc> = collection.updateDocument(
            docId,
            updated,
            DocumentUpdateOptions().returnNew(true)
        )
        return result.new
    }

    fun getById(id: String): Doc? {
        return try {
            collection.getDocument(id, Doc::class.java, DocumentReadOptions())
        } catch (_: Throwable) {
            null
        }
    }

    fun getByWorldId(worldId: String): List<Doc> {
        val query = """
            FOR d IN @@collection
            FILTER d.worldId == @worldId
            SORT d.parentId, d.orderIndex
            RETURN d
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.DOCS_COLLECTION,
            "worldId" to worldId,
        )
        return ArangoConfig.db.query(query, Doc::class.java, bind).toList()
    }

    fun getWorldDocTree(worldId: String): List<DocTree> {
        val docs = getByWorldId(worldId)
        return buildTree(docs)
    }

    fun searchDocs(worldId: String, query: String, limit: Int = 50): List<Doc> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val aql = """
            LET q = LOWER(@q)
            FOR d IN @@collection
                FILTER d.worldId == @worldId && CONTAINS(LOWER(d.title), q)
                SORT d.title
                LIMIT @limit
                RETURN d
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.DOCS_COLLECTION,
            "worldId" to worldId,
            "q" to q,
            "limit" to limit,
        )
        return ArangoConfig.db.query(aql, Doc::class.java, bind).toList()
    }

    fun updateDocMeta(
        docId: String,
        title: String? = null,
        parentId: String? = null,
        orderIndex: Double? = null,
    ): Doc? {
        val existing = getById(docId) ?: return null
        val updated = existing.copy(
            title = title ?: existing.title,
            parentId = parentId ?: existing.parentId,
            orderIndex = orderIndex ?: existing.orderIndex,
            updatedAt = Clock.System.now()
        ).also { it.key = existing.key }
        val result: DocumentUpdateEntity<Doc> = collection.updateDocument(
            docId,
            updated,
            DocumentUpdateOptions().returnNew(true)
        )
        return result.new
    }

    fun moveDoc(
        docId: String,
        newParentId: String?,
        newOrderIndex: Double,
    ): Doc? {
        return updateDocMeta(
            docId = docId,
            parentId = newParentId,
            orderIndex = newOrderIndex,
        )
    }

    fun deleteDoc(docId: String): Boolean {
        return try {
            collection.deleteDocument(docId)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildTree(docs: List<Doc>): List<DocTree> {
        val byParent: Map<String?, List<Doc>> = docs.groupBy { it.parentId }
        fun build(parentId: String?): List<DocTree> {
            val children = byParent[parentId].orEmpty().sortedBy { it.orderIndex }
            return children.map { d ->
                DocTree(
                    doc = d,
                    children = build(d.key)
                )
            }
        }
        return build(null)
    }

    private fun nextOrderIndex(worldId: String, parentId: String?): Double {
        val query = """
            FOR d IN @@collection
            FILTER d.worldId == @worldId && (
                (@parentId == null && d.parentId == null) || d.parentId == @parentId
            )
            SORT d.orderIndex DESC
            LIMIT 1
            RETURN d.orderIndex
        """.trimIndent()
        val bind = mutableMapOf<String, Any?>(
            "@collection" to CollectionsConfig.DOCS_COLLECTION,
            "worldId" to worldId,
            "parentId" to parentId
        )
        val cursor = ArangoConfig.db.query(query, Double::class.java, bind)
        val maxIndex = if (cursor.hasNext()) cursor.next() else null
        return (maxIndex ?: -1.0) + 1.0
    }

    fun getAncestors(docId: String): List<Doc> {
        val result = mutableListOf<Doc>()
        var current = getById(docId)
        while (current?.parentId != null) {
            val parent = getById(current.parentId!!)
            if (parent != null) {
                result.add(parent)
                current = parent
            } else {
                break
            }
        }
        return result
    }

    fun getSiblings(worldId: String, parentId: String?): List<Doc> {
        val query = """
            FOR d IN @@collection
            FILTER d.worldId == @worldId && (
                (@parentId == null && d.parentId == null) || d.parentId == @parentId
            )
            SORT d.orderIndex
            RETURN d
        """.trimIndent()
        val bind = mutableMapOf<String, Any?>(
            "@collection" to CollectionsConfig.DOCS_COLLECTION,
            "worldId" to worldId,
            "parentId" to parentId
        )
        return ArangoConfig.db.query(query, Doc::class.java, bind).toList()
    }

    fun minDeltaBetweenSiblings(worldId: String, parentId: String?): Double {
        val siblings = getSiblings(worldId, parentId)
        var min = Double.POSITIVE_INFINITY
        for (i in 1 until siblings.size) {
            val delta = kotlin.math.abs(siblings[i].orderIndex - siblings[i - 1].orderIndex)
            if (delta < min) min = delta
        }
        return if (min.isInfinite()) Double.POSITIVE_INFINITY else min
    }

    fun renormalizeSiblings(worldId: String, parentId: String?): Boolean {
        val siblings = getSiblings(worldId, parentId)
        if (siblings.isEmpty()) return false
        var changed = false
        siblings.forEachIndexed { index, d ->
            val desired = index.toDouble()
            if (d.orderIndex != desired) {
                changed = true
                updateDocMeta(
                    docId = d.key,
                    orderIndex = desired,
                )
            }
        }
        return changed
    }
}
