package routes

import auth.JWTUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import models.MediaType
import models.UserMedia
import repository.UserMediaRepository

fun Routing.configureMediaRoutes() {
    val repo = UserMediaRepository()

    authenticate {
        // List media for the authenticated user
        get("/media") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserId(principal)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val items = repo.listForUser(userId, limit, offset)
            call.respond(HttpStatusCode.OK, items)
        }

        // Create a media record for the authenticated user
        post("/media") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserId(principal)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val req = call.receive<CreateMediaRequest>()
            val type = when (req.type.lowercase()) {
                "image" -> MediaType.Image
                "video" -> MediaType.Video
                else -> MediaType.Image
            }
            val saved = repo.insert(
                UserMedia(
                    userId = userId,
                    type = type,
                    path = req.path,
                    originalFileName = req.originalFileName
                )
            )
            call.respond(HttpStatusCode.Created, saved)
        }

        // Delete a media record owned by the authenticated user
        delete("/media/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = JWTUtils.getUserId(principal)
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")

            val deleted = repo.deleteForUser(userId, id)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent, "")
            } else {
                call.respond(HttpStatusCode.NotFound, "Not found")
            }
        }
    }
}

@Serializable
private data class CreateMediaRequest(
    val type: String,
    val path: String,
    val originalFileName: String
)
