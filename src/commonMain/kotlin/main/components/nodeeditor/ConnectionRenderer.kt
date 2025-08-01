package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.browser.document
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import web.cssom.AtRules.Companion.property
import kotlin.math.max
import kotlin.random.Random

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
    isInConnectionLayer: Boolean = false
) {
    // Create the SVG path for the bezier curve using the utility function
    val path = BezierCurveUtils.createPathString(sourceX, sourceY, targetX, targetY)
    
    // Determine the color and thickness based on selection state
    val actualColor = if (isSelected) rgba(100, 200, 255, 0.9) else color
    val actualThickness = if (isSelected) max(thickness, 4f) else thickness
    
    // Generate a unique ID for the SVG container
    val svgContainerId = remember { "svg-container-${Random.nextInt(1000000)}" }
    
    // Render the connection using an SVG path
    Div(
        attrs = {
            // Apply different styles based on whether we're in the connection layer
            if (isInConnectionLayer) {
                // For the connection layer, use absolute positioning relative to the wrapper
                style {
                    position(Position.Absolute)
                    left(0.px)
                    top(0.px)
                    width(0.px)
                    height(0.px)
                    property("pointer-events", if (onClick != null) "auto" else "none")
                    property("z-index", if (isSelected) "3" else "2")
                }
            } else {
                // For the canvas, use the standard connection styles
                classes(NodeEditorStyles.connectionLine)
                if (isSelected) {
                    classes(NodeEditorStyles.connectionSelected)
                }
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
                id(svgContainerId)
                
                // If in connection layer, make the container cover the entire wrapper
                if (isInConnectionLayer) {
                    style {
                        position(Position.Fixed)
                        left(0.px)
                        top(0.px)
                        property("width", "100%")
                        property("height", "100%")
                        property("pointer-events", "none")
                    }
                }
            }
        )
        
        // Use DisposableEffect to create and manage the SVG element
        DisposableEffect(path, actualColor, actualThickness, svgContainerId, isInConnectionLayer) {
            // Get the container element
            val container = document.getElementById(svgContainerId)
            
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
                
                // Add additional attributes for the connection layer
                if (isInConnectionLayer) {
                    // Make the path clickable if onClick is provided
                    if (onClick != null) {
                        pathElement.setAttribute("style", "cursor: pointer; pointer-events: stroke;")
                    } else {
                        pathElement.setAttribute("style", "pointer-events: none;")
                    }
                    
                    // Add a class for styling
                    if (isSelected) {
                        pathElement.setAttribute("class", "connection-path-selected")
                    } else {
                        pathElement.setAttribute("class", "connection-path-normal")
                    }
                }
                
                // Append path to SVG
                svg.appendChild(pathElement)
                
                // Append SVG to container
                container.appendChild(svg)
            }
            
            // Cleanup function
            onDispose {
                // Remove SVG element when component is disposed
                val container = document.getElementById(svgContainerId)
                container?.innerHTML = ""
            }
        }
    }
}

