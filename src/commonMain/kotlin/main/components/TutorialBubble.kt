package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import kotlin.math.sin

/**
 * A composable that displays a floating tutorial bubble.
 * The bubble floats horizontally by ~4px slowly and can be hidden when clicked.
 *
 * @param text The text to display in the bubble
 * @param positionLeft The left position of the bubble in pixels
 * @param positionTop The top position of the bubble in pixels
 * @param onDismiss Callback when the bubble is dismissed (clicked)
 * @param visible Whether the bubble is visible
 */
@Composable
fun TutorialBubble(
    text: String,
    positionLeft: Int,
    positionTop: Int,
    onDismiss: () -> Unit,
    visible: Boolean = true
) {
    if (!visible) return
    
    // State for the floating animation
    var floatOffset by remember { mutableStateOf(0f) }
    
    // Animation effect for floating
    LaunchedEffect(Unit) {
        var time = 0f
        while (true) {
            // Use sine function to create a smooth floating effect
            // Multiply by 4 to get ~4px movement range
            floatOffset = sin(time) * 4f
            time += 0.05f
            delay(50) // Update every 50ms for smooth animation
        }
    }
    
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                left(positionLeft.px)
                top(positionTop.px)
                padding(10.px)
                backgroundColor(Color("#333333"))
                color(Color.white)
                borderRadius(8.px)
                fontSize(14.px)
                cursor("pointer")
                
                // Use property() for CSS properties not directly available
                property("box-shadow", "0 2px 10px rgba(0, 0, 0, 0.3)")
                property("z-index", "1000")
                property("border", "1px solid #555555")
                property("transform", "translateX(${floatOffset}px)")
                property("pointer-events", "auto")
                
                // Add a small arrow on the right side
            }
            onClick { onDismiss() }
        }
    ) {
        Text(text)
        
        // Add the arrow as a separate div
        Div(
            attrs = {
                style {
                    property("position", "absolute")
                    property("right", "-10px")
                    property("top", "50%")
                    property("transform", "translateY(-50%)")
                    property("width", "0")
                    property("height", "0")
                    property("border-width", "10px 0 10px 10px")
                    property("border-style", "solid")
                    property("border-color", "transparent transparent transparent #333333")
                }
            }
        ) {}
    }
}
