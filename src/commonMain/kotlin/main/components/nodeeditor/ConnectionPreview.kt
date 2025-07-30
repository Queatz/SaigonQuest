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
 */
@Composable
fun ConnectionPreview(
    sourceX: Float,
    sourceY: Float,
    currentX: Float,
    currentY: Float,
    isValidTarget: Boolean = false,
    thickness: Float = 2f
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
        thickness = thickness
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
            }
        }
    )
    
    // Add a visual indicator at the cursor position
    Div(
        attrs = {
            classes(NodeEditorStyles.connectionPreviewCursor)
            style {
                // Center the cursor indicator on the current mouse position
                left((currentX - 8).px)
                top((currentY - 8).px)
                
                // Dynamic styles that depend on the color variable
                backgroundColor(rgba(255, 255, 255, 0.2))
                property("border-color", color.toString())
                property("box-shadow", "0 0 12px ${color.toString()}")
                
                // Add animation effect if there's a valid target
                if (isValidTarget) {
                    property("animation", "pulse 1.5s infinite")
                }
            }
        }
    )
    
    // Add a keyframe animation for the pulse effect using the DOM API
    DisposableEffect(Unit) {
        // Check if the style element already exists
        val styleId = "pulse-animation-style"
        var styleElement = document.getElementById(styleId) as? HTMLElement
        
        // If the style element doesn't exist, create it
        if (styleElement == null) {
            styleElement = document.createElement("style") as HTMLElement
            styleElement.id = styleId
            styleElement.innerHTML = """
                @keyframes pulse {
                    0% { transform: scale(1); opacity: 1; }
                    50% { transform: scale(1.2); opacity: 0.8; }
                    100% { transform: scale(1); opacity: 1; }
                }
            """.trimIndent()
            
            // Append the style element to the document head
            document.head?.appendChild(styleElement)
        }
        
        // Cleanup function (we don't remove the style element since it might be used by other instances)
        onDispose { }
    }
}
