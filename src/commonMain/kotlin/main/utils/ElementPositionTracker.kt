package main.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.DOMRect
import org.w3c.dom.HTMLElement

/**
 * External interface for ResizeObserver.
 * This is a JavaScript API that reports changes to the dimensions of an Element's content
 * or border box, or the bounding box of an SVGElement.
 */
@JsName("ResizeObserver")
external class ResizeObserver(callback: (Array<ResizeObserverEntry>, ResizeObserver) -> Unit) {
    /**
     * Starts observing the specified target element.
     */
    fun observe(target: HTMLElement)
    
    /**
     * Stops observing the specified target element.
     */
    fun unobserve(target: HTMLElement)
    
    /**
     * Disconnects the ResizeObserver instance and stops observing all target elements.
     */
    fun disconnect()
}

/**
 * External interface for ResizeObserverEntry.
 * Contains information about the size of the observed element.
 */
external interface ResizeObserverEntry {
    val target: HTMLElement
    val contentRect: DOMRect
}

/**
 * A utility class for tracking element positions.
 * This class handles tracking element positions using direct references to HTMLElements
 * and ResizeObserver to detect changes in element size or position.
 */
class ElementPositionTracker {
    // Map of elements to their position states
    private val elementPositions = mutableMapOf<HTMLElement, MutableState<DOMRect?>>()
    
    // Map of elements to their resize observers
    private val resizeObservers = mutableMapOf<HTMLElement, ResizeObserver>()
    
    /**
     * Tracks the position of an element.
     * Returns a MutableState that will be updated with the element's position.
     * Sets up a ResizeObserver to track changes to the element's size or position.
     *
     * @param element The HTMLElement to track
     * @return A MutableState containing the element's position
     */
    fun trackElement(element: HTMLElement): MutableState<DOMRect?> {
        // If we're already tracking this element, return the existing state
        if (element in elementPositions) {
            return elementPositions[element]!!
        }
        
        // Create a new state for this element
        val positionState = mutableStateOf<DOMRect?>(null)
        elementPositions[element] = positionState
        
        // Create a ResizeObserver for this element
        val observer = ResizeObserver { entries: Array<ResizeObserverEntry>, _: ResizeObserver ->
            // Update the position when the element resizes
            updateElementPosition(element)
        }
        
        // Start observing the element
        observer.observe(element)
        
        // Store the observer for cleanup later
        resizeObservers[element] = observer
        
        // Initial position update
        updateElementPosition(element)
        
        return positionState
    }
    
    /**
     * Stops tracking an element and cleans up its ResizeObserver.
     *
     * @param element The HTMLElement to stop tracking
     */
    fun untrackElement(element: HTMLElement) {
        // Remove the element from our tracking map
        elementPositions.remove(element)
        
        // Disconnect and remove the ResizeObserver
        resizeObservers[element]?.disconnect()
        resizeObservers.remove(element)
    }
    
    /**
     * Updates the positions of all tracked elements.
     * This should be called when the window is resized.
     */
    fun updateAllPositions() {
        elementPositions.forEach { (element, _) ->
            updateElementPosition(element)
        }
    }
    
    /**
     * Updates the position of a specific element.
     * This can be called when a specific element is added or its position changes.
     *
     * @param element The HTMLElement to update
     */
    fun updateElementPosition(element: HTMLElement) {
        val positionState = elementPositions[element] ?: return
        val newRect = element.getBoundingClientRect()
        val oldRect = positionState.value
        
        // Deep comparison of DOMRect values to avoid unnecessary state updates
        if (oldRect == null || 
            oldRect.top != newRect.top || 
            oldRect.left != newRect.left || 
            oldRect.width != newRect.width || 
            oldRect.height != newRect.height) {
            positionState.value = newRect
        }
    }
    
    /**
     * Gets the current position of an element.
     *
     * @param element The HTMLElement
     * @return The element's position, or null if the element hasn't been tracked
     */
    fun getElementPosition(element: HTMLElement): DOMRect? {
        return elementPositions[element]?.value
    }
    
    /**
     * Cleans up all ResizeObservers.
     * This should be called when the tracker is no longer needed.
     */
    fun dispose() {
        resizeObservers.forEach { (_, observer) ->
            observer.disconnect()
        }
        resizeObservers.clear()
        elementPositions.clear()
    }
}

/**
 * A composable function that creates and manages an ElementPositionTracker.
 * This function sets up the necessary event listeners and cleans up when disposed.
 *
 * @return An ElementPositionTracker instance
 */
@Composable
fun rememberElementPositionTracker(): ElementPositionTracker {
    val tracker = remember { ElementPositionTracker() }
    
    // Set up resize listener and cleanup
    DisposableEffect(Unit) {
        // Update positions when window is resized
        val resizeListener: (dynamic) -> Unit = {
            tracker.updateAllPositions()
        }
        
        window.addEventListener("resize", resizeListener)
        
        onDispose {
            window.removeEventListener("resize", resizeListener)
            tracker.dispose()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tracker.updateAllPositions()
        }
    }
    
    return tracker
}
