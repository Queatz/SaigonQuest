package main.components.nodeeditor

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Utility class for calculating bezier curves for connections.
 * This class extracts the bezier curve calculation logic from the BezierConnection component.
 */
object BezierCurveUtils {
    /**
     * Calculates the control points for a bezier curve between two points.
     * Handles special cases for vertical and horizontal alignments.
     *
     * @param sourceX X coordinate of the source point
     * @param sourceY Y coordinate of the source point
     * @param targetX X coordinate of the target point
     * @param targetY Y coordinate of the target point
     * @return Array of control points [controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y]
     */
    fun calculateControlPoints(
        sourceX: Float,
        sourceY: Float,
        targetX: Float,
        targetY: Float
    ): FloatArray {
        // Calculate horizontal and vertical distances between points
        val horizontalDistance = abs(targetX - sourceX)
        val verticalDistance = abs(targetY - sourceY)
        
        // Calculate the total distance between points (Euclidean distance)
        val totalDistance = sqrt(horizontalDistance * horizontalDistance + verticalDistance * verticalDistance)
        
        // Calculate a factor for the control point distance based on the total distance
        // This creates more natural curves for both short and long connections
        val distanceFactor = min(0.5f, totalDistance / 500f + 0.2f)
        
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
        if (false && horizontalDistance < 10f) {
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
        else if (false && verticalDistance < 10f) {
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
        
        return floatArrayOf(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y)
    }
    
    /**
     * Creates an SVG path string for a bezier curve using the calculated control points.
     *
     * @param sourceX X coordinate of the source point
     * @param sourceY Y coordinate of the source point
     * @param targetX X coordinate of the target point
     * @param targetY Y coordinate of the target point
     * @return SVG path string for the bezier curve
     */
    fun createPathString(
        sourceX: Float,
        sourceY: Float,
        targetX: Float,
        targetY: Float
    ): String {
        val controlPoints = calculateControlPoints(sourceX, sourceY, targetX, targetY)
        val controlPoint1X = controlPoints[0]
        val controlPoint1Y = controlPoints[1]
        val controlPoint2X = controlPoints[2]
        val controlPoint2Y = controlPoints[3]
        
        return "M $sourceX $sourceY " +
               "C $controlPoint1X $controlPoint1Y, $controlPoint2X $controlPoint2Y, $targetX $targetY"
    }
}
