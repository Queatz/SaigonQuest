package main

import androidx.compose.runtime.key
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import lib.configureBabylonDracoDecoder
import main.api.WebSocketConnectionManager
import main.components.ByePage
import main.components.LanguageSettings
import main.components.PausedPage
import main.components.SceneComponent
import main.theme.AssetStyles
import main.theme.EditorStyles
import main.theme.InventoryStyles
import main.theme.NodeEditorStyles
import main.theme.NotificationStyles
import main.theme.OverlayStyles
import main.theme.PanelStyles
import main.theme.Styles
import main.theme.WorldDocsStyles
import main.theme.QuestStyles
import main.utils.PageTitleManager
import main.components.minigame.MinigameStyles
import main.components.tools.CharacterToolPage
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.renderComposableInBody
import kotlinx.browser.document
import androidx.compose.runtime.LaunchedEffect

fun main() {
    // Configure BabylonJS Draco decoder very early to ensure local asset URLs are used
    try {
        configureBabylonDracoDecoder("/babylon/Draco")
        console.log("[DracoConfig][startup] configured")
    } catch (t: Throwable) {
        console.warn("[Main] Failed to configure Babylon Draco decoder at startup, falling back to default CDN URLs", t)
    }

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
            Style(NotificationStyles)
            Style(EditorStyles)
            Style(InventoryStyles)
            Style(AssetStyles)
            Style(WorldDocsStyles)
            Style(MinigameStyles)
            Style(QuestStyles)

            // Update document title based on current world title
            LaunchedEffect(PageTitleManager.worldTitle) {
                val title = PageTitleManager.worldTitle
                if (title.isNullOrBlank()) {
                    document.title = "Saigon Quest"
                } else {
                    document.title = "$title • Saigon Quest"
                }
            }
            
            BrowserRouter(
                initPath = "/"
            ) {
                val router = Router.current

                // Render the global, floating AI chat box once (except on doc print page)
                if (!router.currentPath.path.startsWith("/doc/")) {
                    main.components.chat.GlobalAiChat()
                }

                route("/bye") {
                    ByePage()
                }

                route("/paused") {
                    PausedPage()
                }

                route("/doc") {
                    string { id ->
                        main.components.worlddocs.PrintDocPage(docId = id)
                    }
                }

                route("/tools") {
                    route("/character") {
                        CharacterToolPage()
                    }
                }

                // Catch-all route for any other path
                noMatch {
                    // Get the current path from the router
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
