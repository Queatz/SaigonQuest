package main

import androidx.compose.runtime.key
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.renderComposableInBody
import main.api.WebSocketConnectionManager
import main.theme.Styles
import main.components.SceneComponent
import main.components.ByePage
import main.components.PausedPage
import main.theme.EditorStyles
import main.theme.InventoryStyles
import main.theme.NodeEditorStyles
import main.theme.NodeStyles
import main.theme.NotificationStyles

fun main() {
    WebSocketConnectionManager.initialize()
    console.log("WebSocketConnectionManager initialized")
    
    renderComposableInBody {
        Style(Styles)
        Style(NodeEditorStyles)
        Style(NodeStyles)
        Style(NotificationStyles)
        Style(EditorStyles)
        Style(InventoryStyles)

        BrowserRouter(
            initPath = "/"
        ) {
            val router = Router.current

            route("/bye") {
                ByePage()
            }

            route("/paused") {
                PausedPage()
            }

            // Catch-all route for any other path
            noMatch {
                // Get the current path from the window.location.pathname
                val url = router.currentPath.path.trimStart('/')

                // Render the 3D scene with the URL from the path or Bye page
                key(url) {
                    SceneComponent(url = url)
                }
            }
        }
    }
}
