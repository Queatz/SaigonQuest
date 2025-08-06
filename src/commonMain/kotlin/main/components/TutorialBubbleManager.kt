package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import main.utils.ElementPositionTracker
import main.utils.rememberElementPositionTracker
import org.w3c.dom.DOMRect
import org.w3c.dom.HTMLElement
import main.components.ArrowDirection
import main.components.PositionValues

/**
 * Enum defining different positioning strategies for tutorial bubbles.
 */
enum class BubblePosition {
    LEFT, // Position the bubble to the left of the target
    RIGHT, // Position the bubble to the right of the target
    TOP, // Position the bubble above the target
    BOTTOM // Position the bubble below the target
}

/**
 * Maps a BubblePosition to the corresponding ArrowDirection.
 * The arrow should point in the opposite direction of the bubble position.
 */
private fun BubblePosition.toArrowDirection(): ArrowDirection {
    return when (this) {
        BubblePosition.LEFT -> ArrowDirection.RIGHT   // Bubble on left, arrow points right
        BubblePosition.RIGHT -> ArrowDirection.LEFT   // Bubble on right, arrow points left
        BubblePosition.TOP -> ArrowDirection.BOTTOM   // Bubble on top, arrow points down
        BubblePosition.BOTTOM -> ArrowDirection.TOP   // Bubble on bottom, arrow points up
    }
}

/**
 * A manager component that handles the display of tutorial bubbles.
 * This component uses element refs to track button positions and the TutorialManager
 * to get step information, eliminating duplicate code for positioning and displaying bubbles.
 *
 * @param tutorialManager The tutorial manager instance
 * @param buttonRefs Map of button IDs to their element references
 * @param isVisible Whether the tutorial bubbles should be visible
 * @param defaultPosition The default position strategy for bubbles (default is LEFT)
 * @param backgroundColor Background color of the bubble (default is #333333)
 * @param textColor Text color of the bubble (default is white)
 * @param borderColor Border color of the bubble (default is #555555)
 * @param fontSize Font size of the text in pixels (default is 14)
 * @param padding Padding inside the bubble in pixels (default is 10)
 * @param floatAmount Amount of floating animation in pixels (default is 4)
 */
@Composable
fun TutorialBubbleManager(
    tutorialManager: TutorialManager,
    buttonRefs: Map<String, HTMLElement>,
    isVisible: Boolean = true,
    defaultPosition: BubblePosition = BubblePosition.LEFT,
    backgroundColor: String = "#333333",
    textColor: String = "white",
    borderColor: String = "#555555",
    fontSize: Int = 14,
    padding: Int = 10,
    floatAmount: Float = 4f,
) {
    // Don't show anything if the tutorial is not visible or is completed
    if (!isVisible || !tutorialManager.isTutorialVisible || tutorialManager.isTutorialCompleted()) {
        return
    }
    
    // Get the current step info
    val currentStepInfo = tutorialManager.getCurrentStepInfo() ?: return
    
    // Get the target button element
    val targetButtonId = currentStepInfo.targetButtonId
    val buttonElement = buttonRefs[targetButtonId] ?: return
    
    // Create an ElementPositionTracker to track the button position
    val tracker = rememberElementPositionTracker()
    
    // Track the button element
    tracker.trackElement(buttonElement)
    
    // Get the button position
    val buttonRect = tracker.getElementPosition(buttonElement) ?: return
    
    // Calculate the bubble position based on the positioning strategy
    val positionValues = calculateBubblePosition(
        buttonRect = buttonRect,
        position = defaultPosition
    )
    
    // Get the arrow direction based on the bubble position
    val arrowDirection = defaultPosition.toArrowDirection()
    
    // Display the tutorial bubble
    TutorialBubble(
        text = currentStepInfo.text,
        positionValues = positionValues,
        onDismiss = { tutorialManager.advanceToNextStep() },
        arrowDirection = arrowDirection,
        backgroundColor = backgroundColor,
        textColor = textColor,
        borderColor = borderColor,
        fontSize = fontSize,
        padding = padding,
        floatAmount = floatAmount,
    )
}

/**
 * Calculates the position of a tutorial bubble based on the target button position and the
 * positioning strategy.
 *
 * @param buttonRect The bounding rectangle of the target button
 * @param position The positioning strategy to use
 * @return A PositionValues object with the appropriate position values
 */
private fun calculateBubblePosition(
    buttonRect: DOMRect,
    position: BubblePosition,
): PositionValues {
    return when (position) {
        BubblePosition.LEFT -> {
            // Position to the left of the button, centered vertically
            val left = (buttonRect.left - 150).toInt() // Fixed offset for left positioning
            val top = (buttonRect.top + (buttonRect.height / 2)).toInt() // Center vertically
            PositionValues(left = left, top = top)
        }
        BubblePosition.RIGHT -> {
            // Position to the right of the button, centered vertically
            val left = (buttonRect.right + 20).toInt() // Fixed offset for right positioning
            val top = (buttonRect.top + (buttonRect.height / 2)).toInt() // Center vertically
            PositionValues(left = left, top = top)
        }
        BubblePosition.TOP -> {
            // Position above the button, centered horizontally
            val left = (buttonRect.left + (buttonRect.width / 2)).toInt() // Center horizontally
            val top = (buttonRect.top - 50).toInt() // Fixed offset for top positioning
            PositionValues(left = left, top = top)
        }
        BubblePosition.BOTTOM -> {
            // Position below the button, centered horizontally
            val left = (buttonRect.left + (buttonRect.width / 2)).toInt() // Center horizontally
            val top = (buttonRect.bottom + 20).toInt() // Fixed offset for bottom positioning
            PositionValues(left = left, top = top)
        }
    }
}
