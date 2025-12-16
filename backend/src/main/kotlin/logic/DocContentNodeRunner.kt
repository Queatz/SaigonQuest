package logic

import debug.WebSocketDebug
import models.Doc
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.DocRepository
import repository.WorldRepository
import utils.json

/**
 * Runner for DocContent nodes.
 *
 * Value-only node: reads a doc ID input and emits its content as TEXT if the
 * current user has access; otherwise emits an empty string.
 */
class DocContentNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    private val docRepo = DocRepository()
    private val worldRepo = WorldRepository()

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse DocContent node data", e, context.session)
            PropertyBasedNodeData()
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val docId = getInput(PropertyKey.ID)
        if (docId.isNullOrBlank()) {
            // No doc specified; produce empty
            eventData["${node.id}_out_0"] = ""
            eventData["${node.id}_out_1"] = ""
            eventData["${node.id}_text"] = ""
            eventData["${node.id}_title"] = ""
            WebSocketDebug.logMessage(
                "DOC_CONTENT_NODE",
                "No docId provided for node ${node.id}; emitting empty string",
                context.session
            )
            return true
        }

        val doc: Doc? = try { docRepo.getById(docId) } catch (_: Throwable) { null }
        if (doc == null) {
            eventData["${node.id}_out_0"] = ""
            eventData["${node.id}_out_1"] = ""
            eventData["${node.id}_text"] = ""
            eventData["${node.id}_title"] = ""
            WebSocketDebug.logMessage(
                "DOC_CONTENT_NODE",
                "Doc not found for id=$docId; emitting empty string",
                context.session
            )
            return true
        }

        // Permission check: owner OR world allows viewing (doc + all ancestors)
        val worldOwnerId = try { worldRepo.getById(doc.worldId)?.userId } catch (_: Throwable) { null }
        val userId = context.connection.userId
        val isOwner = worldOwnerId != null && userId == worldOwnerId
        val canViewByWorld = try {
            if (isOwner) true else {
                // Require worldUsersCanView to be true on doc and all ancestors
                val ancestors = try { DocRepository().getAncestors(docId) } catch (_: Throwable) { emptyList() }
                (doc.permissions.worldUsersCanView && ancestors.all { it.permissions.worldUsersCanView })
            }
        } catch (_: Throwable) { false }

        val content = if (isOwner || canViewByWorld) doc.content else ""
        val title = if (isOwner || canViewByWorld) doc.title else ""

        eventData["${node.id}_out_0"] = content
        eventData["${node.id}_out_1"] = title
        eventData["${node.id}_text"] = content
        eventData["${node.id}_title"] = title
        WebSocketDebug.logMessage(
            "DOC_CONTENT_NODE",
            "Emitting content length=${content.length}, title length=${title.length} for docId=$docId (allowed=${isOwner || canViewByWorld})",
            context.session
        )
        return true
    }
}
