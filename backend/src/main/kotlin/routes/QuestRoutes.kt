package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.ErrorResponse
import models.Quest
import models.QuestState
import models.PlayerQuestState
import repository.QuestRepository
import repository.UserRepository

fun Routing.configureQuestRoutes() {
    val questRepository = QuestRepository()
    val userRepository = UserRepository()

    route("/quests") {
        // Authenticated routes
        authenticate {
            // Get all quests (for selecting in node editor)
            get {
                val quests = questRepository.getAllQuests()
                call.respond(quests)
            }

            // Create new quest (admin only)
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }
                
                if (user?.isAdmin != true) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can create quests", code = 403))
                }

                val quest = call.receive<Quest>()
                val createdQuest = questRepository.createQuest(quest)
                call.respond(HttpStatusCode.Created, createdQuest)
            }

            // Delete quest (admin only)
            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }
                
                if (user?.isAdmin != true) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can delete quests", code = 403))
                }

                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing id", code = 400))
                questRepository.deleteQuest(id)
                call.respond(HttpStatusCode.NoContent)
            }

            // Get global states for a quest
            get("/{id}/states") {
                val questId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing quest id", code = 400))
                val states = questRepository.getQuestStatesByQuestId(questId)
                call.respond(states)
            }

            // Create global state definition (admin only)
            post("/{id}/states") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }
                
                if (user?.isAdmin != true) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can update global quest states", code = 403))
                }

                val questId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing quest id", code = 400))
                val state = call.receive<QuestState>()
                val updated = questRepository.updateQuestState(questId, state.name, state.value)
                call.respond(updated)
            }

            // Delete global state (admin only)
            delete("/{id}/states/{name}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }
                
                if (user?.isAdmin != true) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can delete global quest states", code = 403))
                }

                val questId = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing quest id", code = 400))
                val name = call.parameters["name"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing state name", code = 400))
                questRepository.deleteQuestState(questId, name)
                call.respond(HttpStatusCode.NoContent)
            }

            // Get player-local states for current user
            get("/{id}/player-states") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal) ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val questId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing quest id", code = 400))
                
                val query = "FOR ps IN @@collection FILTER ps.questId == @questId AND ps.userId == @userId RETURN ps"
                val bindVars = mapOf(
                    "@collection" to database.CollectionsConfig.PLAYER_QUEST_STATES_COLLECTION,
                    "questId" to questId,
                    "userId" to userId
                )
                val results = database.ArangoConfig.db.query(query, PlayerQuestState::class.java, bindVars).toList()
                call.respond(results)
            }

            // Get all player states for a quest (admin only)
            get("/{id}/player-states/all") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }
                
                if (user?.isAdmin != true) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can view all player quest states", code = 403))
                }

                val questId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing quest id", code = 400))
                val results = questRepository.getAllPlayerQuestStates(questId)
                call.respond(results)
            }

            // Update or create player state (admin only)
            post("/player-states") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }

                if (user?.isAdmin != true) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can update player quest states", code = 403))
                }

                val playerState = call.receive<PlayerQuestState>()
                val updated = questRepository.updatePlayerQuestState(
                    playerState.questId,
                    playerState.userId,
                    playerState.name,
                    playerState.value
                )
                call.respond(updated)
            }

            // Delete player state (admin only)
            delete("/player-states/{playerStateId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserId(principal)
                val user = userId?.let { userRepository.getById(it) }
                
                if (user?.isAdmin != true) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse(message = "Only admins can delete player quest states", code = 403))
                }

                val playerStateId = call.parameters["playerStateId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(message = "Missing player state id", code = 400))
                questRepository.deletePlayerQuestState(playerStateId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
