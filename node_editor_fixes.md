# Node Editor Fixes

This document explains the changes made to fix several issues with the node editor in the Saigon.Quest project.

## Issues Fixed

### 1. Active Drag Indicators Alignment

**Problem:** When dragging from a socket to connect nodes, the active drag indicators were not properly aligned to the sockets.

**Root Cause:** The connection preview was rendered inside the transformed canvas but used screen coordinates without accounting for the zoom and pan transformations.

**Solution:**
- Added a specific ID "nodeEditorCanvas" to the canvas element for easy selection
- Created a helper function `screenToCanvasCoordinates` to convert screen coordinates to canvas coordinates
- Updated `handlePortDragStart` and `handlePortDragMove` functions to use this conversion
- This ensures that the connection preview aligns properly with the transformed canvas

```kotlin
// Helper function to convert screen coordinates to canvas coordinates
val screenToCanvasCoordinates = { screenX: Float, screenY: Float ->
    // Get the canvas element's bounding rect
    val canvasElement = getDocument().getElementById("nodeEditorCanvas")
    val rect = canvasElement?.getBoundingClientRectSafe()
    
    if (rect != null) {
        // Calculate the position relative to the canvas
        val relativeX = screenX - rect.left.toFloat()
        val relativeY = screenY - rect.top.toFloat()
        
        // Apply inverse of the zoom and pan transformations
        val canvasX = relativeX / zoomLevel - panX
        val canvasY = relativeY / zoomLevel - panY
        
        Pair(canvasX, canvasY)
    } else {
        // Fallback if we can't get the canvas rect
        Pair(screenX, screenY)
    }
}
```

### 2. NodeEditorCanvasZoomed Element Coverage

**Problem:** The `nodeEditorCanvasZoomed` element was not correctly covering the entire middle section of the canvas, and it was scrollable, which was strange because the middle section was already scrollable.

**Root Cause:** The `nodeEditorCanvasZoomed` style didn't have explicit width, height, or positioning properties, and the parent `nodeEditorCanvas` had `overflow: auto` which caused nested scrolling.

**Solution:**
- Updated the `nodeEditorCanvasZoomed` style to ensure it covers the entire middle section:
  - Added `width(100.percent)` and `height(100.percent)` to make it cover the entire parent
  - Added `position(Position.Absolute)` to position it correctly
  - Added `property("overflow", "visible")` to prevent scrolling in the transformed container

```kotlin
// Canvas with zoom styles
val nodeEditorCanvasZoomed by style {
    property("transform-origin", "0 0")
    property("transition", "transform 0.2s ease-out")
    width(100.percent)
    height(100.percent)
    position(Position.Absolute)
    property("overflow", "visible") // Prevent scrolling in the transformed container
}
```

### 3. Grid Background Issue

**Problem:** The grid background was strange and needed to be removed or fixed to cover the middle section properly.

**Solution:**
- Removed the grid background from the `nodeEditorCanvas` style as requested:

```kotlin
// Canvas styles
val nodeEditorCanvas by style {
    property("flex-grow", "1")
    backgroundColor(rgba(40, 40, 40, 0.8))
    padding(16.px)
    property("overflow", "auto")
    position(Position.Relative)
    // Grid background removed as requested
}
```

## Benefits of the Changes

1. **Improved Connection Dragging:** The active drag indicators now properly align with the sockets, making it easier to create connections between nodes.

2. **Better Canvas Structure:** The canvas now has a cleaner structure with proper containment and no nested scrolling, which improves the user experience.

3. **Cleaner Visual Appearance:** Removing the grid background simplifies the visual appearance of the canvas, making it easier to focus on the nodes and connections.

## Implementation Notes

- The changes were made with minimal modifications to the existing code structure.
- The coordinate conversion logic accounts for both zoom level and pan position to ensure accurate positioning.
- For DOM operations like `getElementFromPoint`, we still use screen coordinates as these methods work with the actual screen positions.
