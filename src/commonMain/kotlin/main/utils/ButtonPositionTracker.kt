package main.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import main.utils.getBoundingClientRectSafe
import main.utils.getDocument
import org.w3c.dom.DOMRect

/**
 * A utility class for tracking button positions.
 * This class handles the common pattern of tracking button positions for UI elements.
 */
class ButtonPositionTracker {
    // Map of button IDs to their position states
    private val buttonPositions = mutableMapOf<String, MutableState<DOMRect?>>()
    
    /**
     * Tracks the position of a button with the given ID.
     * Returns a MutableState that will be updated with the button's position.
     *
     * @param buttonId The ID of the button to track
     * @return A MutableState containing the button's position, or null if the button doesn't exist
     */
    fun trackButton(buttonId: String): MutableState<DOMRect?> {
        return buttonPositions.getOrPut(buttonId) { mutableStateOf(null) }
    }
    
    /**
     * Updates the positions of all tracked buttons.
     * This should be called when the window is resized or when buttons are added/removed.
     */
    fun updateAllPositions() {
        buttonPositions.forEach { (buttonId, positionState) ->
            val buttonElement = getDocument().getElementById(buttonId)
            if (buttonElement != null) {
                positionState.value = buttonElement.getBoundingClientRectSafe()
            }
        }
    }
    
    /**
     * Updates the position of a specific button.
     * This can be called when a specific button is added or its position changes.
     *
     * @param buttonId The ID of the button to update
     */
    fun updateButtonPosition(buttonId: String) {
        val positionState = buttonPositions[buttonId] ?: return
        val buttonElement = getDocument().getElementById(buttonId)
        if (buttonElement != null) {
            positionState.value = buttonElement.getBoundingClientRectSafe()
        }
    }
    
    /**
     * Gets the current position of a button.
     *
     * @param buttonId The ID of the button
     * @return The button's position, or null if the button doesn't exist or hasn't been tracked
     */
    fun getButtonPosition(buttonId: String): DOMRect? {
        return buttonPositions[buttonId]?.value
    }
}

/**
 * A composable function that creates and manages a ButtonPositionTracker.
 * This function sets up the necessary event listeners and updates button positions.
 *
 * @param buttonIds The IDs of the buttons to track
 * @return A ButtonPositionTracker instance
 */
@Composable
fun rememberButtonPositionTracker(buttonIds: List<String>): ButtonPositionTracker {
    val tracker = remember { ButtonPositionTracker() }
    
    // Create state objects for each button
    buttonIds.forEach { buttonId ->
        tracker.trackButton(buttonId)
    }
    
    // Set up resize listener and initial position calculation
    DisposableEffect(buttonIds) {
        // Update positions when window is resized
        val resizeListener: (dynamic) -> Unit = {
            tracker.updateAllPositions()
        }
        
        window.addEventListener("resize", resizeListener)
        
        // Initial position calculation
        tracker.updateAllPositions()
        
        onDispose {
            window.removeEventListener("resize", resizeListener)
        }
    }
    
    return tracker
}
