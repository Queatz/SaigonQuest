package logic

import debug.WebSocketDebug
import models.Doc
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.DocRepository
import utils.json

/**
 * Runner for CreateDoc nodes.
 *
 * Creates a new Doc in the current world. If a parent doc id is provided but
 * the doc doesn't exist or belongs to a different world, the parent is ignored.
 * After creation, the provided text content is saved as the first version.
 */
class CreateDocNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    private val docRepo = DocRepository()

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse CreateDoc node data", e, context.session)
            PropertyBasedNodeData()
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val requestedParentId = getInput(PropertyKey.ID)
        val title = getInput(PropertyKey.TITLE) ?: ""
        val text = getInput(PropertyKey.TEXT_CONTENT) ?: ""

        // Validate parent doc: must exist and belong to this world
        val parentId = if (!requestedParentId.isNullOrBlank()) {
            val parent: Doc? = try { docRepo.getById(requestedParentId) } catch (_: Throwable) { null }
            if (parent != null && parent.worldId == context.worldId) parent.key else null
        } else null

        // Create the doc
        val creatorId = context.connection.userId
        val created: Doc = try {
            docRepo.createDoc(
                worldId = context.worldId,
                title = title,
                parentId = parentId,
                createdBy = creatorId,
            )
        } catch (e: Throwable) {
            WebSocketDebug.logError("CREATE_DOC_FAILED", e, context.session)
            return false
        }

        // Save initial content as version 1 (base version 0)
        try {
            docRepo.saveContentAndVersion(created.key, text, baseVersion = 0)
        } catch (e: Throwable) {
            // Non-fatal: doc exists without content
            WebSocketDebug.logError("CREATE_DOC_SAVE_CONTENT_FAILED for doc=${created.key}", e, context.session)
        }

        WebSocketDebug.logMessage(
            "CREATE_DOC_NODE",
            "Created doc id=${created.key} title='${title}' parent=${parentId ?: "<none>"} textLen=${text.length}",
            context.session
        )

        return true
    }
}
