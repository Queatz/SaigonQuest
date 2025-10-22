package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import lib.marked
import lib.marked.parse
import main.theme.Styles
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Span
import org.w3c.dom.Element
import org.w3c.dom.HTMLSpanElement

@Composable
fun Md(
    content: String,
    attrs: AttrBuilderContext<HTMLSpanElement>? = null,
) {
    val html = remember(content) {
        val renderer = marked.Renderer()
        val originalLinkRenderer: dynamic = renderer.link

        renderer.html = { data ->
            data.text.replace("<", "&lt;").replace(">", "&gt;")
        }

        renderer.link = { data ->
            val localLink = data.href.startsWith("${window.location.protocol}//${window.location.hostname}")
            val html = originalLinkRenderer.call(renderer, data) as String
            if (localLink) {
                html
            } else {
                html.replace(
                    regex = "^<a ".toRegex(),
                    replacement = "<a target=\"_blank\" rel=\"noreferrer noopener nofollow\" "
                )
            }
        }

        // Create options object with renderer
        val options: dynamic = object {}
        options.renderer = renderer
        
        parse(
            text = content.replace("\n", "  \n"),
            options = options
        )
    }
    var element by remember { mutableStateOf<Element?>(null) }

    LaunchedEffect(html, element) {
        element?.innerHTML = html
    }

    Span(
        attrs = {
            classes(Styles.md)
            attrs?.invoke(this)

            ref {
                element = it

                onDispose {
                    element = null
                }
            }
        }
    )
}
