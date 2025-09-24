# Node Editor Positioning Fixes

This document explains the changes made to fix positioning issues in the node editor component of the Saigon.Quest project.

## Issues Fixed

### 1. NodeEditorCanvas Position Issue

**Problem:** When making nodeEditorCanvas position absolute, it needed to be inside a position relative div.

**Root Cause:** The nodeEditorCanvas had position: absolute but wasn't contained within a position: relative parent element. In CSS, absolute positioning is relative to the nearest positioned ancestor (an element with position other than static). Without a positioned ancestor, absolute elements are positioned relative to the initial containing block (usually the viewport), which can cause unexpected layout issues.

**Solution:**
1. Created a new nodeEditorCanvasWrapper style in NodeEditorStyles.kt:
```kotlin
// Canvas wrapper styles
val nodeEditorCanvasWrapper by style {
    property("flex-grow", "1")
    backgroundColor(rgba(40, 40, 40, 0.8))
    padding(16.px)
    position(Position.Relative) // Important: This makes absolute positioning of children work
    property("overflow", "auto")
}
```

2. Modified the LogicEditorDialog.kt to wrap the nodeEditorCanvas in a div with the nodeEditorCanvasWrapper style:
```kotlin
// Node editor canvas wrapper
Div(
    attrs = {
        classes(NodeEditorStyles.nodeEditorCanvasWrapper)
        // Event handlers...
    }
) {
    // Node editor canvas
    Div(
        attrs = {
            classes(NodeEditorStyles.nodeEditorCanvas)
            classes(NodeEditorStyles.nodeEditorCanvasZoomed)
            // Canvas-specific event handlers...
        }
    ) {
        // Canvas content...
    }
}
```

3. Reorganized event handlers:
   - Moved keyboard events (delete, undo, redo) to the wrapper
   - Moved drop events to the wrapper
   - Kept mouse movement and connection-related events on the canvas

### 2. Connector SVG Positioning Issue

**Problem:** The connector SVG was not rendering at the right position.

**Root Cause:** The SVG element inside the connection line div didn't have explicit positioning coordinates, which caused it to be positioned incorrectly relative to its container.

**Solution:**
Modified the SVG element in ConnectionRenderer.kt to include explicit top and left positioning:

```kotlin
attr("innerHTML", """
    <svg width="100%" height="100%" style="position: absolute; top: 0; left: 0; overflow: visible;">
        <path d="$path" stroke="${actualColor}" stroke-width="$actualThickness" fill="none" />
    </svg>
""".trimIndent())
```

By adding `top: 0; left: 0;` to the SVG style, we ensure it's properly positioned at the top-left corner of its container.

## Benefits of the Changes

1. **Proper Containment:** The nodeEditorCanvas is now properly contained within a positioned parent, ensuring that its absolute positioning works as expected.

2. **Correct SVG Rendering:** The connector SVG now renders at the correct position, making the connections between nodes display properly.

3. **Better Structure:** The event handling is now better organized, with global events on the wrapper and canvas-specific events on the canvas itself.

4. **Improved Maintainability:** The code is now more maintainable with a clearer structure and proper CSS positioning.

## Implementation Notes

- The changes were made with minimal modifications to the existing code structure.
- The event handlers were carefully reorganized to maintain the same functionality.
- The SVG positioning fix is a simple but effective solution that ensures proper rendering without changing the overall connection rendering logic.
