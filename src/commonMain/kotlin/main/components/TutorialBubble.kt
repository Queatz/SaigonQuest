package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.ExperimentalComposeWebApi
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.borderWidth
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.transform
import org.jetbrains.compose.web.css.unaryMinus
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import kotlin.math.sin

/**
 * Data class representing position values for UI elements.
 * All values are nullable to allow for flexible positioning.
 *
 * @property left The left position in pixels
 * @property top The top position in pixels
 * @property right The right position in pixels
 * @property bottom The bottom position in pixels
 */
data class PositionValues(
    val left: Int? = null,
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
)

/**
 * Enum defining different arrow directions for tutorial bubbles.
 */
enum class ArrowDirection {
    LEFT,   // Arrow points to the left (bubble is on the right of the target)
    RIGHT,  // Arrow points to the right (bubble is on the left of the target)
    TOP,    // Arrow points to the top (bubble is below the target)
    BOTTOM  // Arrow points to the bottom (bubble is above the target)
}

/**
 * A composable that displays a floating tutorial bubble.
 * The bubble floats by ~4px slowly and can be hidden when clicked.
 * The bubble can have an arrow pointing in different directions.
 *
 * @param text The text to display in the bubble
 * @param positionValues The position values for the bubble (left, top, right, bottom)
 * @param onDismiss Callback when the bubble is dismissed (clicked)
 * @param arrowDirection Direction the arrow should point (default is RIGHT)
 * @param backgroundColor Background color of the bubble (default is #333333)
 * @param textColor Text color of the bubble (default is white)
 * @param borderColor Border color of the bubble (default is #555555)
 * @param fontSize Font size of the text in pixels (default is 14)
 * @param padding Padding inside the bubble in pixels (default is 10)
 * @param floatAmount Amount of floating animation in pixels (default is 4)
 * @param visible Whether the bubble is visible (default is true)
 */
@OptIn(ExperimentalComposeWebApi::class)
@Composable
fun TutorialBubble(
    text: String,
    positionValues: PositionValues,
    onDismiss: () -> Unit,
    arrowDirection: ArrowDirection = ArrowDirection.RIGHT,
    backgroundColor: String = "#333333",
    textColor: String = "white",
    borderColor: String = "#555555",
    fontSize: Int = 14,
    padding: Int = 10,
    floatAmount: Float = 4f,
    visible: Boolean = true,
) {
    if (!visible) return
    
    // State for the floating animation
    var floatOffset by remember { mutableStateOf(0f) }
    
    // Animation effect for floating
    LaunchedEffect(Unit) {
        var time = 0f
        while (true) {
            // Use sine function to create a smooth floating effect
            floatOffset = sin(time) * floatAmount
            time += 0.05f
            delay(50) // Update every 50ms for smooth animation
        }
    }
    
    // Determine the transform based on arrow direction
    val transform = when (arrowDirection) {
        ArrowDirection.LEFT, ArrowDirection.RIGHT -> "translateY(-50%) translateX(${floatOffset}px)"
        ArrowDirection.TOP, ArrowDirection.BOTTOM -> "translateX(-50%) translateY(${floatOffset}px)"
    }
    
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                
                // Apply position values
                positionValues.left?.let { left(it.px) }
                positionValues.top?.let { top(it.px) }
                positionValues.right?.let { right(it.px) }
                positionValues.bottom?.let { bottom(it.px) }
                
                padding(padding.px)
                backgroundColor(Color(backgroundColor))
                color(Color(textColor))
                borderRadius(8.px)
                fontSize(fontSize.px)
                cursor("pointer")
                
                // Use property() for CSS properties not directly available
                property("box-shadow", "0 2px 10px rgba(0, 0, 0, 0.3)")
                property("z-index", "990")
                property("border", "1px solid $borderColor")
                property("transform", transform)
                property("pointer-events", "auto")
                property("max-width", "250px")
            }
            onClick { onDismiss() }
        }
    ) {
        Text(text)
        
        // Add the arrow as a separate div with direction based on arrowDirection
        (0..1).forEach { index ->
            Div(
                attrs = {
                    style {
                        position(Position.Absolute)
                        width(0.px)
                        height(0.px)

                        val bw = 10 + index
                        val bc = if (index == 0) backgroundColor else borderColor
                        val o = 10 + index

                        if (index == 0) {
                            property("z-index", "1")
                        }

                        when (arrowDirection) {
                            ArrowDirection.RIGHT -> {
                                // Arrow points to the right (bubble is on the left of the target)
                                right(-o.px)
                                top(50.percent)
                                transform {
                                    translateY(-50.percent)
                                }
                                borderWidth(bw.px, 0.px, bw.px, bw.px)
                                property("border-style", "solid")
                                property("border-color", "transparent transparent transparent $bc")
                            }

                            ArrowDirection.LEFT -> {
                                // Arrow points to the left (bubble is on the right of the target)
                                left(-o.px)
                                top(50.percent)
                                transform {
                                    translateY(-50.percent)
                                }
                                borderWidth(bw.px, bw.px, bw.px, 0.px)
                                property("border-style", "solid")
                                property("border-color", "transparent $bc transparent transparent")
                            }

                            ArrowDirection.BOTTOM -> {
                                // Arrow points to the bottom (bubble is above the target)
                                bottom(-o.px)
                                left(50.percent)
                                transform {
                                    translateX(-50.percent)
                                }
                                borderWidth(bw.px, bw.px, 0.px, bw.px)
                                property("border-style", "solid")
                                property("border-color", "$bc transparent transparent transparent")
                            }

                            ArrowDirection.TOP -> {
                                // Arrow points to the top (bubble is below the target)
                                top(-o.px)
                                left(50.percent)
                                transform {
                                    translateX(-50.percent)
                                }
                                borderWidth(0.px, bw.px, bw.px, bw.px)
                                property("border-style", "solid")
                                property("border-color", "transparent transparent $bc transparent")
                            }
                        }
                    }
                }
            )
        }
    }
}
