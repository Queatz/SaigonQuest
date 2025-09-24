# Comprehensive Depth Texture Fix

## Issue
The previous attempt to fix the depth texture by ensuring all meshes were not transparent did not work. Further investigation revealed multiple issues affecting the depth texture functionality:

1. The `useDepthTexture` property was not explicitly enabled in the DefaultRenderingPipeline
2. The dynamic depth of field focus distance calculation was being overwritten with a hardcoded value
3. The camera target was completely transparent (alpha=0), which might have been interfering with depth texture rendering

## Solution
A comprehensive fix was implemented with the following changes:

### 1. Enabled Depth Texture in Rendering Pipeline
In `CameraController.kt`, explicitly enabled the depth texture in the DefaultRenderingPipeline:

```kotlin
pipeline.apply {
    // Enable depth texture for proper depth of field effect
    useDepthTexture = true
    
    // Rest of pipeline configuration...
}
```

### 2. Fixed Depth of Field Focus Calculation
In `CameraController.kt`, fixed the `updateDepthOfFieldFocus()` method to use the dynamic calculation instead of overwriting it with a hardcoded value:

```kotlin
private fun updateDepthOfFieldFocus() {
    if (pipeline.depthOfFieldEnabled && cameraTarget != null) {
        // Calculate distance between camera and camera target
        val distanceToTarget = Vector3.Distance(camera.position, cameraTarget!!.position)
        // Update DOF focus distance with the actual distance to target
        pipeline.depthOfField.focusDistance = distanceToTarget
        
        println("[DEBUG] Updated DOF focus distance to ${distanceToTarget}")
    }
}
```

### 3. Improved Camera Target Material Configuration
In `CameraController.kt`, modified the `createCameraTarget()` method to ensure the camera target material is configured correctly for depth texture rendering while still remaining visually invisible:

```kotlin
private fun createCameraTarget() {
    // Create a separate camera target (invisible)
    cameraTarget = MeshBuilder.CreateSphere("cameraTarget",
        object {
            val diameter = 0.1f
        }, scene)
    
    // Make the camera target invisible but ensure it works with depth texture
    val invisibleMaterial = StandardMaterial("invisibleMaterial", scene)
    
    // Configure material for proper depth texture rendering
    invisibleMaterial.alpha = 0.01f  // Nearly invisible but not completely transparent
    invisibleMaterial.disableDepthWrite = false  // Enable depth writing
    invisibleMaterial.needDepthPrePass = false  // No need for depth pre-pass
    
    // Apply the material to the camera target
    cameraTarget?.material = invisibleMaterial
    
    println("[DEBUG] Created camera target with depth-texture-friendly invisible material")
}
```

## Technical Details

### Depth Textures in BabylonJS
Depth textures are special textures that store the distance from the camera to each pixel in the scene. They are used for various effects like:
- Depth of field (DOF)
- Screen Space Ambient Occlusion (SSAO)
- Shadows
- Post-processing effects

For depth textures to work correctly, several conditions must be met:
1. The `useDepthTexture` property must be enabled in the rendering pipeline
2. Materials must be configured to write to the depth buffer
3. Objects must have appropriate transparency settings

### Material Properties for Depth Textures
The following material properties affect depth texture rendering:
- `alpha`: Controls transparency (0=transparent, 1=opaque)
- `disableDepthWrite`: When false, ensures the material writes to the depth buffer
- `needDepthPrePass`: When true, renders the object in a separate pass to generate depth information

### Dynamic Depth of Field
For the depth of field effect to work correctly, the focus distance must be updated dynamically based on the distance between the camera and the target. This ensures that the object the camera is looking at remains in focus while objects at different distances are blurred.

## Related Files
- `/home/jacobferrero/IdeaProjects/Saigon.Quest/src/commonMain/kotlin/main/components/camera/CameraController.kt`
- `/home/jacobferrero/IdeaProjects/Saigon.Quest/src/commonMain/kotlin/main/components/world/WorldLoader.kt`
- `/home/jacobferrero/IdeaProjects/Saigon.Quest/src/commonMain/kotlin/main/components/shadow/ShadowManager.kt`

## Date
2025-07-29
