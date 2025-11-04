package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.Serializable
import models.Doc
import models.DocTree
import repository.DocRepository
import auth.JWTUtils
import repository.WorldRepository

/**
 * World Docs routes
 */
fun Routing.configureDocRoutes() {
    val docRepository = DocRepository()
    val worldRepository = WorldRepository()

    route("/worlds/{worldId}/docs") {
        // Get document tree for a world
        get {
            val worldId = call.parameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to "Missing worldId")
            )
            val tree: List<DocTree> = try {
                docRepository.getWorldDocTree(worldId)
            } catch (t: Throwable) {
                return@get call.respond(HttpStatusCode.InternalServerError, mapOf("message" to (t.message ?: "Error")))
            }
            call.respond(tree)
        }

        // Search docs by title
        get("/search") {
            val worldId = call.parameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to "Missing worldId")
            )
            val query = call.request.queryParameters["query"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to "Missing query")
            )
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            try {
                val results = docRepository.searchDocs(worldId, query, limit)
                call.respond(results)
            } catch (t: Throwable) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to (t.message ?: "Error")))
            }
        }

        // Create a new document (auth required)
        authenticate {
            post {
                val worldId = call.parameters["worldId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Missing worldId")
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                // Owner-only: verify requester owns the world
                val world = worldRepository.getById(worldId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "World not found"))
                if (world.userId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Only the world owner can create docs"))
                }

                val payload = try { call.receive<CreateDocRequest>() } catch (_: Throwable) { CreateDocRequest(title = "New doc", parentId = null) }
                val created: Doc = try {
                    docRepository.createDoc(
                        worldId = worldId,
                        title = payload.title.ifBlank { "Untitled" },
                        parentId = payload.parentId,
                        createdBy = userId,
                    )
                } catch (t: Throwable) {
                    return@post call.respond(HttpStatusCode.InternalServerError, mapOf("message" to (t.message ?: "Error")))
                }
                call.respond(HttpStatusCode.Created, created)
            }
        }
    }

    route("/docs") {
        // Get a single doc by id (basic)
        get("/{docId}") {
            val docId = call.parameters["docId"]
            if (docId.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Missing docId"))
            }
            val doc = docRepository.getById(docId)
            if (doc == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
            } else {
                call.respond(doc)
            }
        }

        // Mutations require authentication
        authenticate {
            // Update metadata
            put("/{docId}") {
                val docId = call.parameters["docId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Missing docId")
                )
                // Ensure user is authenticated
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put

                val doc = docRepository.getById(docId)
                    ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
                val worldOwnerId = worldRepository.getById(doc.worldId)?.userId
                val ancestors = runCatching { docRepository.getAncestors(docId) }.getOrNull().orEmpty()
                val canEdit = when {
                    worldOwnerId != null && userId == worldOwnerId -> true
                    doc.permissions.worldUsersCanEdit && ancestors.all { it.permissions.worldUsersCanEdit } -> true
                    else -> false
                }
                if (!canEdit) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Forbidden: cannot edit doc metadata"))
                }

                val req = try { call.receive<UpdateDocRequest>() } catch (_: Throwable) { UpdateDocRequest() }
                val updated = docRepository.updateDocMeta(
                    docId = docId,
                    title = req.title,
                    parentId = req.parentId,
                    orderIndex = req.orderIndex,
                ) ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
                call.respond(updated)
            }

            // Delete doc (owner-only)
            delete("/{docId}") {
                val docId = call.parameters["docId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Missing docId")
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete

                val doc = docRepository.getById(docId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
                val world = worldRepository.getById(doc.worldId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("message" to "World not found"))
                if (world.userId != userId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Only the world owner can delete docs"))
                }

                // Server-side guard: refuse delete if children exist
                if (docRepository.hasChildren(docId)) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Cannot delete: this doc has children")
                    )
                }

                val ok = docRepository.deleteDoc(docId)
                if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
            }

            // Move/reorder (owner or editor)
            post("/{docId}/move") {
                val docId = call.parameters["docId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Missing docId")
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val doc = docRepository.getById(docId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
                val worldOwnerId = worldRepository.getById(doc.worldId)?.userId
                val ancestors = runCatching { docRepository.getAncestors(docId) }.getOrNull().orEmpty()
                val canEdit = when {
                    worldOwnerId != null && userId == worldOwnerId -> true
                    doc.permissions.worldUsersCanEdit && ancestors.all { it.permissions.worldUsersCanEdit } -> true
                    else -> false
                }
                if (!canEdit) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Forbidden: cannot move/reorder doc"))
                }

                val req = try { call.receive<MoveDocRequest>() } catch (_: Throwable) { return@post call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid request")) }
                val updated = docRepository.moveDoc(
                    docId = docId,
                    newParentId = req.newParentId,
                    newOrderIndex = req.newOrderIndex,
                ) ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))

                // Optional renormalization: if sibling order indices are too dense, re-space them
                val epsilon = 1e-5
                try {
                    val minDelta = docRepository.minDeltaBetweenSiblings(updated.worldId, updated.parentId)
                    if (minDelta < epsilon) {
                        docRepository.renormalizeSiblings(updated.worldId, updated.parentId)
                    }
                } catch (_: Throwable) { /* best-effort only */ }

                call.respond(updated)
            }

            // Update content with optimistic versioning (owner or editor)
            put("/{docId}/content") {
                val docId = call.parameters["docId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Missing docId")
                )
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put

                val doc = docRepository.getById(docId)
                    ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
                val worldOwnerId = worldRepository.getById(doc.worldId)?.userId
                val ancestors = runCatching { docRepository.getAncestors(docId) }.getOrNull().orEmpty()
                val canEdit = when {
                    worldOwnerId != null && userId == worldOwnerId -> true
                    doc.permissions.worldUsersCanEdit && ancestors.all { it.permissions.worldUsersCanEdit } -> true
                    else -> false
                }
                if (!canEdit) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Forbidden: cannot edit doc content"))
                }

                val req = try { call.receive<UpdateContentRequest>() } catch (_: Throwable) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid request"))
                }
                try {
                    val updated = docRepository.saveContentAndVersion(
                        docId = docId,
                        newContent = req.content,
                        baseVersion = req.baseVersion,
                    ) ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("message" to "Doc not found"))
                    call.respond(updated)
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("message" to (e.message ?: "Version conflict")))
                } catch (t: Throwable) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("message" to (t.message ?: "Error")))
                }
            }
        }

        // WebSocket collaboration (manual auth like other WS routes)
        webSocket("/{docId}/collaborate") {
            val docId = call.parameters["docId"] ?: run {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing docId"))
                return@webSocket
            }

            // Accept token via query (?token=) or Authorization: Bearer <token>, and optional visitorId like other WS routes
            val headerAuth = call.request.headers[HttpHeaders.Authorization] ?: ""
            val bearerToken = headerAuth.removePrefix("Bearer ").trim()
            val tokenParam = call.parameters["token"] ?: ""
            val visitorId = call.parameters["visitorId"] ?: ""
            val rawToken = tokenParam.ifBlank { bearerToken }

            val userId: String = run {
                if (rawToken.isNotBlank()) {
                    try {
                        val decoded = auth.JwtConfig.verifier.verify(rawToken)
                        decoded.getClaim("userId").asString() ?: "Anonymous"
                    } catch (_: Throwable) {
                        "Anonymous"
                    }
                } else if (visitorId.isNotBlank()) {
                    repository.UserRepository().getByVisitorId(visitorId)?.key ?: "Anonymous"
                } else {
                    "Anonymous"
                }
            }

            val repo = docRepository

            // Compute edit permission: owner can edit; otherwise worldUsersCanEdit must be true and user authenticated
            val doc = runCatching { repo.getById(docId) }.getOrNull()
            val worldOwnerId = doc?.worldId?.let { runCatching { worldRepository.getById(it)?.userId }.getOrNull() }
            val ancestors = if (doc != null) runCatching { repo.getAncestors(doc.key) }.getOrNull().orEmpty() else emptyList()
            val canEditComputed = when {
                worldOwnerId != null && userId == worldOwnerId -> true
                doc != null && userId != "Anonymous" &&
                        doc.permissions.worldUsersCanEdit &&
                        ancestors.all { it.permissions.worldUsersCanEdit } -> true
                else -> false
            }

            service.CollaborationService.handle(
                call = call,
                ws = this,
                userId = userId,
                docId = docId,
                repo = repo,
                canEdit = canEditComputed,
            )
        }
    }
}

@Serializable
private data class CreateDocRequest(
    val title: String,
    val parentId: String? = null,
)

@Serializable
private data class UpdateDocRequest(
    val title: String? = null,
    val parentId: String? = null,
    val orderIndex: Double? = null,
)

@Serializable
private data class MoveDocRequest(
    val newParentId: String?,
    val newOrderIndex: Double,
)

@Serializable
private data class UpdateContentRequest(
    val content: String,
    val baseVersion: Int,
)
