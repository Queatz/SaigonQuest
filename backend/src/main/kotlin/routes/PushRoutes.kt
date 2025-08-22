

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
import models.PushPublicKeyResponse
import models.PushSubscription
import models.PushSubscriptionRequest
import repository.PushSubscriptionRepository
import repository.UserRepository


fun Routing.configurePushRoutes() {
    val subsRepo = PushSubscriptionRepository()
    val userRepo = UserRepository()

    route("/notification/push") {
        authenticate {
            // Get public VAPID key (player only)
            get("/public-key") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val user = userRepo.getById(userId)
                if (user == null || !user.isPlayer) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Players only", HttpStatusCode.Forbidden.value))
                }
                val publicKey = System.getenv("VAPID_PUBLIC_KEY") ?: ""
                if (publicKey.isBlank()) {
                    return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("VAPID public key not configured", 503))
                }
                call.respond(PushPublicKeyResponse(publicKey))
            }

            // Subscribe or refresh current subscription
            post("/subscribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val user = userRepo.getById(userId)
                if (user == null || !user.isPlayer) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Players only", HttpStatusCode.Forbidden.value))
                }

                val req = call.receive<PushSubscriptionRequest>()
                if (req.endpoint.isBlank() || req.keys.p256dh.isBlank() || req.keys.auth.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid subscription", 400))
                }

                val sub = PushSubscription(
                    userId = userId,
                    endpoint = req.endpoint,
                    p256dh = req.keys.p256dh,
                    auth = req.keys.auth,
                    userAgent = req.ua ?: ""
                )
                val saved = subsRepo.upsert(sub)
                call.respond(saved)
            }

            // Unsubscribe current endpoint
            delete("/subscribe") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@delete
                val user = userRepo.getById(userId)
                if (user == null || !user.isPlayer) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Players only", HttpStatusCode.Forbidden.value))
                }

                val endpoint = call.request.queryParameters["endpoint"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest, ErrorResponse("Missing endpoint", 400)
                )
                val ok = subsRepo.deleteByEndpoint(userId, endpoint)
                if (ok) call.respond(mapOf("deleted" to true)) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", 404))
            }
        }
    }
}

