# Fallback Sphere Highlighting Fix

## Issue
The fallback sphere (used when objects don't have a file and collection specified) was not showing the highlight effect when hovered over.

## Root Cause
The highlighting system in `SceneObjectManager` was designed to work with complex 3D models that have child meshes. It was using `node.getChildMeshes(false)` to get all child meshes and apply the highlight effect to each one.

However, the fallback sphere is a simple mesh with no children. When the highlighting code tried to get child meshes from the fallback sphere, it found none, resulting in no highlighting effect.

## Solution
Modified both the `highlightObject` and `unhighlightObject` methods in `SceneObjectManager.kt` to handle two cases:

1. For regular 3D models: Continue using the existing approach of highlighting all child meshes
2. For fallback spheres: Directly apply the highlight to the mesh itself

The key change was adding a type check to determine if the node is an `AbstractMesh` (the fallback sphere case) and handle it appropriately.

## Code Changes

### In `highlightObject` method:
```kotlin
// Check if the node is a mesh itself (fallback sphere case)
if (node is AbstractMesh) {
    console.log("[DEBUG_HIGHLIGHT] Node is a mesh (fallback sphere): ${node.name}")
    highlightLayer.addMesh(node, Color3(1f, 1f, 0f), false)
} else {
    // Get all child meshes from the node (original code for 3D models)
    val childMeshes = node.getChildMeshes(false)
    // ... rest of the original code
}
```

### In `unhighlightObject` method:
```kotlin
// Check if the node is a mesh itself (fallback sphere case)
if (node is AbstractMesh) {
    console.log("[DEBUG_HIGHLIGHT] Node is a mesh (fallback sphere): ${node.name}")
    highlightLayer.removeMesh(node)
} else {
    // Get all child meshes from the node (original code for 3D models)
    val childMeshes = node.getChildMeshes(false)
    // ... rest of the original code
}
```

## Testing
To test this fix:
1. Create an object without a file and collection specified
2. Hover over the white fallback sphere in the 3D scene
3. Verify that the sphere shows a yellow highlight

A test script (`test_fallback_sphere_highlight.sh`) has been provided to help with testing.
