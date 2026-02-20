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
            json(
                "name" to "video",
                "level" to "inline",
                "start" to { src: String -> src.indexOf("![video]") },
                "tokenizer" to { src: String ->
                    val rule = Regex("^!\\[video\\]\\((.*?)\\)")
                    val match = rule.find(src)
                    if (match != null) {
                        json(
                            "type" to "video",
                            "raw" to match.value,
                            "href" to match.groupValues[1].trim(),
                        )
                    } else {
                        null
                    }
                },
                "renderer" to { token: Json ->
                    "<video src=\"${token["href"] as String}\" controls style=\"max-width: 100%; display: flex; width: 100%; border-radius:1rem;\"></video>"
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
    clickableWords: Boolean = false,
): String {
    val renderer = marked.Renderer()
    val originalLinkRenderer: dynamic = renderer.link

    renderer.html = { token ->
        token.text.replace("<", "&lt;").replace(">", "&gt;")
    }

    if (clickableWords) {
        renderer.text = { token ->
            val text = token.text
            // Wrap words (sequences of letters/numbers) in clickable spans
            val result = mutableListOf<String>()
            var currentWord = StringBuilder()
            for (char in text) {
                if (char.isLetterOrDigit() || char == '\'' || char == '-') {
                    currentWord.append(char)
                } else {
                    if (currentWord.isNotEmpty()) {
                        val w = currentWord.toString()
                        result.add("<span class=\"word\" data-word=\"$w\">$w</span>")
                        currentWord = StringBuilder()
                    }
                    result.add(char.toString())
                }
            }
            if (currentWord.isNotEmpty()) {
                val w = currentWord.toString()
                result.add("<span class=\"word\" data-word=\"$w\">$w</span>")
            }
            result.joinToString("")
        }
    }

    renderer.link = { token ->
        val localLink = token.href.startsWith("${window.location.protocol}//${window.location.hostname}")
        val html = originalLinkRenderer.call(renderer, token) as String
        if (localLink) {
            html
        } else {
            html.replace(
                regex = "^<a ".toRegex(),
                replacement = "<a target=\"_blank\" rel=\"noreferrer noopener nofollow\" ",
            )
        }
    }

    val options = json("renderer" to renderer)

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
    clickableWords: Boolean = false,
    attrs: AttrBuilderContext<HTMLSpanElement>? = null,
) {
    val html = remember(content, clickableWords) { 
        markdownToHtml(content, softBreaks = false, clickableWords = clickableWords) 
    }
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
