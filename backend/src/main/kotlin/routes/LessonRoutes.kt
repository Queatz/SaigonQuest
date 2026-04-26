package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.LessonScene
import repository.LessonRepository

fun Routing.configureLessonRoutes() {
    val repository = LessonRepository()

    route("/lessons/scenes") {
        authenticate {
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val scenes = repository.getLessonsByUserId(userId)
                call.respond(scenes)
            }

            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
                val scene = repository.getLessonById(id)
                if (scene == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(scene)
                }
            }

            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val scene = call.receive<LessonScene>()
                
                // Ensure the scene is owned by the user
                scene.createdBy = userId
                
                val existing = if (scene.key.isNotBlank()) repository.getLessonById(scene.key) else null
                
                val saved = if (existing == null) {
                    repository.createLesson(scene)
                } else {
                    // Verify ownership before update
                    if (existing.createdBy != userId) {
                        return@post call.respond(HttpStatusCode.Forbidden, "You do not own this lesson")
                    }
                    repository.updateLesson(scene)
                }
                call.respond(saved)
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
                
                val existing = repository.getLessonById(id)
                if (existing != null && existing.createdBy != userId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, "You do not own this lesson")
                }
                
                val deleted = repository.deleteLesson(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
