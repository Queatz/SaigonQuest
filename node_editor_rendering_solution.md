# Node Editor Rendering Solution

## Problem Overview

The node editor was experiencing issues with connection rendering during zooming and panning operations:

1. **Misaligned Connections**: Connections between nodes would detach from their ports when zooming or panning.
2. **Non-Smooth Zooming**: Zooming was jumpy and not centered on the cursor position.
3. **Coordinate System Mismatch**: The coordinate transformations didn't properly account for the CSS transform applied to the canvas.

## Root Causes

After thorough analysis, several fundamental issues were identified:

1. **CSS Transform Order**: The transform was applying scale first, then translation (`scale(${zoomLevel}) translate(${panX}px, ${panY}px)`), which made coordinate transformations more complex and error-prone.

2. **Rendering Context**: Connections were rendered inside the transformed canvas, making them subject to the same CSS transform, which caused misalignment issues.

3. **Coordinate Transformations**: The functions for converting between screen and canvas coordinates didn't properly account for the CSS transform, especially for DOM element positions.

4. **Zooming Implementation**: The zoom function used a delta-based approach instead of a multiplicative factor, resulting in non-smooth zooming.

## Comprehensive Solution

A comprehensive solution was implemented to address all these issues:

### 1. Separate Connection Layer

A separate connection layer was created that sits above the canvas but isn't affected by its transform:

```kotlin
// Connection layer - positioned above the canvas but not affected by its transform
Div(
    attrs = {
        classes(NodeEditorStyles.connectionLayer)
        id("connectionLayer")
    }
) {
    // Render connections using ConnectionHandler
    ConnectionHandler(controller, isInConnectionLayer = true)
}
```

New styles were added to support this layer:

```kotlin
// Connection layer styles
val connectionLayer by style {
    position(Position.Absolute)
    left(0.px)
    top(0.px)
    width(100.percent)
    height(100.percent)
    property("pointer-events", "none") // Let events pass through to canvas
    property("z-index", "2") // Above canvas but below UI elements
}

val connectionLayerSvg by style {
    width(100.percent)
    height(100.percent)
    position(Position.Absolute)
    left(0.px)
    top(0.px)
    property("overflow", "visible")
}
```

### 2. Fixed CSS Transform Order

The CSS transform order was changed to apply translation first, then scaling:

```kotlin
// Apply zoom and pan transformations
// Important: Apply translate first, then scale for more intuitive coordinate transformations
style {
    property("transform", "translate(${controller.panX}px, ${controller.panY}px) scale(${controller.zoomLevel})")
}
```

This makes coordinate transformations more intuitive and easier to work with.

### 3. Improved Coordinate Transformations

The coordinate transformation functions were updated to properly handle the new transform order:

```kotlin
/**
 * Converts screen coordinates to canvas coordinates.
 * With the new transform order (translate first, then scale), the formula is:
 * canvasX = (screenX - rect.left) / zoomLevel - panX
 * canvasY = (screenY - rect.top) / zoomLevel - panY
 */
fun screenToCanvasCoordinates(screenX: Float, screenY: Float, isElementPosition: Boolean = false): Pair<Float, Float> {
    // Implementation details...
}

/**
 * Converts canvas coordinates to screen coordinates.
 * With the new transform order (translate first, then scale), the formula is:
 * screenX = (canvasX + panX) * zoomLevel + rect.left
 * screenY = (canvasY + panY) * zoomLevel + rect.top
 */
fun canvasToScreenCoordinates(canvasX: Float, canvasY: Float, forElementPositioning: Boolean = false): Pair<Float, Float> {
    // Implementation details...
}
```

Special handling was added for DOM element positions to avoid double-transformation.

### 4. Smoother Zooming

The zoom function was updated to use a multiplicative factor instead of a delta value:

```kotlin
/**
 * Handles zooming of the canvas.
 *
 * @param zoomFactor The zoom factor (> 1.0 for zoom in, < 1.0 for zoom out)
 * @param mouseX The x position of the mouse in screen coordinates
 * @param mouseY The y position of the mouse in screen coordinates
 */
fun zoom(zoomFactor: Float, mouseX: Float, mouseY: Float) {
    // Calculate new zoom level with constraints
    val newZoom = (zoomLevel * zoomFactor).coerceIn(0.1f, 3.0f)
    
    // Implementation details...
}
```

The zoom function was also updated to properly center the zoom on the cursor position.

### 5. Updated Connection Rendering

The `BezierConnection` and `ConnectionPreview` components were updated to handle the connection layer:

```kotlin
/**
 * Renders a connection between two ports using a bezier curve.
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
    // Implementation details...
}

/**
 * Renders a preview connection while dragging from a port.
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
    // Implementation details...
}
```

## Benefits of the Solution

1. **Stable Connections**: Connections now stay properly attached to their ports during zooming and panning operations.

2. **Smooth Zooming**: Zooming is now smooth and centered on the cursor position, providing a more natural user experience.

3. **Improved Performance**: The separate connection layer reduces the need for complex coordinate transformations and DOM manipulations.

4. **Future-Proof Architecture**: The solution is designed to be flexible and extensible, making it easier to add new features or make changes in the future.

## Future Improvements

1. **SVG Rendering**: Consider using Compose for Web's SVG support for more reactive and declarative connection rendering.

2. **Matrix-Based Transformations**: Implement matrix-based coordinate transformations for even more accurate positioning.

3. **Connection Styling**: Add more customization options for connection styling, such as different line styles, arrow heads, etc.

4. **Performance Optimization**: Optimize the rendering of connections for large graphs by implementing techniques like culling or level-of-detail rendering.

5. **Touch Support**: Add support for touch interactions for mobile devices.

## Conclusion

The implemented solution addresses all the identified issues with node connection rendering during zooming and panning operations. By creating a separate connection layer, fixing the CSS transform order, improving coordinate transformations, and enhancing the zooming implementation, the node editor now provides a stable and smooth user experience.
