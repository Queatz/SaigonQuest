package routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import models.UserRequestType
import utils.CostsProvider

fun Routing.configureCostsRoutes() {
    route("/costs") {
        get {
            // Single source of truth for costs
            val costs = CostsProvider.get()
            call.respond(costs)
        }

        // GET /costs/user-request-fee?type=Item|Wearable|Object|Node
        get("/user-request-fee") {
            val typeParam = call.request.queryParameters["type"]
            if (typeParam.isNullOrBlank()) {
                call.respondText("Missing type", status = io.ktor.http.HttpStatusCode.BadRequest)
                return@get
            }
            val type = try {
                UserRequestType.valueOf(typeParam)
            } catch (_: Throwable) {
                call.respondText("Invalid type", status = io.ktor.http.HttpStatusCode.BadRequest)
                return@get
            }
            val fee = CostsProvider.userRequestFee(type)
            call.respond(fee)
        }
    }
}
