import auth.JwtConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import routes.configureAssetRoutes
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
import routes.configurePushRoutes
import routes.configureAuthRoutes
import routes.configureAiChatRoutes
import routes.configureMediaRoutes
import routes.configureBalanceRoutes
import routes.configureAssetItemRoutes
import routes.configureCostsRoutes
import routes.configureDocRoutes
import routes.configureAiRoutes
import routes.configureVoiceRoutes
import routes.configureUserRequestRoutes
import utils.json
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // Install Content Negotiation with JSON
    install(ContentNegotiation) {
        json(json)
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
        // Note: Service worker and favicon are served by the frontend host in this setup.
        configureHealthRoutes()
        configureWorldRoutes()
        configureUserRoutes()
        configureObjectRoutes()
        configureChatRoutes()
        configureNotificationRoutes()
        configureFriendRoutes()
        configureInventoryRoutes()
        configureItemRoutes()
        configureAssetRoutes()
        configureAssetItemRoutes()
        configurePushRoutes()
        configureAuthRoutes()
        configureAiChatRoutes()
        configureMediaRoutes()
        configureBalanceRoutes()
        configureCostsRoutes()
        configureUserRequestRoutes()
        configureDocRoutes()
        configureAiRoutes()
        configureWebSocketRoutes()
        configureVoiceRoutes()
    }
}
