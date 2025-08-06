package main

import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.renderComposableInBody
import main.api.WebSocketConnectionManager
import main.theme.Styles
import main.components.SceneComponent
import main.theme.NodeEditorStyles
import main.theme.NodeStyles
import main.theme.NotificationStyles

fun main() {
    // Initialize WebSocketConnectionManager
    WebSocketConnectionManager.initialize()
    console.log("WebSocketConnectionManager initialized")
    
    renderComposableInBody {
        Style(Styles)
        Style(NodeEditorStyles)
        Style(NodeStyles)
        Style(NotificationStyles)

        BrowserRouter(
            initPath = "/"
        ) {
            val router = Router.current

            // Home route (empty URL)
            route("/") {
                // Render the 3D scene with empty URL (Home World)
                SceneComponent(url = "")
            }
            
            // Catch-all route for any other path
            noMatch {
                // Get the current path from the window.location.pathname
                val path = kotlinx.browser.window.location.pathname
                val url = if (path.startsWith("/")) path.substring(1) else path
                
                // Render the 3D scene with the URL from the path
                SceneComponent(url = url)
            }
        }
    }
}
