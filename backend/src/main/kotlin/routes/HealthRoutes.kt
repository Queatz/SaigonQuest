package routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import models.HealthResponse

fun Routing.configureHealthRoutes() {
    route("/") {
        get {
            call.respond(HealthResponse(alive = true))
        }
    }
}
