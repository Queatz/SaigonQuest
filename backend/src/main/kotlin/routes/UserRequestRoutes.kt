package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.CreateUserRequestDto
import models.ErrorResponse
import models.UpdateUserRequestStatusDto
import models.UserRequestStatus
import models.AddUserRequestNoteDto
import models.UserRequestNote
import repository.UserRequestRepository
import repository.UserRepository

fun Routing.configureUserRequestRoutes() {
    val repo = UserRequestRepository()
    val users = UserRepository()

    authenticate {
        route("/user-requests") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val list = repo.listByUser(userId)
                call.respond(list)
            }

            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val dto = try {
                    call.receive<CreateUserRequestDto>()
                } catch (e: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request body", code = HttpStatusCode.BadRequest.value)
                    )
                }

                if (dto.title.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Title is required", code = HttpStatusCode.BadRequest.value)
                    )
                }
                if (dto.content.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Description is required", code = HttpStatusCode.BadRequest.value)
                    )
                }

                // Enforce min/max constraints
                val title = dto.title.trim()
                val content = dto.content.trim()
                if (title.length !in 3..80) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Title must be between 3 and 80 characters", code = HttpStatusCode.BadRequest.value)
                    )
                }
                if (content.length !in 10..2000) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Description must be between 10 and 2000 characters", code = HttpStatusCode.BadRequest.value)
                    )
                }

                try {
                    val created = repo.create(userId, dto)
                    call.respond(HttpStatusCode.Created, created)
                } catch (e: IllegalStateException) {
                    // Insufficient funds or validation thrown by repo
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = e.message ?: "Unable to create request", code = HttpStatusCode.BadRequest.value)
                    )
                } catch (t: Throwable) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = t.message ?: "Server error", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
        }

        // Admin routes
        route("/admin/user-requests") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val user = users.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Forbidden", HttpStatusCode.Forbidden.value))
                }
                val statusParam = call.request.queryParameters["status"]
                val status = statusParam?.let {
                    try { UserRequestStatus.valueOf(it) } catch (_: Throwable) { null }
                }
                val list = repo.listAll(status = status)
                call.respond(list)
            }

            patch("/{id}/status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@patch
                val adminUser = users.getById(userId)
                if (adminUser == null || !adminUser.isAdmin) {
                    return@patch call.respond(HttpStatusCode.Forbidden, ErrorResponse("Forbidden", HttpStatusCode.Forbidden.value))
                }
                val id = call.parameters["id"].orEmpty()
                if (id.isBlank()) {
                    return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id", HttpStatusCode.BadRequest.value))
                }
                val dto = try { call.receive<UpdateUserRequestStatusDto>() } catch (_: Throwable) {
                    return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body", HttpStatusCode.BadRequest.value))
                }
                val updated = repo.updateStatus(id, dto.status, adminUserId = userId, note = dto.note)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", HttpStatusCode.NotFound.value))
                } else {
                    call.respond(updated)
                }
            }

            post("/{id}/notes") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val adminUser = users.getById(userId)
                if (adminUser == null || !adminUser.isAdmin) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Forbidden", HttpStatusCode.Forbidden.value))
                }
                val id = call.parameters["id"].orEmpty()
                if (id.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id", HttpStatusCode.BadRequest.value))
                }
                val dto = try { call.receive<AddUserRequestNoteDto>() } catch (_: Throwable) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body", HttpStatusCode.BadRequest.value))
                }
                if (dto.note.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Note is required", HttpStatusCode.BadRequest.value))
                }
                val saved = repo.addNote(id, UserRequestNote(authorUserId = userId, text = dto.note))
                if (saved == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", HttpStatusCode.NotFound.value))
                } else {
                    call.respond(saved)
                }
            }
        }
    }
}
