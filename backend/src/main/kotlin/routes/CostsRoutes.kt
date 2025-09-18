package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.CostsProvider

fun Routing.configureCostsRoutes() {
    route("/costs") {
        get {
            // Single source of truth for costs
            val costs = CostsProvider.get()
            call.respond(HttpStatusCode.OK, costs)
        }
    }
}
