package main

import androidx.compose.runtime.key
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import main.api.WebSocketConnectionManager
import main.components.ByePage
import main.components.LanguageSettings
import main.components.PausedPage
import main.components.SceneComponent
import main.theme.EditorStyles
import main.theme.InventoryStyles
import main.theme.NodeEditorStyles
import main.theme.NodeStyles
import main.theme.NotificationStyles
import main.theme.Styles
import main.theme.AssetStyles
import main.theme.OverlayStyles
import main.theme.PanelStyles
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.renderComposableInBody

fun main() {
    WebSocketConnectionManager.initialize()
    console.log("WebSocketConnectionManager initialized")

    // Init user language
    LanguageSettings.getCurrentLanguage()
    
    try {
        renderComposableInBody {
            Style(Styles)
            Style(OverlayStyles)
            Style(PanelStyles)
            Style(NodeEditorStyles)
            Style(NodeStyles)
            Style(NotificationStyles)
            Style(EditorStyles)
            Style(InventoryStyles)
            Style(AssetStyles)

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
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}
