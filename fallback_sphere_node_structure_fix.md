# Fallback Sphere Node Structure Fix

## Issue
The latest change broke highlighting on all other objects. The fallback sphere implementation was directly using a mesh as a node, which caused issues with the highlighting system.

## Root Cause
The highlighting system was modified to handle two different cases:
1. For fallback spheres (AbstractMesh): Apply highlighting directly to the mesh
2. For complex objects (Node): Apply highlighting to all child meshes

This dual approach broke highlighting for other objects because it created inconsistent behavior between different types of objects.

## Solution
Modified the fallback sphere implementation to use a node structure similar to complex objects:

1. Changed `createFallbackSphere` to create a TransformNode as a container and add the sphere mesh as a child
2. Updated the fallback sphere disposal code to dispose of a TransformNode instead of an AbstractMesh
3. Simplified the highlighting logic to use the same approach for all objects (get child meshes and highlight them)
4. Updated the WorldObjectHandle implementation for fallback spheres to use getChildMeshes

This ensures that all objects in the scene, whether they are complex models or fallback spheres, have a consistent node structure and are handled the same way by the highlighting system.

## Code Changes

### 1. Modified `createFallbackSphere` method:
```kotlin
private fun createFallbackSphere(name: String, position: Vector3, rotation: Vector3, scale: Vector3): Node {
    // Create a transform node that will act as our container
    val container = lib.TransformNode("container_$name", scene)
    
    // Set the container's position, rotation, and scale
    container.position = position
    container.rotation = rotation
    container.scaling = scale
    
    // Create a sphere mesh
    val sphere = MeshBuilder.CreateSphere(
        name,
        object : CreateSphereOptions {
            override val diameter = 1.0f
            override val segments = 16
        },
        scene
    )
    
    // Create a white material
    val material = PBRMaterial("material_$name", scene)
    material.albedoColor = Color3(1f, 1f, 1f)  // White color
    material.metallic = 0.0f
    material.roughness = 0.4f
    
    // Apply the material to the sphere
    sphere.material = material
    
    // Reset the sphere's position to origin since it will inherit from parent
    sphere.position = Vector3(0f, 0f, 0f)
    sphere.rotation = Vector3(0f, 0f, 0f)
    sphere.scaling = Vector3(1f, 1f, 1f)
    
    // Make the sphere pickable
    sphere.isPickable = true
    
    // Make the sphere a child of the container
    sphere.parent = container
    
    return container
}
```

### 2. Updated fallback sphere disposal:
```kotlin
// Remove the fallback sphere (now it's a TransformNode)
(fallbackSphere as lib.TransformNode).dispose()
```

### 3. Simplified highlighting logic:
```kotlin
// Get all child meshes from the node
val childMeshes = node.getChildMeshes(false)
console.log("[DEBUG_HIGHLIGHT] Found ${childMeshes.size} child meshes to highlight")

// Add each child mesh to the highlight layer
childMeshes.forEach { childMesh ->
    console.log("[DEBUG_HIGHLIGHT] Adding mesh to highlight layer: ${childMesh.name}")
    highlightLayer.addMesh(childMesh, Color3(1f, 1f, 0f), false)
}
```

### 4. Updated WorldObjectHandle implementation:
```kotlin
override fun getMeshes(): Array<AbstractMesh> {
    return fallbackSphere.getChildMeshes(false)
}
```

## Testing
To test this fix:
1. Run the `test_fallback_sphere_highlight.sh` script to create a test object with a fallback sphere
2. Navigate to http://localhost:4080/test in your browser
3. Hover over the white sphere to verify it shows a yellow highlight
4. Also verify that highlighting still works on other objects in the scene

This ensures that both fallback spheres and complex objects have consistent highlighting behavior.
