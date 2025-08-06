package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import main.utils.ButtonPositionTracker
import main.utils.rememberButtonPositionTracker
import org.w3c.dom.DOMRect
import kotlin.math.abs

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
 * A manager component that handles the display of tutorial bubbles.
 * This component uses the ButtonPositionTracker to get button positions and the TutorialManager
 * to get step information, eliminating duplicate code for positioning and displaying bubbles.
 *
 * @param tutorialManager The tutorial manager instance
 * @param buttonPositionTracker The button position tracker instance
 * @param isVisible Whether the tutorial bubbles should be visible
 * @param defaultPosition The default position strategy for bubbles (default is LEFT)
 * @param horizontalOffset Additional horizontal offset for fine-tuning bubble position (default is -120)
 * @param verticalOffset Additional vertical offset for fine-tuning bubble position (default is -15)
 */
@Composable
fun TutorialBubbleManager(
    tutorialManager: TutorialManager,
    buttonPositionTracker: ButtonPositionTracker,
    isVisible: Boolean = true,
    defaultPosition: BubblePosition = BubblePosition.LEFT,
    horizontalOffset: Int = -120,
    verticalOffset: Int = -15
) {
    // Don't show anything if the tutorial is not visible or is completed
    if (!isVisible || !tutorialManager.isTutorialVisible || tutorialManager.isTutorialCompleted()) {
        return
    }
    
    // Get the current step info
    val currentStepInfo = tutorialManager.getCurrentStepInfo() ?: return
    
    // Get the target button position
    val targetButtonId = currentStepInfo.targetButtonId
    val buttonRect = buttonPositionTracker.getButtonPosition(targetButtonId) ?: return
    
    // Calculate the bubble position based on the positioning strategy
    val (bubbleLeft, bubbleTop) = calculateBubblePosition(
        buttonRect = buttonRect,
        position = defaultPosition,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset
    )
    
    // Display the tutorial bubble
    TutorialBubble(
        text = currentStepInfo.text,
        positionLeft = bubbleLeft,
        positionTop = bubbleTop,
        onDismiss = { tutorialManager.advanceToNextStep() }
    )
}

/**
 * Calculates the position of a tutorial bubble based on the target button position and the
 * positioning strategy.
 *
 * @param buttonRect The bounding rectangle of the target button
 * @param position The positioning strategy to use
 * @param horizontalOffset Additional horizontal offset for fine-tuning
 * @param verticalOffset Additional vertical offset for fine-tuning
 * @return A Pair of (left, top) coordinates for the bubble
 */
private fun calculateBubblePosition(
    buttonRect: DOMRect,
    position: BubblePosition,
    horizontalOffset: Int,
    verticalOffset: Int
): Pair<Int, Int> {
    return when (position) {
        BubblePosition.LEFT -> {
            // Position to the left of the button
            val left = (buttonRect.left + horizontalOffset).toInt()
            val top = (buttonRect.top + (buttonRect.height / 2) + verticalOffset).toInt()
            Pair(left, top)
        }
        BubblePosition.RIGHT -> {
            // Position to the right of the button
            val left = (buttonRect.right + abs(horizontalOffset)).toInt()
            val top = (buttonRect.top + (buttonRect.height / 2) + verticalOffset).toInt()
            Pair(left, top)
        }
        BubblePosition.TOP -> {
            // Position above the button
            val left = (buttonRect.left + (buttonRect.width / 2) + horizontalOffset).toInt()
            val top = (buttonRect.top + verticalOffset).toInt()
            Pair(left, top)
        }
        BubblePosition.BOTTOM -> {
            // Position below the button
            val left = (buttonRect.left + (buttonRect.width / 2) + horizontalOffset).toInt()
            val top = (buttonRect.bottom + abs(verticalOffset)).toInt()
            Pair(left, top)
        }
    }
}
