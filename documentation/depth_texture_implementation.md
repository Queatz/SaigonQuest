# Depth Texture Implementation for Depth of Field

## Changes Made

1. **Updated DefaultRenderingPipeline Interface in Bjs.kt**
   - Added `useDepthTexture` parameter to the constructor
   - Added `useDepthTexture` property to the class

2. **Modified CameraController.kt**
   - Replaced the use of `asDynamic()` with the proper parameter in the constructor
   - Updated comments to be more clear about the purpose of enabling depth texture

## Why These Changes Were Necessary

The depth of field effect in BabylonJS requires a depth texture to work correctly. Previously, the code was using `asDynamic()` to set the `useDepthTexture` property, which goes against the project guidelines that state: "Avoid using `js()`, `dynamic` and `asDynamic()` at all costs: Implement the interface in the correct library file instead."

By updating the DefaultRenderingPipeline interface in Bjs.kt to properly support the useDepthTexture parameter, we can now enable the depth texture without using `asDynamic()`. This makes the code more type-safe, easier to maintain, and follows the project guidelines.

## Technical Details

### Before:

```kotlin
// Set up PBR rendering pipeline
pipeline = DefaultRenderingPipeline(
    name = "pbrPipeline",
    hdr = true,
    scene = scene,
    cameras = arrayOf(camera)
)

// Enable depth texture for better depth of field quality
// This is equivalent to passing 'true' as the 5th argument to the constructor
(pipeline.asDynamic()).useDepthTexture = true
```

### After:

```kotlin
// Set up PBR rendering pipeline with depth texture enabled for better depth of field quality
pipeline = DefaultRenderingPipeline(
    name = "pbrPipeline",
    hdr = true,
    scene = scene,
    cameras = arrayOf(camera),
    useDepthTexture = true
)
```

## Benefits

1. **Type Safety**: The code is now more type-safe, as we're using a properly defined interface instead of dynamic access.
2. **Maintainability**: The code is easier to maintain, as it's clear from the interface what parameters are available.
3. **Consistency**: The code now follows the project guidelines, which helps maintain a consistent codebase.
4. **Visual Quality**: The depth of field effect continues to work correctly with proper depth texture support, ensuring high visual quality.

## References

- [BabylonJS DefaultRenderingPipeline Documentation](https://doc.babylonjs.com/typedoc/classes/babylon.defaultrenderingpipeline)
- [BabylonJS Depth of Field Effect Documentation](https://doc.babylonjs.com/features/featuresDeepDive/postProcesses/depthOfFieldEffect)
- [Project Guidelines](/home/jacobferrero/IdeaProjects/Saigon.Quest/.junie/guidelines.md)
