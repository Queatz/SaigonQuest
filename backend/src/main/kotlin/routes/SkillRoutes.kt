package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ErrorResponse
import models.Skill
import repository.SkillRepository
import repository.UserRepository

fun Routing.configureSkillRoutes() {
    val skillRepository = SkillRepository()
    val userRepository = UserRepository()
    
    route("/skills") {
        // Get all skills - no authentication required
        get {
            val skills = skillRepository.getAllSkills()
            call.respond(skills)
        }
        
        // Get skill by ID - no authentication required
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
            )
            
            val skill = skillRepository.getSkillById(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Skill not found", code = HttpStatusCode.NotFound.value)
                )
                
            call.respond(skill)
        }
        
        // Authenticated routes
        authenticate {
            // Create new skill - requires admin
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val skill = call.receive<Skill>()
                val createdSkill = skillRepository.create(skill)
                call.respond(HttpStatusCode.Created, createdSkill)
            }
            
            // Update skill - requires admin
            put("/{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )

                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@put

                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val skillUpdate = call.receive<Skill>()
                val updatedSkill = skillRepository.update(id, skillUpdate)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Failed to update skill", code = HttpStatusCode.NotFound.value)
                    )

                call.respond(updatedSkill)
            }
            
            // Delete skill - requires admin
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete

                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                val success = skillRepository.delete(id)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "Failed to delete skill", code = HttpStatusCode.InternalServerError.value)
                    )
                }
            }
            
            // Get users with a specific skill - requires admin
            get("/{id}/users") {
                val id = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get

                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                val users = userRepository.getUsersBySkill(id)
                call.respond(users)
            }
            
            // Manually set a user's skill level - requires admin
            post("/admin/user/{userId}/{skillId}/level") {
                val targetUserId = call.parameters["userId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing userId parameter", code = HttpStatusCode.BadRequest.value)
                )
                val skillId = call.parameters["skillId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing skillId parameter", code = HttpStatusCode.BadRequest.value)
                )
                
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val user = userRepository.getById(userId)
                if (user == null || !user.isAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                
                val level = try { call.receive<Int>() } catch (e: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid level", code = HttpStatusCode.BadRequest.value)
                    )
                }
                
                val updatedUser = userRepository.updateUserSkillLevel(targetUserId, skillId, level)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "User not found", code = HttpStatusCode.NotFound.value)
                    )
                
                call.respond(updatedUser)
            }
        }
    }
}
