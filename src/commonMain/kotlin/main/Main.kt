package main

import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.renderComposableInBody
import main.theme.Styles

fun main() {
    renderComposableInBody {
        Style(Styles)

        BrowserRouter(
            initPath = "/"
        ) {
            val router = Router.current

            route("/") {

            }
        }
    }
}
