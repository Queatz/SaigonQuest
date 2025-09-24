# SVG Rendering Improvements

This document explains the changes made to improve SVG rendering in the Saigon.Quest project.

## Issue

The original implementation of SVG rendering in the `ConnectionRenderer.kt` file used `attr("innerHTML", ...)` to inject SVG markup directly into the DOM. However, this approach was not working correctly, as innerHTML does not properly set the inner HTML content in some cases.

## Solution

The solution was to replace the innerHTML-based approach with a proper DOM API-based implementation that creates SVG elements programmatically.

### 1. BezierConnection Function

**Original Implementation:**
```kotlin
// Use inline SVG to draw the bezier curve
Div(
    attrs = {
        classes(NodeEditorStyles.connectionSvgContainer)
        // Insert SVG element directly
        attr("innerHTML", """
            <svg width="100%" height="100%" style="position: absolute; top: 0; left: 0; overflow: visible;">
                <path d="$path" stroke="${actualColor}" stroke-width="$actualThickness" fill="none" />
            </svg>
        """.trimIndent())
    }
)
```

**New Implementation:**
```kotlin
// Generate a unique ID for the SVG container
val svgContainerId = remember { "svg-container-${Random.nextInt(1000000)}" }

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
```

### 2. Keyframe Animation in ConnectionPreview

**Original Implementation:**
```kotlin
// Add a keyframe animation for the pulse effect
Div(
    attrs = {
        attr("innerHTML", """
            <style>
                @keyframes pulse {
                    0% { transform: scale(1); opacity: 1; }
                    50% { transform: scale(1.2); opacity: 0.8; }
                    100% { transform: scale(1); opacity: 1; }
                }
            </style>
        """.trimIndent())
    }
)
```

**New Implementation:**
```kotlin
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
```

## Benefits of the Changes

1. **Proper SVG Rendering**: The SVG elements are now created using the proper DOM API, ensuring they are rendered correctly.

2. **Better Separation of Concerns**: The new implementation separates the creation of the SVG elements from the Compose UI components, making the code more maintainable.

3. **Improved Performance**: The keyframe animation is now added to the document head only once, rather than being recreated for each instance of the ConnectionPreview component.

4. **Proper Cleanup**: The DisposableEffect ensures that resources are properly cleaned up when components are disposed, preventing memory leaks.

5. **Type Safety**: The new implementation uses strongly-typed DOM API calls instead of string manipulation, reducing the risk of errors.

## Implementation Notes

- The changes were made with minimal modifications to the existing code structure.
- The bezier curve calculation logic remains unchanged, ensuring that the visual appearance of the connections is preserved.
- The keyframe animation for the pulse effect is now added to the document head, which is the correct place for style elements.
- A unique ID is generated for each SVG container to ensure that multiple connections can be rendered correctly.
