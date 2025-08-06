package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.dom.clear
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLDivElement
import kotlin.math.max

/**
 * Renders a connection between two ports using a bezier curve.
 * 
 * @param sourceX X coordinate of the source port
 * @param sourceY Y coordinate of the source port
 * @param targetX X coordinate of the target port
 * @param targetY Y coordinate of the target port
 * @param color Color of the connection line
 * @param thickness Thickness of the connection line
 * @param isSelected Whether this connection is currently selected
 * @param onClick Callback when the connection is clicked
 * @param connectionId Optional identifier for the connection (for debugging)
 * @param isInConnectionLayer Whether this connection is being rendered in the separate connection layer
 */
@Composable
fun BezierConnection(
    sourceX: Float,
    sourceY: Float,
    targetX: Float,
    targetY: Float,
    color: CSSColorValue = rgba(255, 255, 255, 0.7),
    thickness: Float = 3f,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    connectionId: String? = null,
) {
    // Create the SVG path for the bezier curve using the utility function
    val path = remember(
        sourceX,
        sourceY,
        targetX,
        targetY
    ) { BezierCurveUtils.createPathString(sourceX, sourceY, targetX, targetY) }
    
    // Determine the color and thickness based on selection state
    val actualColor = if (isSelected) rgba(100, 200, 255, 0.9) else color
    val actualThickness = if (isSelected) max(thickness, 4f) else thickness
    
    // Generate a unique ID for the SVG container
    var containerRef by remember { mutableStateOf<HTMLDivElement?>(null) }
    
    // Render the connection using an SVG path
    Div(
        attrs = {
            // For the canvas, use the standard connection styles
            classes(NodeEditorStyles.connectionLine)
            if (isSelected) {
                classes(NodeEditorStyles.connectionSelected)
            }

            // Add click handler if provided
            if (onClick != null) {
                onClick { 
                    it.stopPropagation() // Prevent canvas click
                    onClick() 
                }
            }
            
            // Add data attribute for debugging
            if (connectionId != null) {
                attr("data-connection-id", connectionId)
            }
        }
    ) {
        // Container for the SVG
        Div(
            attrs = {
                classes(NodeEditorStyles.connectionSvgContainer)


                ref {
                    containerRef = it

                    onDispose {  }
                }
            }
        )
        
        // Use DisposableEffect to create and manage the SVG element
        DisposableEffect(path, actualColor, actualThickness, containerRef) {
            // Get the container element
            val container = containerRef
            
            if (container != null) {
                // Create SVG element
                val svgNS = "http://www.w3.org/2000/svg"
                val svg = document.createElementNS(svgNS, "svg")
                svg.setAttribute("width", "100%")
                svg.setAttribute("height", "100%")
                svg.setAttribute("style", "position: absolute; top: 0; left: 0; overflow: visible;")
                
                // Create path element
                val pathElement = document.createElementNS(svgNS, "path")
                pathElement.setAttribute("d", path)
                pathElement.setAttribute("stroke", actualColor.toString())
                pathElement.setAttribute("stroke-width", actualThickness.toString())
                pathElement.setAttribute("fill", "none")

                // Append path to SVG
                svg.appendChild(pathElement)
                
                // Append SVG to container
                container.appendChild(svg)
            }
            
            // Cleanup function
            onDispose {
                containerRef?.clear()
            }
        }
    }
}
