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
import kotlin.js.Json
import kotlin.js.json

private var extensionsRegistered: Boolean = false

internal fun ensureExtensionsRegistered() {
    if (extensionsRegistered) return

    val extension: Json = json(
        "extensions" to arrayOf(
            json(
                "name" to "underline",
                "level" to "inline",
                "start" to { src: String -> src.indexOf("<u>") },
                "tokenizer" to { src: String ->
                    val rule = Regex("^<u>(.*?)</u>")
                    val match = rule.find(src)
                    if (match != null) {
                        json(
                            "type" to "underline",
                            "raw" to match.value,
                            "text" to match.groupValues[1].trim(),
                        )
                    } else {
                        null
                    }
                },
                "renderer" to { token: Json ->
                    "<u>${token["text"] as String}</u>"
                },
            ),
            json(
                "name" to "audio",
                "level" to "inline",
                "start" to { src: String -> src.indexOf("![audio]") },
                "tokenizer" to { src: String ->
                    val rule = Regex("^!\\[audio\\]\\((.*?)\\)")
                    val match = rule.find(src)
                    if (match != null) {
                        json(
                            "type" to "audio",
                            "raw" to match.value,
                            "href" to match.groupValues[1].trim(),
                        )
                    } else {
                        null
                    }
                },
                "renderer" to { token: Json ->
                    "<audio src=\"${token["href"] as String}\" controls style=\"max-width: 100%; display: flex; width: 100%; border-radius:1rem;\"></audio>"
                },
            ),
        ),
    )

    marked.use(extension)
    extensionsRegistered = true
}

/**
 * Converts markdown to HTML using the same Marked configuration as the Md composable.
 *
 * softBreaks controls whether single newlines are converted to Markdown soft breaks ("  \n").
 * We disable soft breaks across the app to avoid extraneous <br> and selection/mapping issues.
 */
fun markdownToHtml(
    content: String,
    softBreaks: Boolean = false,
): String {
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
                replacement = "<a target=\"_blank\" rel=\"noreferrer noopener nofollow\" ",
            )
        }
    }

    val options: dynamic = object {}
    options.renderer = renderer

    ensureExtensionsRegistered()

    val text = if (softBreaks) content.replace("\n", "  \n") else content

    return parse(
        text = text,
        options = options,
    )
}

@Composable
fun Md(
    content: String,
    attrs: AttrBuilderContext<HTMLSpanElement>? = null,
) {
    val html = remember(content) { markdownToHtml(content, softBreaks = false) }
    var element by remember { mutableStateOf<Element?>(null) }

    LaunchedEffect(html, element) {
        val container = element ?: return@LaunchedEffect

        // Inject HTML
        container.innerHTML = html

        // After injection, attach load handlers to images so they fade in once ready
        applyImageLoadBehavior(container)
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
        },
    )
}
