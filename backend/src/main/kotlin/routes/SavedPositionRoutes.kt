package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ErrorResponse
import models.SavedPosition
import models.canUserEdit
import repository.SavedPositionRepository
import repository.WorldRepository

fun Routing.configureSavedPositionRoutes() {
    val savedPositionRepository = SavedPositionRepository()
    val worldRepository = WorldRepository()

    route("/saved-position") {
        authenticate {
            post {
                val pos = call.receive<SavedPosition>()
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                if (pos.worldId.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "World ID is required")
                    )
                }

                val world = worldRepository.getById(pos.worldId)
                if (world == null || !world.canUserEdit(userId)) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "You don't have permission to add saved positions to this world")
                    )
                }

                val created = savedPositionRepository.create(pos)
                call.respond(HttpStatusCode.Created, created)
            }
        }

        get {
            val worldId = call.request.queryParameters["worldId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing worldId parameter")
            )
            val positions = savedPositionRepository.getByWorldId(worldId)
            call.respond(positions)
        }

        authenticate {
            put("/{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val posUpdate = call.receive<SavedPosition>()
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put

                val existing = savedPositionRepository.getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
                val world = worldRepository.getById(existing.worldId)
                if (world == null || !world.canUserEdit(userId)) {
                    return@put call.respond(HttpStatusCode.Forbidden)
                }

                val updated = savedPositionRepository.update(id, posUpdate) ?: return@put call.respond(HttpStatusCode.NotFound)
                call.respond(updated)
            }

            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete

                val existing = savedPositionRepository.getById(id) ?: return@delete call.respond(HttpStatusCode.NotFound)
                val world = worldRepository.getById(existing.worldId)
                if (world == null || !world.canUserEdit(userId)) {
                    return@delete call.respond(HttpStatusCode.Forbidden)
                }

                val success = savedPositionRepository.delete(id)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}
