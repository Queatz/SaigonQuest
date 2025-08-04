import auth.JwtConfig
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import routes.configureChatRoutes
import routes.configureFriendRoutes
import routes.configureHealthRoutes
import routes.configureInventoryRoutes
import routes.configureItemRoutes
import routes.configureNotificationRoutes
import routes.configureObjectRoutes
import routes.configureUserRoutes
import routes.configureWebSocketRoutes
import routes.configureWorldRoutes

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // Install Content Negotiation with JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // Install CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        // Allow requests from any origin
        anyHost()
    }
    
    // Install WebSockets
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    // Install Authentication with JWT
    install(Authentication) {
        JwtConfig.configureKtorAuth(this)
    }
    
    // Configure routing
    routing {
        configureHealthRoutes()
        configureWorldRoutes()
        configureUserRoutes()
        configureObjectRoutes()
        configureChatRoutes()
        configureNotificationRoutes()
        configureFriendRoutes()
        configureInventoryRoutes()
        configureItemRoutes()
        configureWebSocketRoutes()
    }
}
