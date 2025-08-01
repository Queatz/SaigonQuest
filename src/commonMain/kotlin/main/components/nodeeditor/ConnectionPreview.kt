package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLElement

/**
 * Renders a preview connection while dragging from a port.
 * 
 * @param sourceX X coordinate of the source port
 * @param sourceY Y coordinate of the source port
 * @param currentX Current X coordinate of the mouse
 * @param currentY Current Y coordinate of the mouse
 * @param isValidTarget Whether there is a valid target port under the cursor
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
    thickness: Float = 2f,
    isInConnectionLayer: Boolean = false
) {
    // Determine color based on whether there's a valid target
    val color = if (isValidTarget) rgba(0, 255, 100, 0.8) else rgba(100, 200, 255, 0.8)
    
    // Use the same bezier curve as the regular connection
    BezierConnection(
        sourceX = sourceX,
        sourceY = sourceY,
        targetX = currentX,
        targetY = currentY,
        color = color,
        thickness = thickness,
        isInConnectionLayer = isInConnectionLayer
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
                backgroundColor(color)
                
                // If in connection layer, make sure it's visible above the canvas
                if (isInConnectionLayer) {
                    property("z-index", "3")
                    property("pointer-events", "none") // Don't interfere with mouse events
                }
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
                property("border-color", color.toString())
                property("box-shadow", "0 0 12px ${color.toString()}")
                
                // If in connection layer, make sure it's visible above the canvas
                if (isInConnectionLayer) {
                    property("z-index", "3")
                    property("pointer-events", "none") // Don't interfere with mouse events
                }
            }
        }
    )
}
