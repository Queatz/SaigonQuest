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
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLMediaElement
import org.w3c.dom.HTMLSpanElement
import main.utils.createCustomEvent
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
                    window.document.createElement("u").let { el ->
                        el as HTMLElement
                        el.textContent = token["text"] as String
                        el.outerHTML
                    }
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
                    val href = token["href"] as String
                    window.document.createElement("audio").let { el ->
                        el as HTMLMediaElement
                        el.src = href
                        el.controls = true
                        el.outerHTML
                    }
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
                    val href = token["href"] as String
                    window.document.createElement("video").let { el ->
                        el as HTMLMediaElement
                        el.src = href
                        el.controls = true
                        el.outerHTML
                    }
                },
            ),
            json(
                "name" to "doc",
                "level" to "inline",
                "start" to { src: String -> src.indexOf("![doc]") },
                "tokenizer" to { src: String ->
                    val rule = Regex("^!\\[doc\\]\\((.*?)\\)")
                    val match = rule.find(src)
                    if (match != null) {
                        json(
                            "type" to "doc",
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
                    val docId = parts.getOrNull(0) ?: ""
                    val worldId = parts.getOrNull(1) ?: ""
                    val titleEnc = parts.getOrNull(2) ?: ""
                    val previewEnc = parts.getOrNull(3) ?: ""
                    val imageUrlEnc = parts.getOrNull(4) ?: ""
                    val title = try {
                        decodeURIComponent(titleEnc)
                    } catch (t: Throwable) {
                        titleEnc
                    }
                    val preview = try {
                        decodeURIComponent(previewEnc)
                    } catch (t: Throwable) {
                        previewEnc
                    }
                    val imageUrl = try {
                        decodeURIComponent(imageUrlEnc)
                    } catch (t: Throwable) {
                        imageUrlEnc
                    }

                    window.document.createElement("div").let { root ->
                        root as HTMLElement
                        root.className = "md-doc"
                        root.setAttribute("contenteditable", "false")
                        root.setAttribute("data-doc-id", docId)
                        root.setAttribute("data-world-id", worldId)
                        root.setAttribute("data-image", imageUrl)

                        if (imageUrl.isNotBlank()) {
                            val img = window.document.createElement("div") as HTMLElement
                            img.className = "md-doc-image"
                            img.style.backgroundImage = "url(\"$imageUrl\")"
                            root.appendChild(img)
                        }

                        val titleDiv = window.document.createElement("div") as HTMLElement
                        titleDiv.className = "md-doc-title"
                        titleDiv.textContent = title
                        root.appendChild(titleDiv)

                        val previewDiv = window.document.createElement("div") as HTMLElement
                        previewDiv.className = "md-doc-preview"
                        previewDiv.textContent = preview
                        root.appendChild(previewDiv)

                        root.outerHTML
                    }
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
                    } catch (t: Throwable) {
                        titleEnc
                    }
                    // Compute sprite background
                    val size = 48
                    val col = index % 10
                    val row = index / 10
                    val posX = -(col * size)
                    val posY = -((9 - row) * size)

                    window.document.createElement("div").let { root ->
                        root as HTMLElement
                        root.className = "md-item"
                        root.setAttribute("contenteditable", "false")
                        root.setAttribute("data-item", raw)
                        root.setAttribute("title", title)

                        val iconDiv = window.document.createElement("div") as HTMLElement
                        iconDiv.className = "md-item-icon"
                        iconDiv.style.width = "${size}px"
                        iconDiv.style.height = "${size}px"
                        iconDiv.style.backgroundImage = "url(\"/raw/data/spritesheets/$file.png\")"
                        iconDiv.style.backgroundSize = "${size * 10}px ${size * 10}px"
                        iconDiv.style.backgroundPosition = "${posX}px ${posY}px"
                        root.appendChild(iconDiv)

                        if (qty > 0) {
                            val qtyDiv = window.document.createElement("div") as HTMLElement
                            qtyDiv.className = "md-item-qty"
                            qtyDiv.textContent = "x$qty"
                            root.appendChild(qtyDiv)
                        }

                        val titleDiv = window.document.createElement("div") as HTMLElement
                        titleDiv.className = "md-item-title"
                        titleDiv.textContent = title
                        root.appendChild(titleDiv)

                        root.outerHTML
                    }
                },
            ),
        ),
    )

    marked.use(extension)
    extensionsRegistered = true
}

private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

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
        token.text.escapeHtml()
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
                        val span = window.document.createElement("span")
                        span.className = "word"
                        span.setAttribute("data-word", w)
                        span.textContent = w
                        result.add(span.outerHTML)
                        currentWord = StringBuilder()
                    }
                    result.add(char.toString().escapeHtml())
                }
            }
            if (currentWord.isNotEmpty()) {
                val w = currentWord.toString()
                val span = window.document.createElement("span")
                span.className = "word"
                span.setAttribute("data-word", w)
                span.textContent = w
                result.add(span.outerHTML)
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

    // Strip images and media first: ![alt](url)
    val mediaRegex = Regex("!\\[.*?\\]\\(.*?\\)")
    val contentWithoutMedia = content.replace(mediaRegex, "")

    // We use a simplified version of markdownToHtml or just use it as is
    // and then strip the HTML tags.
    val html = markdownToHtml(contentWithoutMedia)

    // Use a temporary element to extract text content
    val div = window.document.createElement("div")
    div.innerHTML = html

    // Replace multiple whitespaces/newlines with a single space to keep it clean for HUD
    return (div.textContent ?: "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun extractFirstImage(content: String): String? {
    if (content.isBlank()) return null
    // Standard markdown image: ![alt](url)
    val mediaRegex = Regex("!\\[.*?\\]\\((.*?)\\)")
    val match = mediaRegex.find(content)
    if (match != null) {
        val url = match.groupValues[1].trim()
        // Avoid doc/item custom types which use ![doc] or ![item]
        if (match.value.startsWith("![doc]") || match.value.startsWith("![item]")) {
            // Recurse after this match
            return extractFirstImage(content.substring(match.range.last + 1))
        }
        return url
    }
    return null
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

internal fun applyDocClickBehavior(root: Element) {
    try {
        val nodeList = root.querySelectorAll(".md-doc")
        for (i in 0 until nodeList.length) {
            val el = nodeList.item(i)
            if (el is HTMLElement) {
                val already = el.getAttribute("data-click-listener") != null
                if (!already) {
                    el.setAttribute("data-click-listener", "1")
                    el.onclick = { e ->
                        e.preventDefault()
                        e.stopPropagation()
                        val docId = el.getAttribute("data-doc-id")
                        val worldId = el.getAttribute("data-world-id")
                        val detail = json(
                            "docId" to docId,
                            "worldId" to worldId
                        )
                        val evt = createCustomEvent("openDoc", detail)
                        window.dispatchEvent(evt)
                    }
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
        // Handle doc preview clicks
        applyDocClickBehavior(container)
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
