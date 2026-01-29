package main.components.media

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.js.Promise

fun findImageInClipboardEvent(event: dynamic): File? {
    val dt = event.clipboardData ?: return null
    val files = dt.files
    val length = files.length as Int
    for (i in 0 until length) {
        val f = files.item(i)
        if (f != null && f.type.startsWith("image/")) {
            return f
        }
    }
    return null
}

suspend fun readImageFromClipboard(): File? {
    try {
        val clipboard = window.navigator.asDynamic().clipboard
        if (clipboard == null) return null

        val items = (clipboard.read() as Promise<Array<dynamic>>).await()
        for (item in items) {
            for (type in item.types as Array<String>) {
                if (type.startsWith("image/")) {
                    val blob = (item.getType(type) as Promise<Blob>).await()
                    return File(arrayOf(blob), "pasted-image.png", js("({type: type})"))
                }
            }
        }
    } catch (t: Throwable) {
        console.error("Failed to read image from clipboard", t)
    }
    return null
}
