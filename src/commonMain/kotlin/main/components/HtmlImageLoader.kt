package main.components

import org.w3c.dom.Element
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event

/**
 * Attach load/error handlers to all <img> elements within [container] so that
 * they reveal with the same behavior used by the Md component.
 *
 * Expected CSS behavior (in Styles):
 * - Images start hidden (e.g., opacity 0) and get class "loaded" when ready
 */
fun applyImageLoadBehavior(container: Element) {
    val images = container.getElementsByTagName("img")
    for (i in 0 until images.length) {
        val img = images.item(i) as? HTMLImageElement ?: continue

        // Ensure we start hidden (CSS handles opacity 0), and reveal when ready
        img.classList.remove("loaded")

        // If already loaded from cache, mark as loaded immediately
        if (img.complete && img.naturalWidth > 0) {
            img.classList.add("loaded")
            continue
        }

        var onLoad: ((Event) -> Unit)? = null
        var onError: ((Event) -> Unit)? = null

        onLoad = {
            img.classList.add("loaded")
            // Clean up listeners
            img.removeEventListener("load", onLoad!!)
            img.removeEventListener("error", onError!!)
        }

        // In case of error, still reveal the element so the broken image indicator shows
        onError = {
            img.classList.add("loaded")
            img.removeEventListener("load", onLoad!!)
            img.removeEventListener("error", onError!!)
        }

        img.addEventListener("load", onLoad)
        img.addEventListener("error", onError)
    }
}
