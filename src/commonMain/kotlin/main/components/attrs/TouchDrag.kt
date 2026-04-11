package main.components.attrs

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.css.cursor
import org.w3c.dom.CustomEvent
import org.w3c.dom.HTMLElement
import org.w3c.dom.Touch
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event
import utils.json
import main.utils.DataType

@Serializable
private data class TouchDropPayload(
    val clientX: Double,
    val clientY: Double,
    val data: String,
    val dataType: DataType,
)

private fun <TElement : HTMLElement> AttrsScope<TElement>.listen(
    type: String,
    listener: (Event) -> Unit,
) {
    ref { target ->
        target.addEventListener(type, listener)
        onDispose {
            target.removeEventListener(type, listener)
        }
    }
}

private fun dispatchTouchDrop(
    clientX: Double,
    clientY: Double,
    data: String,
    dataType: DataType,
) {
    val target = (document.elementFromPoint(clientX, clientY) as? HTMLElement)
        ?: (document.body as HTMLElement)

    val payload = TouchDropPayload(
        clientX = clientX,
        clientY = clientY,
        data = data,
        dataType = dataType,
    )

    // Create a CustomEvent via legacy init API to avoid js() object literals
    val event = document.createEvent("CustomEvent") as CustomEvent
    event.initCustomEvent(
        type = "touchdrop",
        bubbles = true,
        cancelable = true,
        detail = json.encodeToString(TouchDropPayload.serializer(), payload),
    )

    target.dispatchEvent(event)
}

fun <TElement : HTMLElement> AttrsScope<TElement>.touchDraggable(
    dataProvider: () -> String,
    dataType: DataType,
    previewBuilder: (HTMLElement) -> Unit = {},
) {
    var isDragging = false
    var preview: HTMLElement? = null
    var offsetX = 0.0
    var offsetY = 0.0

    // UX hint
    style { cursor("grab") }

    ref { element ->
        var onWindowTouchMove: ((Event) -> Unit)? = null
        var onWindowTouchEnd: ((Event) -> Unit)? = null

        fun updatePreviewPosition(clientX: Double, clientY: Double) {
            preview?.style?.transform = "translate(${clientX - offsetX}px, ${clientY - offsetY}px)"
        }

        fun detachWindowListeners() {
            onWindowTouchMove?.let { window.removeEventListener("touchmove", it, false) }
            onWindowTouchEnd?.let {
                window.removeEventListener("touchend", it, false)
                window.removeEventListener("touchcancel", it, false)
            }
            onWindowTouchMove = null
            onWindowTouchEnd = null
        }

        fun endDrag(clientX: Double, clientY: Double) {
            if (!isDragging) return
            dispatchTouchDrop(
                clientX = clientX,
                clientY = clientY,
                data = dataProvider(),
                dataType = dataType,
            )
            // Cleanup preview and listeners
            preview?.let { document.body?.removeChild(it) }
            preview = null
            detachWindowListeners()
            // Restore styles
            element.style.removeProperty("touch-action")
            element.style.removeProperty("user-select")
            isDragging = false
        }

        fun startDrag(clientX: Double, clientY: Double) {
            isDragging = true

            // Prevent native gestures only during drag
            element.style.setProperty("touch-action", "none")
            element.style.setProperty("user-select", "none")

            val p = document.createElement("div") as HTMLElement
            p.style.position = "fixed"
            p.style.left = "0px"
            p.style.top = "0px"
            p.style.setProperty("pointer-events", "none")
            p.style.zIndex = "1000"
            previewBuilder(p)
            document.body?.appendChild(p)
            preview = p

            // Center preview under the finger based on its own size
            val previewRect = p.getBoundingClientRect()
            val previewWidth = if (p.offsetWidth > 0) p.offsetWidth.toDouble() else previewRect.width
            val previewHeight = if (p.offsetHeight > 0) p.offsetHeight.toDouble() else previewRect.height
            offsetX = if (previewWidth > 0) previewWidth / 2.0 else 16.0
            offsetY = if (previewHeight > 0) previewHeight / 2.0 else 16.0

            p.style.transformOrigin = "50% 50%"
            updatePreviewPosition(clientX, clientY)

            // Attach global listeners only while dragging
            val move: (Event) -> Unit = { ev ->
                if (isDragging) {
                    val te = ev as TouchEvent
                    if (te.touches.length == 1) {
                        val t: Touch = te.touches.item(0)!!
                        updatePreviewPosition(t.clientX.toDouble(), t.clientY.toDouble())
                        ev.preventDefault()
                    }
                }
            }
            val ender: (Event) -> Unit = { ev ->
                val te = ev as TouchEvent
                val changed = te.changedTouches.item(0)
                val x = changed?.clientX?.toDouble() ?: 0.0
                val y = changed?.clientY?.toDouble() ?: 0.0
                if (isDragging) {
                    endDrag(x, y)
                    ev.preventDefault()
                }
            }
            onWindowTouchMove = move
            onWindowTouchEnd = ender
            window.addEventListener("touchmove", move, false)
            window.addEventListener("touchend", ender, false)
            window.addEventListener("touchcancel", ender, false)
        }

        val handleTouchStart: (Event) -> Unit = { e ->
            val te = e as TouchEvent
            if (te.touches.length == 1) {
                val t: Touch = te.touches.item(0)!!
                startDrag(t.clientX.toDouble(), t.clientY.toDouble())
                e.preventDefault()
            }
        }

        element.addEventListener("touchstart", handleTouchStart, false)

        onDispose {
            preview?.let { document.body?.removeChild(it) }
            preview = null
            val wasDragging = isDragging
            isDragging = false
            element.removeEventListener("touchstart", handleTouchStart, false)
            detachWindowListeners()
            if (wasDragging) {
                element.style.removeProperty("touch-action")
                element.style.removeProperty("user-select")
            }
        }
    }
}

fun <TElement : HTMLElement> AttrsScope<TElement>.onTouchDrop(
    handler: (clientX: Double, clientY: Double, data: String, dataType: DataType) -> Unit,
) {
    listen("touchdrop") { e ->
        val ev = e as CustomEvent
        val detailJson = ev.detail as? String ?: return@listen
        val payload = json.decodeFromString(TouchDropPayload.serializer(), detailJson)
        handler(payload.clientX, payload.clientY, payload.data, payload.dataType)
    }
}
