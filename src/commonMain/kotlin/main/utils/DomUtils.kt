package main.utils

import org.w3c.dom.Element
import org.w3c.dom.DOMRect
import org.w3c.dom.Document
import org.w3c.dom.get
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Extension function to get an attribute from an Element.
 * This replaces the use of element.asDynamic().getAttribute().
 */
fun Element.getAttributeSafe(name: String): String? {
    return getAttribute(name)
}

/**
 * Extension function to get the bounding client rect of an Element.
 * This replaces the use of element.asDynamic().getBoundingClientRect().
 */
fun Element.getBoundingClientRectSafe(): DOMRect {
    return getBoundingClientRect()
}

/**
 * Extension function to safely cast an EventTarget to Element and get its bounding client rect.
 * This replaces the use of eventTarget.asDynamic().getBoundingClientRect().
 */
fun EventTarget.getBoundingClientRectSafe(): DOMRect? {
    return (this as? Element)?.getBoundingClientRect()
}

/**
 * Extension function to safely cast an EventTarget to Element and get an attribute.
 * This replaces the use of eventTarget.asDynamic().getAttribute().
 */
fun EventTarget.getAttributeSafe(name: String): String? {
    return (this as? Element)?.getAttribute(name)
}

/**
 * Utility function to get an element from a point in the document.
 * This replaces the use of js("document.elementFromPoint(x, y)").
 */
fun Document.getElementFromPoint(x: Float, y: Float): Element? {
    return elementFromPoint(x.toDouble(), y.toDouble())
}

/**
 * Extension function for Document to get the current document.
 * This provides a type-safe way to access the document.
 */
fun getDocument(): Document {
    return document
}

/**
 * Creates a CustomEvent with a detail object.
 * This replaces the use of js("new CustomEvent(...)").
 *
 * @param type The event type
 * @param detail The detail object to include in the event
 * @return The created CustomEvent
 */
fun createCustomEvent(type: String, detail: dynamic): Event {
    // Create options object with detail
    val options: dynamic = object {}
    options.detail = detail
    options.bubbles = true
    options.cancelable = true
    
    // Create the CustomEvent using the Kotlin/JS external class
    val customEvent = js("new CustomEvent(type, options)")
    
    return customEvent
}

/**
 * Creates a CustomEvent specifically for object drop events.
 *
 * @param x The x coordinate of the drop
 * @param y The y coordinate of the drop
 * @param data The JSON data of the dropped object
 * @return The created CustomEvent
 */
fun createObjectDropEvent(x: Int, y: Int, data: String): Event {
    // Create detail object
    val detail: dynamic = object {}
    detail.x = x
    detail.y = y
    detail.data = data
    
    return createCustomEvent("objectDrop", detail)
}
