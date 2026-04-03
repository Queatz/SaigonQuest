package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import js.uri.decodeURIComponent
import kotlinx.browser.window
import lib.marked
import lib.marked.parse
import main.theme.Styles
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Span
import org.w3c.dom.Element
import org.w3c.dom.HTMLAudioElement
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
            json(
                "name" to "item",
                "level" to "inline",
                "start" to { src: String -> src.indexOf("![item]") },
                "tokenizer" to { src: String ->
                    val rule = Regex("^!\\[item\\]\\((.*?)\\)")
                    val match = rule.find(src)
                    if (match != null) {
                        json(
                            "type" to "item",
                            "raw" to match.value,
                            "data" to match.groupValues[1].trim(),
                        )
                    } else {
                        null
                    }
                },
                "renderer" to { token: Json ->
                    val raw = token["data"] as String
                    val parts = raw.split("|")
                    val file = parts.getOrNull(0) ?: ""
                    val indexStr = parts.getOrNull(1) ?: "0"
                    val titleEnc = parts.getOrNull(2) ?: ""
                    val qtyStr = parts.getOrNull(3) ?: "1"
                    val index = indexStr.toIntOrNull() ?: 0
                    val qty = qtyStr.toIntOrNull() ?: 1
                    val title = try {
                         decodeURIComponent(titleEnc)
                    } catch (e: dynamic) {
                        titleEnc
                    }
                    // Compute sprite background
                    val size = 48
                    val col = index % 10
                    val row = index / 10
                    val posX = -(col * size)
                    val posY = -((9 - row) * size)
                    val safeTitle = title
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                    "<div class=\"md-item\" contenteditable=\"false\" title=\"${safeTitle}\" style='display:inline-flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;padding:6px;border-radius:8px;background:rgba(255,255,255,0.06);width:4rem;height:6rem;flex-shrink:0;'>" +
                        "<div class=\"md-item-icon\" style='width:${size}px;height:${size}px;background-image:url(\"/raw/data/spritesheets/${file}.png\");background-size:${size * 10}px ${size * 10}px;background-repeat:no-repeat;image-rendering: pixelated;background-position:${posX}px ${posY}px;flex-shrink:0;'></div>" +
                        (if (qty > 0) "<div class=\"md-item-qty\" style='font-size:12px;margin-top:4px;'>x${qty}</div>" else "") +
                        "<div class=\"md-item-title\" style='font-size:12px;text-align:center;opacity:0.9;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;width:100%;'>${safeTitle}</div>" +
                    "</div>"
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

/**
 * Strips all markdown formatting and returns plain text.
 * Useful for HUD billboards where we don't want to show markdown markers or media.
 */
fun stripMarkdown(content: String): String {
    if (content.isBlank()) return ""

    // We use a simplified version of markdownToHtml or just use it as is
    // and then strip the HTML tags.
    val html = markdownToHtml(content)

    // Use a temporary element to extract text content
    val div = window.document.createElement("div")
    div.innerHTML = html

    // Replace multiple whitespaces/newlines with a single space to keep it clean for HUD
    return (div.textContent ?: "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun applyAudioExclusivePlayBehavior(root: Element) {
    try {
        val nodeList = root.querySelectorAll("audio")
        for (i in 0 until nodeList.length) {
            val el = nodeList.item(i)
            if (el is HTMLAudioElement) {
                val already = el.getAttribute("data-exclusive-listener") != null
                if (!already) {
                    el.setAttribute("data-exclusive-listener", "1")
                    el.addEventListener("play", { _ ->
                        try {
                            val all = window.document.querySelectorAll("audio")
                            for (j in 0 until all.length) {
                                val other = all.item(j)
                                if (other != el && other is HTMLAudioElement) {
                                    try { other.pause() } catch (_: Throwable) {}
                                }
                            }
                        } catch (_: Throwable) {}
                    })
                }
            }
        }
    } catch (_: Throwable) {}
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
        // Ensure only one audio plays at a time: when one starts, pause all others
        applyAudioExclusivePlayBehavior(container)
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
