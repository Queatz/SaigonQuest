package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.browser.document
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.ElementScope
import org.jetbrains.compose.web.dom.Style
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.svg.SVGElement
import web.cssom.AtRules.Companion.property
import kotlin.math.abs
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
    connectionId: String? = null
) {
    // Calculate control points for the bezier curve
    // We want the curve to start and end horizontally, but with a more natural curve
    
    // Calculate horizontal and vertical distances between points
    val horizontalDistance = abs(targetX - sourceX)
    val verticalDistance = abs(targetY - sourceY)
    
    // Calculate the total distance between points (Euclidean distance)
    val totalDistance = kotlin.math.sqrt(horizontalDistance * horizontalDistance + verticalDistance * verticalDistance)
    
    // Calculate a factor for the control point distance based on the total distance
    // This creates more natural curves for both short and long connections
    val distanceFactor = kotlin.math.min(0.5f, totalDistance / 500f + 0.2f)
    
    // Calculate the control point distances
    // For short connections, this will be shorter, for long connections, it will be longer
    // but never more than 50% of the total distance
    val controlDistance = totalDistance * distanceFactor
    
    // Handle special cases for vertical or horizontal alignments
    val controlPoint1X: Float
    val controlPoint1Y: Float
    val controlPoint2X: Float
    val controlPoint2Y: Float
    
    // Check if points are vertically aligned (or nearly so)
    if (horizontalDistance < 10f) {
        // Add some horizontal curve for vertical connections
        val sideOffset = 50f
        val direction = if (targetY > sourceY) 1 else -1 // Determine if target is below or above source
        
        // Create an S-curve for vertical connections
        controlPoint1X = sourceX + sideOffset * direction
        controlPoint1Y = sourceY + verticalDistance * 0.25f
        controlPoint2X = targetX + sideOffset * direction
        controlPoint2Y = targetY - verticalDistance * 0.25f
    } 
    // Check if points are horizontally aligned (or nearly so)
    else if (verticalDistance < 10f) {
        // Add some vertical curve for horizontal connections
        val vertOffset = 50f
        val direction = if (targetX > sourceX) 1 else -1 // Determine if target is to the right or left of source
        
        // Create a slight arc for horizontal connections
        controlPoint1X = sourceX + horizontalDistance * 0.25f
        controlPoint1Y = sourceY - vertOffset * direction
        controlPoint2X = targetX - horizontalDistance * 0.25f
        controlPoint2Y = targetY - vertOffset * direction
    }
    // Normal case: create horizontal tangents with adaptive control point distances
    else {
        controlPoint1X = sourceX + controlDistance
        controlPoint1Y = sourceY
        controlPoint2X = targetX - controlDistance
        controlPoint2Y = targetY
    }
    
    // Create the SVG path for the bezier curve
    val path = "M $sourceX $sourceY " +
               "C $controlPoint1X $controlPoint1Y, $controlPoint2X $controlPoint2Y, $targetX $targetY"
    
    // Determine the color and thickness based on selection state
    val actualColor = if (isSelected) rgba(100, 200, 255, 0.9) else color
    val actualThickness = if (isSelected) max(thickness, 4f) else thickness
    
    // Generate a unique ID for the SVG container
    val svgContainerId = remember { "svg-container-${Random.nextInt(1000000)}" }
    
    // Render the connection using an SVG path
    Div(
        attrs = {
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
                id(svgContainerId)
            }
        )
        
        // Use DisposableEffect to create and manage the SVG element
        DisposableEffect(path, actualColor, actualThickness, svgContainerId) {
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

