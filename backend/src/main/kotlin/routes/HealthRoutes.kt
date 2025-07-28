package routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.HealthResponse

fun Routing.configureHealthRoutes() {
    route("/") {
        get {
            call.respond(HealthResponse(alive = true))
        }
    }
}
