# BezierConnection Improvements

## Issue
The original implementation of the `BezierConnection` function in `ConnectionRenderer.kt` did not correctly draw the bezier curve connections between nodes. The main issues were:

1. The control points were calculated using a fixed minimum distance of 100 pixels, which could cause exaggerated curves when nodes were close together.
2. The control points were always positioned at exactly half the distance from the source and target points, which didn't create the most visually pleasing curves in all cases.
3. Special cases like vertical or horizontal alignments weren't handled properly, resulting in awkward-looking connections.

## Solution
I've implemented an improved algorithm for calculating the control points in the bezier curve:

### 1. Adaptive Control Point Distance
Instead of using a fixed distance or a simple proportion, the new implementation calculates a distance factor based on the total Euclidean distance between the points:

```kotlin
// Calculate the total distance between points (Euclidean distance)
val totalDistance = kotlin.math.sqrt(horizontalDistance * horizontalDistance + verticalDistance * verticalDistance)

// Calculate a factor for the control point distance based on the total distance
val distanceFactor = kotlin.math.min(0.5f, totalDistance / 500f + 0.2f)

// Calculate the control point distances
val controlDistance = totalDistance * distanceFactor
```

This creates more natural curves for both short and long connections:
- For short connections, the control points are closer to the endpoints (20-30% of the distance)
- For long connections, the control points are farther from the endpoints (up to 50% of the distance)
- The factor is capped at 0.5 to prevent extreme curves

### 2. Special Case Handling
The new implementation adds special case handling for vertical and horizontal alignments:

#### Vertical Connections
When the source and target points are vertically aligned (or nearly so):
```kotlin
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
```
This creates an S-curve for vertical connections, which is more visually pleasing than a straight line.

#### Horizontal Connections
When the source and target points are horizontally aligned (or nearly so):
```kotlin
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
```
This creates a slight arc for horizontal connections, which is more visually pleasing than a straight line.

### 3. Direction-Aware Control Points
The special case handling is direction-aware, meaning it adjusts the curve based on whether the target is above/below or left/right of the source. This ensures that the curve always flows in a natural direction.

## Benefits of the New Implementation

1. **More Natural Curves**: The adaptive control point distance creates more natural curves for connections of all lengths.

2. **Better Special Case Handling**: Vertical and horizontal connections now have visually pleasing curves instead of straight lines or awkward bends.

3. **Direction-Aware**: The curves adjust based on the relative positions of the source and target, ensuring a natural flow.

4. **Improved Visual Hierarchy**: The more natural curves help users better understand the relationships between nodes in the editor.

5. **Consistent with Design Best Practices**: The implementation follows best practices for bezier curve control point placement in node-based editors and flow diagrams.

## Conclusion
The new implementation of the `BezierConnection` function provides a significant improvement in the visual appearance of connections in the node editor. The curves are more natural, handle special cases better, and follow best practices for bezier curve control point placement.
