package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.dom.Div

/**
 * Renders a preview connection while dragging from a port.
 * 
 * @param sourceX X coordinate of the source port
 * @param sourceY Y coordinate of the source port
 * @param currentX Current X coordinate of the mouse
 * @param currentY Current Y coordinate of the mouse
 * @param isValidTarget Whether there is a valid target port under the cursor
 * @param color The base color for the connection preview (usually the color of the source node)
 * @param thickness Thickness of the preview connection
 * @param isInConnectionLayer Whether this preview is being rendered in the separate connection layer
 */
@Composable
fun ConnectionPreview(
    sourceX: Float,
    sourceY: Float,
    currentX: Float,
    currentY: Float,
    isValidTarget: Boolean = false,
    color: org.jetbrains.compose.web.css.CSSColorValue = rgba(100, 200, 255, 0.8),
    thickness: Float = 2f
) {
    // Determine the actual color based on whether there's a valid target
    val actualColor = if (isValidTarget) {
        rgba(0, 255, 100, 0.8)
    } else {
        color
    }
    
    // Use the same bezier curve as the regular connection
    BezierConnection(
        sourceX = sourceX,
        sourceY = sourceY,
        targetX = currentX,
        targetY = currentY,
        color = actualColor,
        thickness = thickness,
    )
    
    // Add a visual indicator at the source port
    // The dot should be centered on the port position
    Div(
        attrs = {
            classes(NodeEditorStyles.connectionSourceIndicator)
            style {
                // Center the dot on the port position
                left((sourceX - 6).px)
                top((sourceY - 6).px)
                backgroundColor(actualColor)
                property("box-shadow", "0 0 8px $actualColor")
            }
        }
    )
    
    // Add a visual indicator at the cursor position
    Div(
        attrs = {
            // Apply the base cursor style
            classes(NodeEditorStyles.connectionPreviewCursor)
            
            // Apply the pulse animation class if there's a valid target
            if (isValidTarget) {
                classes(NodeEditorStyles.connectionPreviewPulse)
            }
            
            style {
                // Center the cursor indicator on the current mouse position
                left((currentX - 8).px)
                top((currentY - 8).px)
                
                // Dynamic styles that depend on the color variable
                backgroundColor(rgba(255, 255, 255, 0.2))
                property("border-color", actualColor.toString())
                property("box-shadow", "0 0 12px $actualColor")
            }
        }
    )
}
