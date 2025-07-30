package main.utils

import org.w3c.dom.Element
import org.w3c.dom.DOMRect
import org.w3c.dom.Document
import org.w3c.dom.get
import org.w3c.dom.events.EventTarget
import kotlinx.browser.document

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
